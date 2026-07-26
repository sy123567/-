package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.ai.AiClient;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.repository.ItineraryNodeRepository;

/**
 * 为受影响节点寻找可替代地点。分工： - AI（{@link AiClient}）只负责“提名”候选地点的名字与理由； - 百度地图（{@link
 * BaiduMapClient}）负责把名字“落成真实坐标”，或直接就近搜索真实地点； - 已有的天气（{@link WeatherClient}）与外部事件管线负责“把关”。
 *
 * <p>任何候选都必须通过天气校验（无灾害预警/无降水）与事件校验（不落在 HIGH+ 事件的时空范围内）才会被采用， 因此“AI
 * 不知道新地点天气”的短板被闭环校验消除——新地点走的是和原节点同一套监测数据源。
 */
@Service
public class ReplacementCandidateService {
  private static final List<String> INDOOR_KEYWORDS =
      List.of("博物馆", "美术馆", "展览", "商场", "水族馆", "科技馆", "图书馆", "剧院", "购物", "室内", "温泉", "书店");

  private final AiClient ai;
  private final BaiduMapClient maps;
  private final WeatherClient weather;
  private final ItineraryNodeRepository nodes;

  @Value("${replan.candidate-search-radius-meters:8000}")
  private int searchRadiusMeters;

  @Value("${replan.candidate-max-count:6}")
  private int maxCandidates;

  public ReplacementCandidateService(
      AiClient ai, BaiduMapClient maps, WeatherClient weather, ItineraryNodeRepository nodes) {
    this.ai = ai;
    this.maps = maps;
    this.weather = weather;
    this.nodes = nodes;
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
    return findSafeReplacements(node, start, end, constraints, activeEvents, rainy, 1).stream()
        .findFirst();
  }

  /**
   * 列出该节点全部通过校验的替代地点，供成员自行挑选（方案生成只取第一个作为默认建议）。
   *
   * <p>校验顺序与单选一致：预算 → 可达半径 → 饮食 → 天气 → 事件，任何一项不通过都不会出现在候选里。
   */
  public List<Candidate> findSafeReplacements(
      ItineraryNode node,
      LocalDateTime start,
      LocalDateTime end,
      ReplanConstraints constraints,
      List<ExternalEvent> activeEvents,
      boolean rainy,
      int limit) {
    if (node.getLatitude() == null || node.getLongitude() == null) return List.of();
    double lat = node.getLatitude();
    double lng = node.getLongitude();
    String query = queryFor(node.getNodeType(), rainy);
    double reachKm = reachKm(constraints); // 体力约束→可达半径
    List<Candidate> candidates = new ArrayList<>();
    candidates.addAll(aiCandidates(node, query, constraints)); // AI 提名 → 百度落坐标
    candidates.addAll(nearbyCandidates(node, query, lat, lng)); // 百度就近搜索兜底
    candidates.addAll(communityCandidates(node, reachKm)); // 其他队伍走过的同类地点
    List<Candidate> out = new ArrayList<>();
    for (Candidate candidate : candidates) {
      if (out.size() >= limit) break;
      if (isSamePlace(node, candidate)) continue;
      if (out.stream().anyMatch(kept -> isDuplicate(kept, candidate))) continue;
      if (!withinBudget(candidate, constraints)) continue;
      if (!withinReach(node, candidate, reachKm)) continue; // 体力约束：不超出可达半径
      if (!dietOk(node, candidate, constraints)) continue; // 饮食约束：排除冲突餐饮
      if (!weatherSafe(candidate)) continue; // 天气闭环校验
      if (hitByEvent(candidate, start, end, activeEvents)) continue; // 事件闭环校验
      out.add(describe(node, candidate, rainy));
    }
    return out;
  }

