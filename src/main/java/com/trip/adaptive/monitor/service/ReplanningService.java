package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Value("${weather.replan-buffer-minutes:30}")
  private int bufferMinutes;

  public ReplanningService(
      TripRepository t,
      ImpactAssessmentRepository i,
      AlternativePlanRepository p,
      NodeChangeRepository c,
      NotificationService n,
      ExternalEventRepository e) {
    trips = t;
    impacts = i;
    plans = p;
    changes = c;
    notifications = n;
    events = e;
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
        c.setNote(reschedule ? "天气事件结束后顺延并校验时段" : "当前事件无法安全避让，建议移除节点");
        p.getProposedNodeChanges().add(c);
      }
      p.setExtraDelayMinutes(maxDelay);
      out.add(plans.save(p));
    }
    if (!out.isEmpty()) notifications.trip(id, "new-plans", out);
    return out;
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

  public List<AlternativePlan> list(Long id) {
    return plans.findByTripId(id);
  }

  public AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }
}
