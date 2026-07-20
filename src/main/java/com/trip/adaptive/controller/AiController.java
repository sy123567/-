package com.trip.adaptive.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.ai.AiClient;
import com.trip.adaptive.service.AiConstraintService;
import com.trip.adaptive.service.AiConstraintService.ConstraintContext;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private static final String FALLBACK_MESSAGE = "AI 规划暂不可用，已切换本地推荐";
  private static final Set<String> CATEGORIES = Set.of("吃", "喝", "玩", "乐", "住");
  private static final Set<String> NODE_TYPES =
      Set.of("ATTRACTION", "MEAL", "LODGING", "TRANSPORT", "OTHER");

  private final AiClient ai;
  private final AiConstraintService constraints;

  public AiController(AiClient ai, AiConstraintService constraints) {
    this.ai = ai;
    this.constraints = constraints;
  }

  @GetMapping("/plan")
  public AiPlanResult plan(
      @RequestParam(defaultValue = "") String city,
      @RequestParam(defaultValue = "2") int days,
      @RequestParam(defaultValue = "") String interests,
      @RequestParam(required = false) Long groupId) {
    ConstraintContext context = constraints.forGroup(groupId);
    if (!ai.enabled() || city.isBlank() || days < 1) return fallback(city);
    String systemPrompt = "你是中文旅行规划助手。只输出严格 JSON 对象，不要 Markdown、解释文字或代码围栏。";
    String userPrompt =
        """
        请为城市“%s”规划 %d 天的吃喝玩乐清单%s。
        返回约 10 到 16 个真实且适合游客的地点，覆盖吃、喝、玩、乐、住。
        必须严格返回如下 JSON 对象：
        {"places":[{"placeName":"地点名","category":"吃","nodeType":"MEAL","description":"一句话介绍","latitude":31.2,"longitude":121.4,"suggestedDurationMinutes":90}]}
        category 只能是吃、喝、玩、乐、住之一；nodeType 只能是
        ATTRACTION、MEAL、LODGING、TRANSPORT、OTHER 之一，并与 category 合理对应；
        latitude、longitude 必须是大致准确的数字；suggestedDurationMinutes 必须是正整数。
        %s
        """
            .formatted(
                city.trim(),
                days,
                interests.isBlank() ? "" : "，偏好：" + interests.trim(),
                constraints.promptText(context));
    JsonNode root = ai.chatJson(systemPrompt, userPrompt);
    List<AiPlace> places = parsePlaces(root);
    return places.isEmpty() || !containsRequiredPlaces(places, context.mustVisitPlaces())
        ? fallback(city)
        : new AiPlanResult(true, "ai", city.trim(), places, null);
  }

  private boolean containsRequiredPlaces(List<AiPlace> places, List<String> required) {
    return required.stream()
        .allMatch(
            wanted ->
                places.stream()
                    .anyMatch(
                        place ->
                            place.placeName().contains(wanted)
                                || wanted.contains(place.placeName())));
  }

  private List<AiPlace> parsePlaces(JsonNode root) {
    List<AiPlace> places = new ArrayList<>();
    Set<String> names = new HashSet<>();
    if (root == null || !root.path("places").isArray()) return places;
    for (JsonNode place : root.path("places")) {
      String placeName = text(place, "placeName");
      String category = text(place, "category");
      String nodeType = text(place, "nodeType");
      String description = text(place, "description");
      JsonNode latitudeNode = place.path("latitude");
      JsonNode longitudeNode = place.path("longitude");
      JsonNode durationNode = place.path("suggestedDurationMinutes");
      if (placeName.isBlank()
          || !names.add(placeName)
          || description.isBlank()
          || !CATEGORIES.contains(category)
          || !NODE_TYPES.contains(nodeType)
          || !latitudeNode.isNumber()
          || !longitudeNode.isNumber()
          || !durationNode.canConvertToInt()
          || durationNode.asInt() <= 0) continue;
      double latitude = latitudeNode.asDouble();
      double longitude = longitudeNode.asDouble();
      if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) continue;
      places.add(
          new AiPlace(
              placeName,
              category,
              nodeType,
              description,
              latitude,
              longitude,
              durationNode.asInt()));
      if (places.size() == 16) break;
    }
    return places;
  }

  private AiPlanResult fallback(String city) {
    return new AiPlanResult(
        false, "offline", city == null ? "" : city.trim(), List.of(), FALLBACK_MESSAGE);
  }

  private static String text(JsonNode node, String field) {
    return node.path(field).asText("").trim();
  }

  public record AiPlanResult(
      boolean available, String source, String city, List<AiPlace> places, String message) {}

  public record AiPlace(
      String placeName,
      String category,
      String nodeType,
      String description,
      Double latitude,
      Double longitude,
      Integer suggestedDurationMinutes) {}
}
