package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.ai.AiClient;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;

/**
 * 为受影响节点寻找可替代地点。分工： - AI（{@link AiClient}）只负责“提名”候选地点的名字与理由； - 百度地图（{@link
 * BaiduMapClient}）负责把名字“落成真实坐标”，或直接就近搜索真实地点； - 已有的天气（{@link WeatherClient}）与外部事件管线负责“把关”。
 *
 * <p>任何候选都必须通过天气校验（无灾害预警/无降水）与事件校验（不落在 HIGH+ 事件的时空范围内）才会被采用， 因此“AI
 * 不知道新地点天气”的短板被闭环校验消除——新地点走的是和原节点同一套监测数据源。
 */
@Service
public class ReplacementCandidateService {
  private final AiClient ai;
  private final BaiduMapClient maps;
  private final WeatherClient weather;

  @Value("${replan.candidate-search-radius-meters:8000}")
  private int searchRadiusMeters;

  @Value("${replan.candidate-max-count:6}")
  private int maxCandidates;

  public ReplacementCandidateService(AiClient ai, BaiduMapClient maps, WeatherClient weather) {
    this.ai = ai;
    this.maps = maps;
    this.weather = weather;
  }

  /**
   * 为节点在其时段内寻找一个通过全部校验的替代地点；找不到返回 {@code empty}，调用方据此回退到顺延或移除。
   *
   * @param rainy 命中该节点的事件里是否包含天气类事件（决定是否优先室内）
   */
  public Optional<Candidate> findSafeReplacement(
      ItineraryNode node,
      LocalDateTime start,
      LocalDateTime end,
      ReplanConstraints constraints,
      List<ExternalEvent> activeEvents,
      boolean rainy) {
    if (node.getLatitude() == null || node.getLongitude() == null) return Optional.empty();
    double lat = node.getLatitude();
    double lng = node.getLongitude();
    String query = queryFor(node.getNodeType(), rainy);
    List<Candidate> candidates = new ArrayList<>();
    candidates.addAll(aiCandidates(node, query, constraints)); // AI 提名 → 百度落坐标
    candidates.addAll(nearbyCandidates(node, query, lat, lng)); // 百度就近搜索兜底
    for (Candidate candidate : candidates) {
      if (isSamePlace(node, candidate)) continue;
      if (!withinBudget(candidate, constraints)) continue;
      if (!weatherSafe(candidate)) continue; // 天气闭环校验
      if (hitByEvent(candidate, start, end, activeEvents)) continue; // 事件闭环校验
      return Optional.of(candidate);
    }
    return Optional.empty();
  }

  private List<Candidate> aiCandidates(ItineraryNode node, String query, ReplanConstraints c) {
    if (!ai.enabled() || !maps.enabled()) return List.of();
    JsonNode response = ai.chatJson(aiSystemPrompt(), aiUserPrompt(node, query, c));
    if (response == null) return List.of();
    JsonNode places = response.path("places");
    if (!places.isArray()) return List.of();
    List<Candidate> out = new ArrayList<>();
    for (JsonNode place : places) {
      if (out.size() >= maxCandidates) break;
      String name = place.path("name").asText("").trim();
      if (name.isBlank()) continue;
      BaiduMapClient.ResolvedPlace resolved =
          maps.resolve(name, node.getLatitude(), node.getLongitude());
      if (resolved == null || resolved.lat() == null || resolved.lng() == null) continue;
      String reason = place.path("reason").asText("AI 推荐的替代地点");
      out.add(
          new Candidate(
              resolved.name() == null ? name : resolved.name(),
              resolved.lat(),
              resolved.lng(),
              node.getCost(),
              "ai",
              reason));
    }
    return out;
  }

  private List<Candidate> nearbyCandidates(
      ItineraryNode node, String query, double lat, double lng) {
    if (!maps.enabled()) return List.of();
    List<BaiduMapClient.Place> places = maps.searchNearby(query, lat, lng, searchRadiusMeters);
    if (places == null) return List.of();
    List<Candidate> out = new ArrayList<>();
    for (BaiduMapClient.Place place : places) {
      if (out.size() >= maxCandidates) break;
      if (place.lat() == null || place.lng() == null || place.name() == null) continue;
      BigDecimal cost = place.price() != null ? BigDecimal.valueOf(place.price()) : node.getCost();
      out.add(new Candidate(place.name(), place.lat(), place.lng(), cost, "nearby", "就近可达的替代地点"));
    }
    return out;
  }

