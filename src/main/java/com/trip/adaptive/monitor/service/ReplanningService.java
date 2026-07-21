package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.ReplanConstraints;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.NodeChangeRepository;
import com.trip.adaptive.repository.TripRepository;

/**
 * 重规划：针对受影响节点，按三种策略生成差异化的替代方案。
 *
 * <p>每个受影响节点会枚举可行动作（顺延 RESCHEDULE / 换地点 REPLACE / 移除 REMOVE），并估算三项代价： 额外成本 Δcost（含相邻路段重算）、额外延误
 * Δdelay、改动幅度 Δchange。三种策略用不同的排序偏好挑选动作， 从而得到成本最省 / 延误最少 / 改动最小三份可比较的方案。
 *
 * <p>REPLACE 的候选地点由 {@link ReplacementCandidateService} 提供并已通过天气/事件闭环校验。
 */
@Service
public class ReplanningService {
  private final TripRepository trips;
  private final ImpactAssessmentRepository impacts;
  private final AlternativePlanRepository plans;
  private final NodeChangeRepository changes;
  private final NotificationService notifications;
  private final ExternalEventRepository events;
  private final ReplacementCandidateService candidates;
  private final RouteRecalculationService routing;

  @Value("${weather.replan-buffer-minutes:30}")
  private int bufferMinutes;

  public ReplanningService(
      TripRepository t,
      ImpactAssessmentRepository i,
      AlternativePlanRepository p,
      NodeChangeRepository c,
      NotificationService n,
      ExternalEventRepository e,
      ReplacementCandidateService candidates,
      RouteRecalculationService routing) {
    trips = t;
    impacts = i;
    plans = p;
    changes = c;
    notifications = n;
    events = e;
    this.candidates = candidates;
    this.routing = routing;
  }

  @Transactional
  public List<AlternativePlan> generate(Long id) {
    Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    // 投票锁：已有方案在投票中时不重算，避免打断进行中的群体决策。
    if (!plans.findByTripIdAndStatus(id, Enums.PlanStatus.VOTING).isEmpty()) {
      return plans.findByTripId(id);
    }
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
    ReplanConstraints constraints = constraintsFor(t);
    List<AlternativePlan> out = new ArrayList<>();
    for (Enums.ReplanStrategy strategy : Enums.ReplanStrategy.values()) {
      AlternativePlan p = new AlternativePlan();
      p.setTrip(t);
      p.setStrategy(strategy);
      p.setTitle("替代方案-" + strategy);
      p.setStatus(Enums.PlanStatus.PROPOSED);

      BigDecimal extraCost = BigDecimal.ZERO;
      int maxDelay = 0;
      int changedNodes = 0;
      for (ItineraryNode node : affected) {
        List<ActionOption> options = buildOptions(node, activeEvents, constraints, t);
        ActionOption chosen = chooseOption(strategy, options);
        p.getProposedNodeChanges().add(toNodeChange(p, node, chosen));
        extraCost = extraCost.add(chosen.deltaCost().max(BigDecimal.ZERO));
        maxDelay = Math.max(maxDelay, chosen.deltaDelayMinutes());
        changedNodes++;
      }
      p.setExtraCost(extraCost);
      p.setExtraDelayMinutes(maxDelay);
      p.setChangedNodeCount(changedNodes);
      p.setSummary(
          String.format(
              "针对 %d 个受影响节点，按 %s 策略生成：额外成本约 ¥%s，最大延误 %d 分钟。",
              affected.size(), strategy, extraCost.stripTrailingZeros().toPlainString(), maxDelay));
      out.add(plans.save(p));
    }
    if (!out.isEmpty()) notifications.trip(id, "new-plans", out);
    return out;
  }

  /** 根据策略在候选动作里挑选：三种策略的排序偏好不同，产出差异化方案。 */
  public static ActionOption chooseOption(
      Enums.ReplanStrategy strategy, List<ActionOption> options) {
    Comparator<ActionOption> byCost = Comparator.comparing(o -> o.deltaCost().max(BigDecimal.ZERO));
    Comparator<ActionOption> byDelay = Comparator.comparingInt(ActionOption::deltaDelayMinutes);
    Comparator<ActionOption> byChange = Comparator.comparingInt(ActionOption::changeWeight);
    Comparator<ActionOption> comparator =
        switch (strategy) {
          case MIN_EXTRA_COST -> byCost.thenComparing(byChange).thenComparing(byDelay);
          case MIN_DELAY -> byDelay.thenComparing(byChange).thenComparing(byCost);
          case MIN_CHANGE -> byChange.thenComparing(byDelay).thenComparing(byCost);
        };
    return options.stream().min(comparator).orElseThrow();
  }

