package com.trip.adaptive.monitor.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.service.TripService;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ExternalEventRepository;



@Service
public class EventIngestionService {
  private final ExternalEventRepository events;
  private final TripService trips;
  private final WeatherClient weather;
  public EventIngestionService(ExternalEventRepository e, TripService t, WeatherClient w) {
    events = e;
    trips = t;
    weather = w;
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
      out.add(events.save(e));
    }
    return out;
  }

  public List<ExternalEvent> ingestWeatherForTrip(Long tripId) {
    Trip t = trips.get(tripId);
    List<ExternalEvent> out = new ArrayList<>();
    if (!weather.enabled())
      return out;
    for (ItineraryNode n : t.getItineraryNodes()) {
      if (n.getLatitude() == null || n.getLongitude() == null) {
        continue;
      }
      String loc = weather.locationKey(n.getLatitude(), n.getLongitude());
      if (loc == null) continue;
      boolean created = false;
      //自然灾害预警
      JsonNode alerts = weather.alerts(loc);
      if (alerts != null && alerts.isArray() && alerts.size() > 0) {
        for (JsonNode a : alerts) {
          ExternalEvent e = new ExternalEvent();
          e.setEventType(Enums.EventType.WEATHER);
          e.setTitle(text(a, "Description", "预警"));   // 字段名以真实预警返回为准
          e.setDescription(a.path("Description").path("Localized").asText("天气预警"));
          e.setSeverity(Enums.Severity.HIGH);
          fill(e, n, "weathercn-alert");
          save(out, e);
          created = true;
        }
        //无灾害,看降水
        if (!created) {
          JsonNode fc = weather.dailyForecast(loc);
          if (fc != null) {
            JsonNode day = fc.path("DailyForecasts").path(0).path("Day");
            boolean rain = day.path("HasPrecipitation").asBoolean(false);
            String headline = fc.path("Headline").path("Text").asText("");
            if (rain || headline.matches(".*(雨|雪|暴|雷).*")) {
              ExternalEvent e = new ExternalEvent();
              e.setEventType(Enums.EventType.WEATHER);
              e.setTitle(headline.isBlank() ? "预报有降水" : headline);
              e.setDescription(fc.path("Headline").path("Text").asText("天气预报"));
              e.setSeverity(Enums.Severity.MEDIUM);
              fill(e, n, "weathercn-forecast");
              save(out, e);
            }
          }
        }

      }
    }
    return out;
  }
  //一条 ExternalEvent 除了标题/类型/严重度不同，其余字段（地点、经纬度、影响半径、起止时间、来源）都来自那个行程节点 n，两个分支都要设一遍。与其写两遍，就抽成 fill：
  private void fill(ExternalEvent e, ItineraryNode n, String source) {
    e.setPlaceName(n.getPlaceName());
    e.setLatitude(n.getLatitude());
    e.setLongitude(n.getLongitude());
    e.setRadiusKm(10.0);
    e.setStartTime(n.getPlannedStart());
    e.setEndTime(n.getPlannedEnd());
    e.setSource(source);
  }
//去重后再存库,定时轮询每 10 分钟跑一次，同一条暴雨预警不会被重复插入几十遍。
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
}
