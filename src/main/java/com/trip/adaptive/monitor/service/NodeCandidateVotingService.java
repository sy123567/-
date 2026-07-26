package com.trip.adaptive.monitor.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.NodeCandidateVote;
import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.NodeCandidateVoteRepository;
import com.trip.adaptive.repository.NodeChangeRepository;

/**
 * 节点级投票：受影响节点换到哪里由成员一起决定。
 *
 * <p>成员可以投给某个通过校验的候选地点、维持方案里的原安排，或者弃权，每票都能附备注。
 * 参与人数过半（法定人数）且有唯一领先项时自动落定：领先项是候选地点就写回节点变更，是「维持原安排」则什么都不动。
 */
@Service
public class NodeCandidateVotingService {
  /** 维持方案原安排的选项键，与候选地点名区分开。 */
  public static final String KEEP_PLAN_KEY = "__keep_plan__";

  private final NodeChangeRepository changes;
  private final NodeCandidateVoteRepository votes;
  private final GroupMemberRepository members;
  private final ReplanningService replanning;
  private final NotificationService notifications;

  public NodeCandidateVotingService(
      NodeChangeRepository changes,
      NodeCandidateVoteRepository votes,
      GroupMemberRepository members,
      ReplanningService replanning,
      NotificationService notifications) {
    this.changes = changes;
    this.votes = votes;
    this.members = members;
    this.replanning = replanning;
    this.notifications = notifications;
  }

  @Transactional
  public NodeVoteTally vote(
      Long changeId,
      Long memberId,
      Enums.NodeVoteChoice choice,
      String placeName,
      Double lat,
      Double lng,
      String comment) {
    NodeChange change = change(changeId);
    requireOpen(change);
    GroupMember member =
        members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    NodeCandidateVote vote =
        votes.findByNodeChangeIdAndMemberId(changeId, memberId).orElseGet(NodeCandidateVote::new);
    vote.setNodeChange(change);
    vote.setMember(member);
    vote.setChoice(choice);
    vote.setComment(comment);
    if (choice == Enums.NodeVoteChoice.CANDIDATE) {
      Candidate verified = verify(changeId, placeName, lat, lng);
      vote.setPlaceName(verified.name());
      vote.setLatitude(verified.lat());
      vote.setLongitude(verified.lng());
    } else {
      vote.setPlaceName(null);
      vote.setLatitude(null);
      vote.setLongitude(null);
    }
    vote.setVotedAt(java.time.LocalDateTime.now());
    votes.save(vote);
    notifications.trip(change.getPlan().getTrip().getId(), "node-vote", changeId);
    return tally(changeId);
  }

  /** 计票，并在达到法定人数且有唯一领先项时把结果写回节点变更。 */
  @Transactional
  public NodeVoteTally tally(Long changeId) {
    NodeChange change = change(changeId);
    NodeVoteTally result = compute(change, votes.findByNodeChangeIdOrderByVotedAtAsc(changeId));
    if (!result.decided() || result.options().isEmpty()) return result;
    OptionTally leading = result.options().get(0);
    if (KEEP_PLAN_KEY.equals(leading.key())) return result.withApplied("维持方案原安排");
    if (!isOpen(change)) return result;
    replanning.chooseReplacement(
        changeId,
        Candidate.of(leading.label(), leading.lat(), leading.lng(), null, "vote", "成员投票选出的替代地点"));
    return result.withApplied(leading.label());
  }

  /** 只读计票：给页面展示用，不会把结果写回节点变更。 */
  @Transactional(readOnly = true)
  public NodeVoteTally summary(Long changeId) {
    Long id = changeId;
    return compute(change(id), votes.findByNodeChangeIdOrderByVotedAtAsc(id));
  }