  private List<ActionOption> buildOptions(
      ItineraryNode node, List<ExternalEvent> activeEvents, ReplanConstraints constraints, Trip t) {
    List<ActionOption> options = new ArrayList<>();
    List<ExternalEvent> hits = hittingEvents(node, activeEvents);
    boolean rainy = hits.stream().anyMatch(e -> e.getEventType() == Enums.EventType.WEATHER);

    // 1) 顺延（保持地点，时间推到事件结束后）
    if (node.getPlannedStart() != null && node.getPlannedEnd() != null && !hits.isEmpty()) {
      LocalDateTime latestEnd =
          hits.stream().map(ExternalEvent::getEndTime).max(Comparator.naturalOrder()).orElse(null);
      if (latestEnd != null) {
        Duration duration = Duration.between(node.getPlannedStart(), node.getPlannedEnd());
        LocalDateTime newStart = latestEnd.plusMinutes(bufferMinutes);
        LocalDateTime newEnd = newStart.plus(duration);
        if (!hitsAny(node, activeEvents, newStart, newEnd)) {
          int delay = (int) Duration.between(node.getPlannedStart(), newStart).toMinutes();
          options.add(
              new ActionOption(
                  Enums.ChangeType.RESCHEDULE,
                  node.getPlaceName(),
                  node.getLatitude(),
                  node.getLongitude(),
                  newStart,
                  newEnd,
                  node.getCost(),
                  BigDecimal.ZERO,
                  Math.max(0, delay),
                  1,
                  "天气/事件结束后顺延，并校验新时段不再命中事件"));
        }
      }
    }

    // 2) 换地点（AI 提名 + 百度定位/就近兜底，已通过天气与事件闭环校验）
    Optional<Candidate> replacement =
        candidates.findSafeReplacement(
            node, node.getPlannedStart(), node.getPlannedEnd(), constraints, activeEvents, rainy);
    if (replacement.isPresent()) {
      Candidate candidate = replacement.get();
      BigDecimal nodeCostDelta = nz(candidate.cost()).subtract(nz(node.getCost()));
      BigDecimal routeDelta = routeCostDelta(node, candidate, t);
      options.add(
          new ActionOption(
              Enums.ChangeType.REPLACE,
              candidate.name(),
              candidate.lat(),
              candidate.lng(),
              node.getPlannedStart(),
              node.getPlannedEnd(),
              candidate.cost(),
              nodeCostDelta.add(routeDelta),
              0,
              2,
              "换到已校验安全的替代地点：" + candidate.reason()));
    }

    // 3) 移除（兜底：无法避让时移除节点）
    options.add(
        new ActionOption(
            Enums.ChangeType.REMOVE,
            null,
            null,
            null,
            null,
            null,
            null,
            BigDecimal.ZERO,
            0,
            3,
            "当前事件无法安全避让，建议移除该节点"));
    return options;
  }

  /** REPLACE 时相邻两段路线成本的变化估算（换地点会改变前后路段）。 */
  private BigDecimal routeCostDelta(ItineraryNode node, Candidate candidate, Trip t) {
    List<ItineraryNode> ordered =
        t.getItineraryNodes().stream()
            .sorted(Comparator.comparingInt(ItineraryNode::getSequenceOrder))
            .toList();
    int index = -1;
    for (int i = 0; i < ordered.size(); i++) {
      if (Objects.equals(ordered.get(i).getId(), node.getId())) {
        index = i;
        break;
      }
    }
    if (index < 0) return BigDecimal.ZERO;
    ItineraryNode prev = index > 0 ? ordered.get(index - 1) : null;
    ItineraryNode next = index < ordered.size() - 1 ? ordered.get(index + 1) : null;
    BigDecimal delta = BigDecimal.ZERO;
    if (prev != null) {
      delta =
          delta
              .add(segCost(prev, candidate.lat(), candidate.lng()))
              .subtract(segCost(prev, node.getLatitude(), node.getLongitude()));
    }
    if (next != null) {
      delta =
          delta
              .add(segCost(next, candidate.lat(), candidate.lng()))
              .subtract(segCost(next, node.getLatitude(), node.getLongitude()));
    }
    return delta;
  }

  private BigDecimal segCost(ItineraryNode other, Double lat, Double lng) {
    RouteRecalculationService.Segment segment =
        routing.segment(other.getLatitude(), other.getLongitude(), lat, lng, null);
    return segment == null ? BigDecimal.ZERO : segment.cost();
  }

  private NodeChange toNodeChange(AlternativePlan plan, ItineraryNode node, ActionOption option) {
    NodeChange change = new NodeChange();
    change.setPlan(plan);
    change.setOriginalNode(node);
    change.setChangeType(option.type());
    change.setNewPlaceName(option.newPlaceName());
    change.setNewLatitude(option.newLat());
    change.setNewLongitude(option.newLng());
    change.setNewStart(option.newStart());
    change.setNewEnd(option.newEnd());
    change.setNewCost(option.newCost());
    change.setNote(option.note());
    return change;
  }

  private ReplanConstraints constraintsFor(Trip t) {
    if (t.getGroup() == null || t.getGroup().getMembers() == null) {
      return ReplanConstraints.none();
    }
    List<MemberConstraint> memberConstraints =
        t.getGroup().getMembers().stream()
            .map(GroupMember::getConstraint)
            .filter(Objects::nonNull)
            .toList();
    BigDecimal budgetCap =
        memberConstraints.stream()
            .map(MemberConstraint::getMaxBudget)
            .filter(Objects::nonNull)
            .min(BigDecimal::compareTo)
            .orElse(null);
    List<String> mustVisit =
        memberConstraints.stream()
            .filter(c -> c.getMustVisitPlaces() != null)
            .flatMap(c -> c.getMustVisitPlaces().stream())
            .distinct()
            .toList();
    return new ReplanConstraints(budgetCap, mustVisit);
  }

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

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  public List<AlternativePlan> list(Long id) {
    return plans.findByTripId(id);
  }

  public AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }

  /** 单个受影响节点的一个候选动作及其代价估算。 */
  public record ActionOption(
      Enums.ChangeType type,
      String newPlaceName,
      Double newLat,
      Double newLng,
      LocalDateTime newStart,
      LocalDateTime newEnd,
      BigDecimal newCost,
      BigDecimal deltaCost,
      int deltaDelayMinutes,
      int changeWeight,
      String note) {}
}
