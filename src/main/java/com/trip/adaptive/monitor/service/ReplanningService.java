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
import com.trip.adaptive.exception.BusinessException;
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

  @Value("${replan.selectable-candidate-count:8}")
  private int maxSelectableCandidates;

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
    if (!plans.findByTripIdAndArchivedFalseAndStatus(id, Enums.PlanStatus.VOTING).isEmpty()) {
      return list(id);
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
    archivePreviousRound(id);
    int roundNo = nextRound(id);

    List<ExternalEvent> activeEvents = events.findByTripIdAndEndTimeAfter(id, LocalDateTime.now());
    ReplanConstraints constraints = constraintsFor(t);
    List<AlternativePlan> out = new ArrayList<>();
    for (Enums.ReplanStrategy strategy : Enums.ReplanStrategy.values()) {
      AlternativePlan p = new AlternativePlan();
      p.setTrip(t);
      p.setStrategy(strategy);
      p.setTitle("替代方案-" + strategy);
      p.setRoundNo(roundNo);
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

  /** 某个节点变更可选的全部替代地点：方案里给的只是默认建议，成员可以在候选里改选。 */
  @Transactional(readOnly = true)
  public List<Candidate> candidatesFor(Long changeId) {
    NodeChange change =
        changes.findById(changeId).orElseThrow(() -> new ResourceNotFoundException("节点变更不存在"));
    ItineraryNode node = change.getOriginalNode();
    if (node == null || node.getTrip() == null) return List.of();
    Trip t = node.getTrip();
    List<ExternalEvent> activeEvents =
        events.findByTripIdAndEndTimeAfter(t.getId(), LocalDateTime.now());
    boolean rainy =
        hittingEvents(node, activeEvents).stream()
            .anyMatch(e -> e.getEventType() == Enums.EventType.WEATHER);
    return candidates.findSafeReplacements(
        node,
        node.getPlannedStart(),
        node.getPlannedEnd(),
        constraintsFor(t),
        activeEvents,
        rainy,
        maxSelectableCandidates);
  }

  /**
   * 成员为某个节点改选替代地点：只在方案还没进入投票时允许，改完后同步方案的成本/改动统计。
   *
   * @param selection 候选地点，必须来自 {@link #candidatesFor(Long)}（同名或 50 米内视为同一地点）
   */
  @Transactional
  public NodeChange chooseReplacement(Long changeId, Candidate selection) {
    NodeChange change =
        changes.findById(changeId).orElseThrow(() -> new ResourceNotFoundException("节点变更不存在"));
    AlternativePlan plan = change.getPlan();
    if (plan.isArchived() || plan.getStatus() != Enums.PlanStatus.PROPOSED) {
      throw new BusinessException("方案已进入投票或已归档，不能再更换替代地点");
    }
    ItineraryNode node = change.getOriginalNode();
    Candidate verified =
        candidatesFor(changeId).stream()
            .filter(c -> matches(c, selection))
            .findFirst()
            .orElseThrow(() -> new BusinessException("该地点已不在通过校验的候选中，请刷新后重选"));

    change.setChangeType(Enums.ChangeType.REPLACE);
    change.setNewPlaceName(verified.name());
    change.setNewLatitude(verified.lat());
    change.setNewLongitude(verified.lng());
    change.setNewStart(node.getPlannedStart());
    change.setNewEnd(node.getPlannedEnd());
    change.setNewCost(verified.cost());
    change.setNote("成员选择的替代地点：" + verified.reason());
    changes.save(change);
    recomputeTotals(plan);
    notifications.trip(node.getTrip().getId(), "plan-updated", plan);
    return change;
  }

  private static boolean matches(Candidate candidate, Candidate selection) {
    if (selection.name() != null && selection.name().equalsIgnoreCase(candidate.name()))
      return true;
    return ImpactMatchingService.distance(
            candidate.lat(), candidate.lng(), selection.lat(), selection.lng())
        < 0.05;
  }

  /** 改选后重新汇总方案的额外成本与改动节点数，保证卡片上的对比数字与实际变更一致。 */
  private void recomputeTotals(AlternativePlan plan) {
    BigDecimal extraCost = BigDecimal.ZERO;
    int changedNodes = 0;
    for (NodeChange change : changes.findByPlanId(plan.getId())) {
      BigDecimal original =
          change.getOriginalNode() == null
              ? BigDecimal.ZERO
              : nz(change.getOriginalNode().getCost());
      extraCost = extraCost.add(nz(change.getNewCost()).subtract(original).max(BigDecimal.ZERO));
      changedNodes++;
    }
    plan.setExtraCost(extraCost);
    plan.setChangedNodeCount(changedNodes);
    plans.save(plan);
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
    // 团队体力取最弱者（LOW 优先），保证替代地点对所有人可达。
    Enums.FitnessLevel fitness =
        memberConstraints.stream()
            .map(MemberConstraint::getFitnessLevel)
            .filter(Objects::nonNull)
            .min(Comparator.comparingInt(Enum::ordinal))
            .orElse(null);
    List<String> dietary =
        memberConstraints.stream()
            .filter(c -> c.getDietaryNeeds() != null)
            .flatMap(c -> c.getDietaryNeeds().stream())
            .distinct()
            .toList();
    List<String> accessibility =
        memberConstraints.stream()
            .filter(c -> c.getAccessibilityNeeds() != null)
            .flatMap(c -> c.getAccessibilityNeeds().stream())
            .distinct()
            .toList();
    return new ReplanConstraints(budgetCap, mustVisit, fitness, dietary, accessibility);
  }

  @Transactional
  public void clearProposed(Long id) {
    plans.deleteAll(plans.findByTripIdAndArchivedFalseAndStatus(id, Enums.PlanStatus.PROPOSED));
  }

  /**
   * 新一轮方案生成前清场：从未表决的旧提议直接删除，已投过票（采纳/否决）的归档为历史。
   *
   * <p>否则上一轮的方案会和新方案混在一起继续展示，群组会对已失效的旧方案重新发起投票。
   */
  private void archivePreviousRound(Long id) {
    for (AlternativePlan plan : plans.findByTripIdAndArchivedFalse(id)) {
      if (plan.getStatus() == Enums.PlanStatus.PROPOSED) {
        plans.delete(plan);
      } else {
        plan.setArchived(true);
        plans.save(plan);
      }
    }
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

  /** 当前轮次的方案（不含已归档的历史方案）。 */
  /** 当前轮：最近一次监测产出的全部方案（含已否决/已采纳），让成员看到这次监测到底给了哪些选择。 */
  public List<AlternativePlan> list(Long id) {
    List<AlternativePlan> all = plans.findByTripId(id);
    int latest = latestRound(all);
    return all.stream()
        .filter(plan -> plan.getRoundNo() == latest)
        .sorted(Comparator.comparing(AlternativePlan::getId))
        .toList();
  }

  /** 历史方案：更早轮次的方案，仅用于回溯。 */
  public List<AlternativePlan> history(Long id) {
    List<AlternativePlan> all = plans.findByTripId(id);
    int latest = latestRound(all);
    return all.stream()
        .filter(plan -> plan.getRoundNo() < latest)
        .sorted(Comparator.comparing(AlternativePlan::getId).reversed())
        .toList();
  }

  private int nextRound(Long tripId) {
    return latestRound(plans.findByTripId(tripId)) + 1;
  }

  private static int latestRound(List<AlternativePlan> all) {
    return all.stream().mapToInt(AlternativePlan::getRoundNo).max().orElse(0);
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
