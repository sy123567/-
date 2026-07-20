package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.trip.adaptive.ai.AiClient;
import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.NodeChangeRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class ReplanningService {
  private final TripRepository trips;
  private final ImpactAssessmentRepository impacts;
  private final AlternativePlanRepository plans;
  private final NodeChangeRepository changes;
  private final NotificationService notifications;
  private final AiClient ai;

  @Value("${weather.replan-buffer-minutes:30}")
  private int bufferMinutes;

  public ReplanningService(
      TripRepository t,
      ImpactAssessmentRepository i,
      AlternativePlanRepository p,
      NodeChangeRepository c,
      NotificationService n,
      ExternalEventRepository e,
      AiClient a) {
    trips = t;
    impacts = i;
    plans = p;
    changes = c;
    notifications = n;
    events = e;
    ai = a;
  }

  private final ExternalEventRepository events;

  @Transactional
  public List<AlternativePlan> generate(Long id) {
    Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    List<ItineraryNode> affected =
        impacts.findByTripId(id).stream()
            .map(ImpactAssessment::getAffectedNode)
            .filter(Objects::nonNull)
            .collect(
                java.util.stream.Collectors.toMap(
                    ItineraryNode::getId, n -> n, (first, ignored) -> first))
            .values()
            .stream()
            .toList();
    if (affected.isEmpty()) return List.of();
    clearProposed(id);

    List<ExternalEvent> activeEvents = events.findByEndTimeAfter(LocalDateTime.now());
    Map<Long, AiSuggestion> aiSuggestions = aiSuggestions(affected, activeEvents);
    List<AlternativePlan> out = new ArrayList<>();
    for (Enums.ReplanStrategy s : Enums.ReplanStrategy.values()) {
      AlternativePlan p = new AlternativePlan();
      p.setTrip(t);
      p.setStrategy(s);
      p.setTitle("替代方案-" + s);
      p.setStatus(Enums.PlanStatus.PROPOSED);
      p.setExtraCost(
          s == Enums.ReplanStrategy.MIN_EXTRA_COST ? BigDecimal.ZERO : new BigDecimal("30"));
      p.setChangedNodeCount(affected.size());
      p.setSummary("针对 " + affected.size() + " 个受影响节点，采用 " + s + " 策略生成。");
      int maxDelay = 0;
      boolean aiApplied = false;
      for (ItineraryNode n : affected) {
        NodeChange c = new NodeChange();
        c.setPlan(p);
        c.setOriginalNode(n);
        List<ExternalEvent> hits = hittingEvents(n, activeEvents);
        LocalDateTime newStart = null;
        LocalDateTime newEnd = null;
        boolean reschedule = s != Enums.ReplanStrategy.MIN_CHANGE && !hits.isEmpty();
        if (reschedule && n.getPlannedStart() != null && n.getPlannedEnd() != null) {
          LocalDateTime latestEnd =
              hits.stream()
                  .map(ExternalEvent::getEndTime)
                  .max(Comparator.naturalOrder())
                  .orElse(null);
          Duration duration = Duration.between(n.getPlannedStart(), n.getPlannedEnd());
          newStart = latestEnd == null ? null : latestEnd.plusMinutes(bufferMinutes);
          newEnd = newStart == null ? null : newStart.plus(duration);
          if (newStart == null || hitsAny(n, activeEvents, newStart, newEnd)) {
            reschedule = false;
            newStart = null;
            newEnd = null;
          } else {
            maxDelay =
                Math.max(
                    maxDelay, (int) Duration.between(n.getPlannedStart(), newStart).toMinutes());
          }
        }
        c.setChangeType(reschedule ? Enums.ChangeType.RESCHEDULE : Enums.ChangeType.REMOVE);
        c.setNewPlaceName(reschedule ? n.getPlaceName() : null);
        c.setNewStart(newStart);
        c.setNewEnd(newEnd);
        c.setNewCost(reschedule && n.getCost() != null ? n.getCost().add(p.getExtraCost()) : null);
        AiSuggestion suggestion = aiSuggestions.get(n.getId());
        if (!reschedule
            && suggestion != null
            && "replace".equals(suggestion.action())
            && suggestion.alternativePlace() != null
            && !suggestion.alternativePlace().isBlank()) {
          c.setChangeType(Enums.ChangeType.REPLACE);
          c.setNewPlaceName(suggestion.alternativePlace());
          aiApplied = true;
        }
        String ruleNote = reschedule ? "天气事件结束后顺延并校验时段" : "当前事件无法安全避让，建议移除节点";
        c.setNote(suggestion == null ? ruleNote : ruleNote + "；AI建议：" + suggestion.reason());
        p.getProposedNodeChanges().add(c);
      }
      p.setExtraDelayMinutes(maxDelay);
      if (aiApplied) p.setSummary(p.getSummary() + "（含 AI 建议）");
      out.add(plans.save(p));
    }
    if (!out.isEmpty()) notifications.trip(id, "new-plans", out);
    return out;
  }

  private Map<Long, AiSuggestion> aiSuggestions(
      List<ItineraryNode> affected, List<ExternalEvent> activeEvents) {
    try {
      if (!ai.enabled()) return Map.of();
      List<NodeEventContext> contexts =
          affected.stream()
              .map(node -> new NodeEventContext(node, hittingEvents(node, activeEvents)))
              .filter(context -> !context.events().isEmpty())
              .toList();
      if (contexts.isEmpty()) return Map.of();
      JsonNode response =
          ai.chatJson(
              "你是严格基于真实外部事件的旅行路线重规划助手，只输出 JSON，不要 Markdown 或解释文字。", replanningPrompt(contexts));
      return parseSuggestions(response, contexts);
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private String replanningPrompt(List<NodeEventContext> contexts) {
    StringBuilder prompt =
        new StringBuilder(
            """
            请基于下面提供的真实行程节点和命中事件，为每个确实受影响的节点给出可执行的替代建议。
            不得虚构事件，也不要为未提供的节点生成建议。
            只返回严格 JSON 对象：
            {"suggestions":[{"nodeId":1,"action":"replace","alternativePlace":"室内博物馆","reason":"因事件标题，该时段有降水，建议改为室内的室内博物馆"}]}
            action 只能是 replace、reschedule、remove 之一；alternativePlace 在非 replace 时可为 null；
            reason 必须是中文，并明确引用对应的真实事件标题或影响原因。

            真实数据：
            """);
    for (NodeEventContext context : contexts) {
      ItineraryNode node = context.node();
      prompt
          .append("\n节点 nodeId=")
          .append(node.getId())
          .append("，名称=")
          .append(value(node.getName()))
          .append("，地点=")
          .append(value(node.getPlaceName()))
          .append("，经纬度=")
          .append(value(node.getLatitude()))
          .append(",")
          .append(value(node.getLongitude()))
          .append("，计划=")
          .append(value(node.getPlannedStart()))
          .append(" 至 ")
          .append(value(node.getPlannedEnd()))
          .append("。命中真实事件：");
      for (ExternalEvent event : context.events()) {
        prompt
            .append("\n- 标题=")
            .append(value(event.getTitle()))
            .append("，类型=")
            .append(value(event.getEventType()))
            .append("，严重度=")
            .append(value(event.getSeverity()))
            .append("，时间=")
            .append(value(event.getStartTime()))
            .append(" 至 ")
            .append(value(event.getEndTime()))
            .append("，描述=")
            .append(value(event.getDescription()))
            .append("，影响半径公里=")
            .append(value(event.getRadiusKm()));
      }
    }
    return prompt.toString();
  }

  private Map<Long, AiSuggestion> parseSuggestions(
      JsonNode response, List<NodeEventContext> contexts) {
    if (response == null || !response.path("suggestions").isArray()) return Map.of();
    Set<Long> eligible = new HashSet<>();
    contexts.forEach(context -> eligible.add(context.node().getId()));
    Map<Long, AiSuggestion> suggestions = new HashMap<>();
    for (JsonNode item : response.path("suggestions")) {
      JsonNode nodeId = item.path("nodeId");
      String action = item.path("action").asText("").trim().toLowerCase();
      String reason = item.path("reason").asText("").trim();
      String alternativePlace = item.path("alternativePlace").asText("").trim();
      if (!nodeId.canConvertToLong()
          || !eligible.contains(nodeId.asLong())
          || !Set.of("replace", "reschedule", "remove").contains(action)
          || reason.isBlank()
          || suggestions.containsKey(nodeId.asLong())) continue;
      suggestions.put(
          nodeId.asLong(),
          new AiSuggestion(action, alternativePlace.isBlank() ? null : alternativePlace, reason));
    }
    return suggestions;
  }

  private static String value(Object value) {
    return value == null ? "未提供" : value.toString();
  }

  private record NodeEventContext(ItineraryNode node, List<ExternalEvent> events) {}

  private record AiSuggestion(String action, String alternativePlace, String reason) {}

  @Transactional
  public void clearProposed(Long id) {
    plans.deleteAll(plans.findByTripIdAndStatus(id, Enums.PlanStatus.PROPOSED));
  }

  private List<ExternalEvent> hittingEvents(ItineraryNode node, List<ExternalEvent> candidates) {
    return candidates.stream()
        .filter(event -> hits(event, node, node.getPlannedStart(), node.getPlannedEnd()))
        .toList();
  }

  private boolean hitsAny(
      ItineraryNode node, List<ExternalEvent> candidates, LocalDateTime start, LocalDateTime end) {
    return candidates.stream().anyMatch(event -> hits(event, node, start, end));
  }

  private boolean hits(
      ExternalEvent event, ItineraryNode node, LocalDateTime start, LocalDateTime end) {
    if (event.getStartTime() == null
        || event.getEndTime() == null
        || start == null
        || end == null
        || event.getLatitude() == null
        || event.getLongitude() == null
        || node.getLatitude() == null
        || node.getLongitude() == null
        || event.getRadiusKm() == null) return false;
    return event.getStartTime().isBefore(end)
        && event.getEndTime().isAfter(start)
        && ImpactMatchingService.distance(
                event.getLatitude(), event.getLongitude(), node.getLatitude(), node.getLongitude())
            <= event.getRadiusKm();
  }

  public List<AlternativePlan> list(Long id) {
    return plans.findByTripId(id);
  }

  public AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }
}
