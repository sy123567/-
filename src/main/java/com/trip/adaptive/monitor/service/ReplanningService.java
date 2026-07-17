package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.AlternativePlanRepository;
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

  public ReplanningService(
      TripRepository t,
      ImpactAssessmentRepository i,
      AlternativePlanRepository p,
      NodeChangeRepository c,
      NotificationService n) {
    trips = t;
    impacts = i;
    plans = p;
    changes = c;
    notifications = n;
  }

  @Transactional
  public List<AlternativePlan> generate(Long id) {
    Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    List<ItineraryNode> affected =
        impacts.findByTripId(id).stream()
            .map(ImpactAssessment::getAffectedNode)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<AlternativePlan> out = new ArrayList<>();
    for (Enums.ReplanStrategy s : Enums.ReplanStrategy.values()) {
      AlternativePlan p = new AlternativePlan();
      p.setTrip(t);
      p.setStrategy(s);
      p.setTitle("替代方案-" + s);
      p.setStatus(Enums.PlanStatus.PROPOSED);
      p.setExtraCost(
          s == Enums.ReplanStrategy.MIN_EXTRA_COST ? BigDecimal.ZERO : new BigDecimal("30"));
      p.setExtraDelayMinutes(s == Enums.ReplanStrategy.MIN_DELAY ? 15 : 45);
      p.setChangedNodeCount(affected.size());
      p.setSummary("针对 " + affected.size() + " 个受影响节点，采用 " + s + " 策略生成。");
      for (ItineraryNode n : affected) {
        NodeChange c = new NodeChange();
        c.setPlan(p);
        c.setOriginalNode(n);
        c.setChangeType(
            s == Enums.ReplanStrategy.MIN_CHANGE
                ? Enums.ChangeType.REMOVE
                : Enums.ChangeType.RESCHEDULE);
        c.setNewPlaceName(
            s == Enums.ReplanStrategy.MIN_CHANGE ? null : n.getPlaceName() + "（替代时段）");
        c.setNewStart(n.getPlannedStart().plusMinutes(p.getExtraDelayMinutes()));
        c.setNewEnd(n.getPlannedEnd().plusMinutes(p.getExtraDelayMinutes()));
        c.setNewCost(n.getCost().add(p.getExtraCost()));
        c.setNote("自动生成的可执行调整");
        p.getProposedNodeChanges().add(c);
      }
      out.add(plans.save(p));
    }
    notifications.trip(id, "new-plans", out);
    return out;
  }

  public List<AlternativePlan> list(Long id) {
    return plans.findByTripId(id);
  }

  public AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }
}
