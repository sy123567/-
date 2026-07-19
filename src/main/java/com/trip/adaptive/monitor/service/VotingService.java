package com.trip.adaptive.monitor.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.ChangeLog;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.PlanVote;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ChangeLogRepository;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.PlanVoteRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class VotingService {
  private final AlternativePlanRepository plans;
  private final GroupMemberRepository members;
  private final PlanVoteRepository votes;
  private final TripRepository trips;
  private final ChangeLogRepository logs;
  private final NotificationService notification;

  public VotingService(
      AlternativePlanRepository p,
      GroupMemberRepository m,
      PlanVoteRepository v,
      TripRepository t,
      ChangeLogRepository l,
      NotificationService n) {
    plans = p;
    members = m;
    votes = v;
    trips = t;
    logs = l;
    notification = n;
  }

  @Transactional
  public AlternativePlan start(Long id) {
    AlternativePlan p = get(id);
    plans
        .findByTripId(p.getTrip().getId())
        .forEach(
            x -> {
              if (x.getStatus() == Enums.PlanStatus.PROPOSED) x.setStatus(Enums.PlanStatus.VOTING);
            });
    return p;
  }

  @Transactional
  public PlanVote vote(Long pid, Long mid, Enums.VoteChoice choice, String comment) {
    AlternativePlan p = get(pid);
    GroupMember m = members.findById(mid).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    PlanVote v = votes.findByPlanIdAndMemberId(pid, mid).orElseGet(PlanVote::new);
    v.setPlan(p);
    v.setMember(m);
    v.setChoice(choice);
    v.setComment(comment);
    return votes.save(v);
  }

  public List<PlanVote> votes(Long id) {
    return votes.findByPlanId(id);
  }

  @Transactional
  public Trip tally(Long id) {
    AlternativePlan p = get(id);
    long approves =
        votes.findByPlanId(id).stream()
            .filter(v -> v.getChoice() == Enums.VoteChoice.APPROVE)
            .count();
    int total = p.getTrip().getGroup().getMembers().size();
    if (approves > total / 2) {
      p.setStatus(Enums.PlanStatus.ACCEPTED);
      p.getProposedNodeChanges()
          .forEach(
              c -> {
                ItineraryNode n = c.getOriginalNode();
                if (c.getChangeType() == Enums.ChangeType.REMOVE)
                  n.setStatus(Enums.NodeStatus.CANCELLED);
                else {
                  n.setPlaceName(c.getNewPlaceName());
                  n.setPlannedStart(c.getNewStart());
                  n.setPlannedEnd(c.getNewEnd());
                  n.setCost(c.getNewCost());
                  n.setStatus(Enums.NodeStatus.REPLACED);
                }
              });
      plans.findByTripId(p.getTrip().getId()).stream()
          .filter(x -> !x.getId().equals(id))
          .forEach(x -> x.setStatus(Enums.PlanStatus.REJECTED));
      ChangeLog l = new ChangeLog();
      l.setTrip(p.getTrip());
      l.setRelatedPlan(p);
      l.setExtraCost(p.getExtraCost());
      l.setRefundDeadline(LocalDateTime.now().plusHours(24));
      l.setDescription("集体投票通过替代方案：" + p.getTitle());
      logs.save(l);
      trips.save(p.getTrip());
      notification.trip(p.getTrip().getId(), "plan-accepted", p);
    }
    return p.getTrip();
  }

  private AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }
}
