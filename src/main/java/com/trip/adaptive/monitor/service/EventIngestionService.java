package com.trip.adaptive.monitor.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.dto.TripRiskResult;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.service.TripService;

@Service
public class EventIngestionService {
  private final ExternalEventRepository events;
  private final TripService trips;
  private final WeatherClient weather;
  private final ImpactMatchingService impactMatching;
  private final RiskScoringService riskScoring;
  private final ImpactAssessmentRepository assessments;
  private final ReplanningService replanning;

  @Value("${weather.forecast-window-days:3}")
  private int forecastWindowDays;

  @Value("${weather.replan-risk-threshold:HIGH}")
  private String replanRiskThreshold;

  public List<ExternalEvent> ingestWeatherForTrip(Long tripId) {
    return ingestWeatherForTrip(tripId, false);
  }

  public EventIngestionService(
      ExternalEventRepository e,
      TripService t,
      WeatherClient w,
      ImpactMatchingService i,
      RiskScoringService rs,
      ImpactAssessmentRepository a,
      ReplanningService r) {
    events = e;
    trips = t;
    weather = w;
    impactMatching = i;
    riskScoring = rs;
    assessments = a;
    replanning = r;
  }

  public ExternalEvent ingest(ExternalEvent e) {
    return events.save(e);
  }

  public List<ExternalEvent> all() {
    return events.findAll();
  }

  public List<ExternalEvent> active() {
    return events.findByEndTimeAfter(LocalDateTime.now());
  }

  public List<ExternalEvent> activeForTrips(Collection<Long> tripIds) {
    return active().stream()
        .filter(event -> event.getTripId() != null && tripIds.contains(event.getTripId()))
        .toList();
  }

