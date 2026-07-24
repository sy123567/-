package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
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
import com.trip.adaptive.exception.BusinessException;
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
  private final RouteRecalculationService routing;

  public VotingService(
      AlternativePlanRepository p,
      GroupMemberRepository m,
      PlanVoteRepository v,
      TripRepository t,
      ChangeLogRepository l,
      NotificationService n,
      RouteRecalculationService routing) {
    plans = p;
    members = m;
    votes = v;
    trips = t;
    logs = l;
    notification = n;
    this.routing = routing;
  }

  @Transactional
  public AlternativePlan start(Long id) {
    AlternativePlan p = get(id);
    // 同一行程同一时间只允许一个方案处于投票：选中方案置为 VOTING，其余保持/回退到 PROPOSED。
    plans
        .findByTripId(p.getTrip().getId())
        .forEach(
            x -> {
              if (x.getId().equals(id)) x.setStatus(Enums.PlanStatus.VOTING);
              else if (x.getStatus() == Enums.PlanStatus.VOTING)
                x.setStatus(Enums.PlanStatus.PROPOSED);
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
    List<PlanVote> all = votes.findByPlanId(id);
    long approves = all.stream().filter(v -> v.getChoice() == Enums.VoteChoice.APPROVE).count();
    long rejects = all.stream().filter(v -> v.getChoice() == Enums.VoteChoice.REJECT).count();
    int total = p.getTrip().getGroup().getMembers().size();
    long cast = all.size();
    // 法定人数：需过半成员参与投票，否则维持投票中，等待更多人投票。
    if (total > 0 && cast * 2 < total) return p.getTrip();
    // 弃权不计入有效票；在有效票中赞成过半即通过。
    long effective = approves + rejects;
    boolean accepted = effective > 0 ? approves * 2 > effective : approves > 0;
    if (accepted) {
      p.setStatus(Enums.PlanStatus.ACCEPTED);
      p.getProposedNodeChanges()
          .forEach(
              c -> {
                ItineraryNode n = c.getOriginalNode();
                if (n == null) return;
                // 记录变更前快照，供后续「回退」恢复。
                c.setPrevPlaceName(n.getPlaceName());
                c.setPrevLatitude(n.getLatitude());
                c.setPrevLongitude(n.getLongitude());
                c.setPrevStart(n.getPlannedStart());
                c.setPrevEnd(n.getPlannedEnd());
                c.setPrevCost(n.getCost());
                c.setPrevStatus(n.getStatus());
                c.setApplied(true);
                if (c.getChangeType() == Enums.ChangeType.REMOVE)
                  n.setStatus(Enums.NodeStatus.CANCELLED);
                else {
                  n.setPlaceName(c.getNewPlaceName());
                  if (c.getNewLatitude() != null) n.setLatitude(c.getNewLatitude());
                  if (c.getNewLongitude() != null) n.setLongitude(c.getNewLongitude());
                  n.setPlannedStart(c.getNewStart());
                  n.setPlannedEnd(c.getNewEnd());
                  n.setCost(c.getNewCost());
                  n.setStatus(Enums.NodeStatus.REPLACED);
                }
              });
      routing.recompute(p.getTrip()); // 节点地点/坐标变化后重算受影响路段
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
    } else {
      // 已达法定人数但未获通过：否决该方案，群组可另选方案重新发起投票。
      p.setStatus(Enums.PlanStatus.REJECTED);
      notification.trip(p.getTrip().getId(), "plan-rejected", p);
    }
    return p.getTrip();
  }

  @Transactional
  public Trip revert(Long id) {
    AlternativePlan p = get(id);
    if (p.getStatus() != Enums.PlanStatus.ACCEPTED) {
      throw new BusinessException("只有已应用的方案才能回退");
    }
    p.getProposedNodeChanges()
        .forEach(
            c -> {
              if (!c.isApplied()) return;
              ItineraryNode n = c.getOriginalNode();
              if (n != null) {
                n.setPlaceName(c.getPrevPlaceName());
                n.setLatitude(c.getPrevLatitude());
                n.setLongitude(c.getPrevLongitude());
                n.setPlannedStart(c.getPrevStart());
                n.setPlannedEnd(c.getPrevEnd());
                n.setCost(c.getPrevCost());
                n.setStatus(c.getPrevStatus());
              }
              c.setApplied(false);
            });
    routing.recompute(p.getTrip()); // 节点恢复后重算路段
    // 回退后方案回到「待决策」，群组可重新发起投票。
    p.setStatus(Enums.PlanStatus.PROPOSED);
    ChangeLog l = new ChangeLog();
    l.setTrip(p.getTrip());
    l.setRelatedPlan(p);
    l.setExtraCost(BigDecimal.ZERO);
    l.setDescription("已回退替代方案：" + p.getTitle() + "，行程已恢复到变更前");
    logs.save(l);
    trips.save(p.getTrip());
    notification.trip(p.getTrip().getId(), "plan-reverted", p);
    return p.getTrip();
  }

  private AlternativePlan get(Long id) {
    return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("方案不存在"));
  }
}