  private boolean weatherSafe(Candidate candidate) {
    WeatherClient.WeatherSummary summary = weather.summary(candidate.lat(), candidate.lng());
    if (summary == null || !summary.available()) return true; // 天气不可判定时不拦截
    return !summary.hasAlert() && !summary.hasPrecipitation();
  }

  private static boolean hitByEvent(
      Candidate candidate, LocalDateTime start, LocalDateTime end, List<ExternalEvent> events) {
    if (events == null || start == null || end == null) return false;
    for (ExternalEvent event : events) {
      if (severityRank(event.getSeverity()) < severityRank(Enums.Severity.HIGH)) continue;
      if (event.getStartTime() == null
          || event.getEndTime() == null
          || event.getLatitude() == null
          || event.getLongitude() == null
          || event.getRadiusKm() == null) continue;
      boolean overlaps = event.getStartTime().isBefore(end) && event.getEndTime().isAfter(start);
      double distance =
          ImpactMatchingService.distance(
              event.getLatitude(), event.getLongitude(), candidate.lat(), candidate.lng());
      if (overlaps && distance <= event.getRadiusKm()) return true;
    }
    return false;
  }

  private static boolean withinBudget(Candidate candidate, ReplanConstraints constraints) {
    if (constraints == null || constraints.budgetCap() == null || candidate.cost() == null) {
      return true;
    }
    return candidate.cost().compareTo(constraints.budgetCap()) <= 0;
  }

  private static boolean isSamePlace(ItineraryNode node, Candidate candidate) {
    if (node.getPlaceName() != null && node.getPlaceName().equalsIgnoreCase(candidate.name())) {
      return true;
    }
    return node.getLatitude() != null
        && node.getLongitude() != null
        && ImpactMatchingService.distance(
                node.getLatitude(), node.getLongitude(), candidate.lat(), candidate.lng())
            < 0.05; // 50 米内视为同一地点
  }

  private static int severityRank(Enums.Severity severity) {
    return severity == null ? -1 : severity.ordinal();
  }

  private static String queryFor(Enums.NodeType type, boolean rainy) {
    if (type == Enums.NodeType.MEAL) return "餐厅 美食";
    if (type == Enums.NodeType.LODGING) return "酒店 宾馆";
    if (type == Enums.NodeType.ATTRACTION) {
      return rainy ? "室内景点 博物馆 展览馆 美术馆 商场" : "景点 公园";
    }
    return rainy ? "室内 商场 博物馆" : "景点";
  }

  private static String aiSystemPrompt() {
    return "你是行程应变助手。只输出 JSON，结构为 {\"places\":[{\"name\":\"地点名\",\"reason\":\"推荐理由\"}]}，"
        + "不要输出坐标或多余文字。";
  }

  private static String aiUserPrompt(ItineraryNode node, String query, ReplanConstraints c) {
    StringBuilder sb = new StringBuilder();
    sb.append("原节点：").append(node.getPlaceName()).append("，类型 ").append(node.getNodeType());
    sb.append("。请在其附近推荐可替代的真实地点（").append(query).append("），最多 6 个，优先知名、可达、当前时段开放的地点。");
    if (c != null && c.budgetCap() != null) {
      sb.append("单点预算不超过 ").append(c.budgetCap()).append(" 元。");
    }
    if (c != null && c.mustVisitPlaces() != null && !c.mustVisitPlaces().isEmpty()) {
      sb.append("如与以下必访地点相关请优先：").append(String.join("、", c.mustVisitPlaces())).append("。");
    }
    return sb.toString();
  }

  /** 生成/校验用到的成员约束依据。 */
  public record ReplanConstraints(BigDecimal budgetCap, List<String> mustVisitPlaces) {
    public static ReplanConstraints none() {
      return new ReplanConstraints(null, List.of());
    }
  }

  /** 通过校验的替代地点候选。 */
  public record Candidate(
      String name, double lat, double lng, BigDecimal cost, String source, String reason) {}
}