  public List<ExternalEvent> fetchAndIngestForTrip(Long id) {
    Trip t = trips.get(id);
    ItineraryNode n =
        t.getItineraryNodes().stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("行程没有节点"));
    List<ExternalEvent> out = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      ExternalEvent e = new ExternalEvent();
      e.setEventType(i == 0 ? Enums.EventType.WEATHER : Enums.EventType.ATTRACTION_CLOSURE);
      e.setTitle(i == 0 ? "暴雨预警" : "景点临时闭馆");
      e.setDescription("模拟外部事件");
      e.setPlaceName(n.getPlaceName());
      e.setLatitude(n.getLatitude());
      e.setLongitude(n.getLongitude());
      e.setRadiusKm(10.0);
      e.setSeverity(i == 0 ? Enums.Severity.HIGH : Enums.Severity.MEDIUM);
      e.setStartTime(n.getPlannedStart().minusHours(1));
      e.setEndTime(n.getPlannedEnd().plusHours(1));
      e.setSource("mock-provider");
      e.setTripId(t.getId());
      e.setTripTitle(t.getTitle());
      out.add(events.save(e));
    }
    return out;
  }

  @Transactional
  public List<ExternalEvent> ingestWeatherForTrip(Long tripId, boolean force) {
    Trip t = trips.get(tripId);
    List<ExternalEvent> out = new ArrayList<>();
    if (!weather.enabled()) return out;
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime cutoff = now.plusDays(forecastWindowDays);
    Map<String, List<ItineraryNode>> byCity = new LinkedHashMap<>();
    for (ItineraryNode n : t.getItineraryNodes()) {
      if (n.getLatitude() == null || n.getLongitude() == null) continue;
      if (force) {
        if (n.getPlannedEnd() != null && n.getPlannedEnd().isBefore(now)) continue;
        if (n.getPlannedStart() != null && n.getPlannedStart().isAfter(cutoff)) continue;
      }
      refreshWeatherEvents(n);
      String loc = weather.locationKey(n.getLatitude(), n.getLongitude());
      if (loc == null) continue;
      byCity.computeIfAbsent(loc, k -> new ArrayList<>()).add(n);
    }
    for (Map.Entry<String, List<ItineraryNode>> entry : byCity.entrySet())
      ingestForCity(entry.getKey(), entry.getValue(), out);
    impactMatching.assessTrip(tripId);
    TripRiskResult result = riskScoring.scoreTrip(tripId);
    if (rank(result.riskLevel()) >= rank(replanRiskThreshold)) replanning.generate(tripId);
    else replanning.clearProposed(tripId);
    return out;
  }

  private void refreshWeatherEvents(ItineraryNode node) {
    if (node.getPlaceName() == null) return;
    for (ExternalEvent event :
        events.findBySourceStartingWithAndPlaceNameAndTripId(
            "weathercn", node.getPlaceName(), node.getTrip().getId())) {
      assessments.deleteByEvent_Id(event.getId());
      events.delete(event);
    }
  }

  private int rank(String level) {
    if (level == null) return 100;
    return switch (level.trim().toUpperCase()) {
      case "LOW" -> 0;
      case "MEDIUM" -> 1;
      case "HIGH" -> 2;
      case "CRITICAL" -> 3;
      default -> 100;
    };
  }

  private void ingestForCity(String loc, List<ItineraryNode> nodes, List<ExternalEvent> out) {
    boolean createdAlert = false;
    // 自然灾害预警：每城市只查一次
    JsonNode alerts = weather.alerts(loc);
    if (alerts != null && alerts.isArray() && alerts.size() > 0) {
      for (JsonNode a : alerts) {
        for (ItineraryNode n : nodes) {
          ExternalEvent ev = new ExternalEvent();
          ev.setEventType(Enums.EventType.WEATHER);
          ev.setTitle(text(a, "Description", "预警"));
          ev.setDescription(a.path("Description").path("Localized").asText("天气预警"));
          ev.setSeverity(Enums.Severity.HIGH);
          fill(ev, n, "weathercn-alert");
          save(out, ev);
        }
        createdAlert = true;
      }
    }
    // 修正：没有任何预警时才看降水（原来这段被错误嵌套在"有预警"分支里）
    if (!createdAlert) {
      JsonNode fc = weather.dailyForecast(loc);
      if (fc != null) {
        JsonNode forecast = fc.path("DailyForecasts").path(0);
        JsonNode day = forecast.path("Day");
        boolean rain = day.path("HasPrecipitation").asBoolean(false);
        String headline = fc.path("Headline").path("Text").asText("");
        String phrase = firstText(day, "IconPhrase", "ShortPhrase", "LongPhrase");
        Double tempMin = temperature(forecast.path("Temperature").path("Minimum"));
        Double tempMax = temperature(forecast.path("Temperature").path("Maximum"));
        if (rain || headline.matches(".*(雨|雪|暴|雷).*")) {
          for (ItineraryNode n : nodes) {
            ExternalEvent ev = new ExternalEvent();
            ev.setEventType(Enums.EventType.WEATHER);
            String title = phrase.isBlank() ? (headline.isBlank() ? "预报有降水" : headline) : phrase;
            ev.setTitle(title);
            ev.setDescription(
                tempMin != null && tempMax != null
                    ? String.format("%s · 气温 %.0f~%.0f°C", title, tempMin, tempMax)
                    : (headline.isBlank() ? "天气预报" : headline));
            ev.setTempMin(tempMin);
            ev.setTempMax(tempMax);
            ev.setSeverity(Enums.Severity.MEDIUM);
            fill(ev, n, "weathercn-forecast");
            save(out, ev);
          }
        }
      }
    }
  }

  // 一条 ExternalEvent 除了标题/类型/严重度不同，其余字段（地点、经纬度、影响半径、起止时间、来源）都来自那个行程节点 n，两个分支都要设一遍。与其写两遍，就抽成 fill：
  private void fill(ExternalEvent e, ItineraryNode n, String source) {
    e.setPlaceName(n.getPlaceName());
    e.setLatitude(n.getLatitude());
    e.setLongitude(n.getLongitude());
    e.setRadiusKm(10.0);
    e.setStartTime(n.getPlannedStart());
    e.setEndTime(n.getPlannedEnd());
    e.setSource(source);
    e.setTripId(n.getTrip().getId());
    e.setTripTitle(n.getTrip().getTitle());
  }

  // 去重后再存库,定时轮询每 100分钟跑一次，同一条暴雨预警不会被重复插入几十遍。
  private void save(java.util.List<ExternalEvent> out, ExternalEvent e) {
    if (!events.existsBySourceAndPlaceNameAndStartTime(
        e.getSource(), e.getPlaceName(), e.getStartTime())) {
      out.add(events.save(e));
    }
  }

  private static String text(JsonNode node, String field, String def) {
    JsonNode v = node.get(field);
    if (v == null) return def;
    return v.isObject() ? v.path("Localized").asText(def) : v.asText(def);
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("");
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static Double temperature(JsonNode node) {
    if (!node.path("Value").isNumber()) return null;
    double value = node.path("Value").asDouble();
    return "F".equalsIgnoreCase(node.path("Unit").asText("")) ? (value - 32) * 5 / 9 : value;
  }
}