  /** 补齐候选的展示信息：与原地点的距离、室内与否、以及用于卡片的亮点标签。 */
  private static Candidate describe(ItineraryNode node, Candidate candidate, boolean rainy) {
    double distanceKm =
        node.getLatitude() == null || node.getLongitude() == null
            ? 0
            : ImpactMatchingService.distance(
                node.getLatitude(), node.getLongitude(), candidate.lat(), candidate.lng());
    boolean indoor = isIndoor(candidate);
    List<String> highlights = new ArrayList<>();
    if (rainy && indoor) highlights.add("室内可避雨");
    if (distanceKm <= 1.5) highlights.add("步行可达");
    if (candidate.rating() != null && candidate.rating() >= 4.5) highlights.add("口碑很高");
    if (candidate.cost() != null
        && node.getCost() != null
        && candidate.cost().compareTo(node.getCost()) < 0) {
      highlights.add("比原计划便宜");
    }
    if (candidate.reviewCount() != null && candidate.reviewCount() >= 500) highlights.add("热门去处");
    if ("community".equals(candidate.source())) highlights.add("其他队伍去过");
    return candidate.with(Math.round(distanceKm * 10) / 10.0, indoor, highlights);
  }

  private static boolean isIndoor(Candidate candidate) {
    String text =
        (candidate.name() + " " + nullToEmpty(candidate.category())).toLowerCase(Locale.ROOT);
    for (String keyword : INDOOR_KEYWORDS) {
      if (text.contains(keyword)) return true;
    }
    return false;
  }

  private static boolean isDuplicate(Candidate kept, Candidate other) {
    if (kept.name() != null && kept.name().equalsIgnoreCase(other.name())) return true;
    return ImpactMatchingService.distance(kept.lat(), kept.lng(), other.lat(), other.lng()) < 0.05;
  }

  /** 兜底候选：其他行程在附近安排过的同类节点，让没有地图 Key 的环境也有真实可选项。 */
  private List<Candidate> communityCandidates(ItineraryNode node, double reachKm) {
    if (node.getNodeType() == null || node.getTrip() == null) return List.of();
    double radius = reachKm == Double.MAX_VALUE ? 30 : reachKm;
    return nodes.findByNodeType(node.getNodeType()).stream()
        .filter(other -> other.getTrip() != null)
        .filter(other -> !Objects.equals(other.getTrip().getId(), node.getTrip().getId()))
        .filter(other -> other.getLatitude() != null && other.getLongitude() != null)
        .filter(other -> other.getPlaceName() != null && !other.getPlaceName().isBlank())
        .filter(
            other ->
                ImpactMatchingService.distance(
                        node.getLatitude(), node.getLongitude(),
                        other.getLatitude(), other.getLongitude())
                    <= radius)
        .limit(20)
        .map(
            other ->
                Candidate.of(
                    other.getPlaceName(),
                    other.getLatitude(),
                    other.getLongitude(),
                    other.getCost() == null ? node.getCost() : other.getCost(),
                    "community",
                    "其他队伍在同一片区域安排过的" + typeLabel(other.getNodeType())))
        .toList();
  }