  private NodeVoteTally compute(NodeChange change, List<NodeCandidateVote> cast) {
    Map<String, OptionTally> byOption = new LinkedHashMap<>();
    List<VoteNote> notes = new ArrayList<>();
    int abstain = 0;
    for (NodeCandidateVote vote : cast) {
      String memberName =
          vote.getMember().getUser() == null ? "成员" : vote.getMember().getUser().getName();
      notes.add(
          new VoteNote(
              memberName,
              vote.getChoice(),
              vote.getChoice() == Enums.NodeVoteChoice.CANDIDATE ? vote.getPlaceName() : null,
              vote.getComment(),
              vote.getVotedAt()));
      if (vote.getChoice() == Enums.NodeVoteChoice.ABSTAIN) {
        abstain++;
        continue;
      }
      boolean keepPlan = vote.getChoice() == Enums.NodeVoteChoice.KEEP_PLAN;
      String key = keepPlan ? KEEP_PLAN_KEY : vote.getPlaceName();
      if (key == null) continue;
      OptionTally current =
          byOption.computeIfAbsent(
              key,
              k ->
                  new OptionTally(
                      k,
                      keepPlan ? "维持方案原安排" : vote.getPlaceName(),
                      keepPlan ? null : vote.getLatitude(),
                      keepPlan ? null : vote.getLongitude(),
                      0,
                      new ArrayList<>()));
      current.voters().add(memberName);
      byOption.put(key, current.withCount(current.count() + 1));
    }
    List<OptionTally> options =
        byOption.values().stream()
            .sorted(Comparator.comparingInt(OptionTally::count).reversed())
            .toList();
    int totalMembers = change.getPlan().getTrip().getGroup().getMembers().size();
    // 过半成员表态（含弃权）算达到法定人数，仅用于展示进度。
    boolean quorum = totalMembers == 0 || cast.size() * 2 >= totalMembers;
    OptionTally leading = options.isEmpty() ? null : options.get(0);
    boolean tie =
        options.size() > 1 && options.get(1).count() == (leading == null ? 0 : leading.count());
    // 落定条件：某一项获得全体成员过半支持，或全员表态完毕且有唯一领先项；平票一律不落定。
    boolean decided =
        leading != null
            && !tie
            && (leading.count() * 2 > totalMembers || cast.size() >= totalMembers);
    return new NodeVoteTally(
        change.getId(),
        totalMembers,
        cast.size(),
        abstain,
        quorum,
        decided,
        tie,
        null,
        isOpen(change),
        options,
        notes);
  }

  private Candidate verify(Long changeId, String placeName, Double lat, Double lng) {
    if (placeName == null || placeName.isBlank()) throw new BusinessException("请先选择一个候选地点");
    return replanning.candidatesFor(changeId).stream()
        .filter(
            c ->
                c.name().equalsIgnoreCase(placeName)
                    || (lat != null
                        && lng != null
                        && ImpactMatchingService.distance(c.lat(), c.lng(), lat, lng) < 0.05))
        .findFirst()
        .orElseThrow(() -> new BusinessException("该地点已不在通过校验的候选中，请刷新后重投"));
  }

  private NodeChange change(Long changeId) {
    return changes.findById(changeId).orElseThrow(() -> new ResourceNotFoundException("节点变更不存在"));
  }

  private void requireOpen(NodeChange change) {
    if (!isOpen(change)) throw new BusinessException("方案已进入投票或已归档，节点投票已结束");
  }

  private static boolean isOpen(NodeChange change) {
    AlternativePlan plan = change.getPlan();
    return !plan.isArchived() && plan.getStatus() == Enums.PlanStatus.PROPOSED;
  }

  /** 一个可投的选项及其得票。 */
  public record OptionTally(
      String key, String label, Double lat, Double lng, int count, List<String> voters) {
    OptionTally withCount(int value) {
      return new OptionTally(key, label, lat, lng, value, voters);
    }
  }

  /** 一条投票备注。 */
  public record VoteNote(
      String member,
      Enums.NodeVoteChoice choice,
      String option,
      String comment,
      java.time.LocalDateTime votedAt) {}

  /** 节点投票的实时结果。 */
  public record NodeVoteTally(
      Long changeId,
      int totalMembers,
      int castCount,
      int abstainCount,
      boolean quorumReached,
      boolean decided,
      boolean tie,
      String appliedOption,
      boolean open,
      List<OptionTally> options,
      List<VoteNote> notes) {

    NodeVoteTally withApplied(String option) {
      return new NodeVoteTally(
          changeId,
          totalMembers,
          castCount,
          abstainCount,
          quorumReached,
          decided,
          tie,
          option,
          open,
          options,
          notes);
    }
  }
}