  private static String typeLabel(Enums.NodeType type) {
    if (type == null) return "地点";
    return switch (type) {
      case MEAL -> "用餐点";
      case LODGING -> "住宿";
      case ATTRACTION -> "景点";
      case TRANSPORT -> "交通点";
      default -> "地点";
    };
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /** 体力等级映射为替代地点的可达半径（公里）：体力越低越就近。 */
  private static double reachKm(ReplanConstraints c) {
    if (c == null || c.fitnessLevel() == null) return Double.MAX_VALUE;
    return switch (c.fitnessLevel()) {
      case LOW -> 2.0;
      case MEDIUM -> 5.0;
      case HIGH -> Double.MAX_VALUE;
    };
  }

  private static boolean withinReach(ItineraryNode node, Candidate candidate, double reachKm) {
    if (reachKm == Double.MAX_VALUE || node.getLatitude() == null || node.getLongitude() == null)
      return true;
    return ImpactMatchingService.distance(
            node.getLatitude(), node.getLongitude(), candidate.lat(), candidate.lng())
        <= reachKm;
  }

  /** 饮食约束：仅对餐饮类节点生效，按关键词排除与需求冲突的候选（保守命中才排除）。 */
  private static boolean dietOk(ItineraryNode node, Candidate candidate, ReplanConstraints c) {
    if (node.getNodeType() != Enums.NodeType.MEAL) return true;
    if (c == null || c.dietaryNeeds() == null || c.dietaryNeeds().isEmpty()) return true;
    String text = (candidate.name() + " " + candidate.reason()).toLowerCase(java.util.Locale.ROOT);
    for (String need : c.dietaryNeeds()) {
      if (need == null) continue;
      String n = need.toLowerCase(java.util.Locale.ROOT);
      List<String> banned = new ArrayList<>();
      if (n.contains("素") || n.contains("vegetar") || n.contains("vegan")) {
        banned.addAll(List.of("烤肉", "牛排", "海鲜", "火锅", "烧烤", "涮"));
      }
      if (n.contains("清真") || n.contains("halal") || n.contains("穆斯林")) {
        banned.addAll(List.of("猪", "烤肉", "日料", "居酒屋"));
      }
      for (String b : banned) {
        if (text.contains(b.toLowerCase(java.util.Locale.ROOT))) return false;
      }
    }
    return true;
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
          Candidate.of(
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
      out.add(
          new Candidate(
              place.name(),
              place.lat(),
              place.lng(),
              cost,
              "nearby",
              "就近可达的替代地点",
              place.address(),
              place.tag(),
              place.overallRating(),
              place.commentNum(),
              place.image(),
              0,
              false,
              List.of()));
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
    if (c != null && c.fitnessLevel() == Enums.FitnessLevel.LOW) {
      sb.append("团队体力较弱，请优先步行可达、无需爬坡或长距离步行的地点。");
    }
    if (c != null && c.dietaryNeeds() != null && !c.dietaryNeeds().isEmpty()) {
      sb.append("餐饮需满足以下饮食需求：").append(String.join("、", c.dietaryNeeds())).append("。");
    }
    if (c != null && c.accessibilityNeeds() != null && !c.accessibilityNeeds().isEmpty()) {
      sb.append("需满足无障碍需求：")
          .append(String.join("、", c.accessibilityNeeds()))
          .append("，请优先有电梯/无障碍通道的地点。");
    }
    return sb.toString();
  }

  /** 生成/校验用到的成员约束依据（预算/必访/体力/饮食/无障碍）。 */
  public record ReplanConstraints(
      BigDecimal budgetCap,
      List<String> mustVisitPlaces,
      Enums.FitnessLevel fitnessLevel,
      List<String> dietaryNeeds,
      List<String> accessibilityNeeds) {
    public static ReplanConstraints none() {
      return new ReplanConstraints(null, List.of(), null, List.of(), List.of());
    }
  }

  /** 通过校验的替代地点候选，附带用于候选卡片的展示信息。 */
  public record Candidate(
      String name,
      double lat,
      double lng,
      BigDecimal cost,
      String source,
      String reason,
      String address,
      String category,
      Double rating,
      Integer reviewCount,
      String image,
      double distanceKm,
      boolean indoor,
      List<String> highlights) {

    public static Candidate of(
        String name, double lat, double lng, BigDecimal cost, String source, String reason) {
      return new Candidate(
          name, lat, lng, cost, source, reason, null, null, null, null, null, 0, false, List.of());
    }

    public Candidate with(double distanceKm, boolean indoor, List<String> highlights) {
      return new Candidate(
          name,
          lat,
          lng,
          cost,
          source,
          reason,
          address,
          category,
          rating,
          reviewCount,
          image,
          distanceKm,
          indoor,
          highlights);
    }
  }
}
