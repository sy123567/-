package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.NodeCandidateVote;
import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.monitor.service.NodeCandidateVotingService;
import com.trip.adaptive.monitor.service.NodeCandidateVotingService.NodeVoteTally;
import com.trip.adaptive.monitor.service.NotificationService;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.monitor.service.ReplanningService;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.NodeCandidateVoteRepository;
import com.trip.adaptive.repository.NodeChangeRepository;

class NodeCandidateVotingServiceTest {
  private final NodeChangeRepository changes = mock(NodeChangeRepository.class);
  private final NodeCandidateVoteRepository votes = mock(NodeCandidateVoteRepository.class);
  private final GroupMemberRepository members = mock(GroupMemberRepository.class);
  private final ReplanningService replanning = mock(ReplanningService.class);
  private final NotificationService notifications = mock(NotificationService.class);

  private final NodeCandidateVotingService service =
      new NodeCandidateVotingService(changes, votes, members, replanning, notifications);

  private final List<NodeCandidateVote> stored = new ArrayList<>();
  private NodeChange change;
  private TravelGroup group;

  @BeforeEach
  void setUp() {
    group = new TravelGroup();
    ReflectionTestUtils.setField(group, "id", 5L);
    Trip trip = new Trip();
    ReflectionTestUtils.setField(trip, "id", 9L);
    trip.setGroup(group);
    AlternativePlan plan = new AlternativePlan();
    ReflectionTestUtils.setField(plan, "id", 3L);
    plan.setTrip(trip);
    plan.setStatus(Enums.PlanStatus.PROPOSED);
    change = new NodeChange();
    ReflectionTestUtils.setField(change, "id", 11L);
    change.setPlan(plan);
    change.setChangeType(Enums.ChangeType.REPLACE);
    change.setNewPlaceName("原方案的博物馆");

    when(changes.findById(11L)).thenReturn(Optional.of(change));
    when(votes.findByNodeChangeIdOrderByVotedAtAsc(11L)).thenReturn(stored);
    when(votes.save(any(NodeCandidateVote.class)))
        .thenAnswer(
            invocation -> {
              NodeCandidateVote vote = invocation.getArgument(0);
              stored.removeIf(existing -> existing.getMember().equals(vote.getMember()));
              stored.add(vote);
              return vote;
            });
    when(replanning.candidatesFor(11L))
        .thenReturn(
            List.of(
                Candidate.of("城市博物馆", 31.205, 121.405, null, "nearby", "室内可避雨"),
                Candidate.of("美术馆", 31.207, 121.402, null, "ai", "室内展览")));
  }

  private GroupMember member(long id, String name) {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", id);
    user.setName(name);
    GroupMember m = new GroupMember();
    ReflectionTestUtils.setField(m, "id", id);
    m.setUser(user);
    m.setGroup(group);
    group.getMembers().add(m);
    when(members.findById(id)).thenReturn(Optional.of(m));
    return m;
  }

  @Test
  void candidateVoteMustMatchAServerVerifiedCandidate() {
    member(1L, "小林");
    assertThrows(
        BusinessException.class,
        () -> service.vote(11L, 1L, Enums.NodeVoteChoice.CANDIDATE, "自己编的地方", 30.0, 120.0, "想去这里"));
  }

  @Test
  void repeatedVoteFromSameMemberIsUpdatedNotDuplicated() {
    member(1L, "小林");
    member(2L, "阿元");
    service.vote(11L, 1L, Enums.NodeVoteChoice.CANDIDATE, "城市博物馆", 31.205, 121.405, "带老人");
    service.vote(11L, 1L, Enums.NodeVoteChoice.ABSTAIN, null, null, null, "我都行");
    assertEquals(1, stored.size());
    assertEquals(Enums.NodeVoteChoice.ABSTAIN, stored.get(0).getChoice());
    assertEquals("我都行", stored.get(0).getComment());
  }

  @Test
  void majorityCandidateIsAppliedToTheNodeChange() {
    member(1L, "小林");
    member(2L, "阿元");
    service.vote(11L, 1L, Enums.NodeVoteChoice.CANDIDATE, "城市博物馆", 31.205, 121.405, "室内更稳妥");
    NodeVoteTally tally =
        service.vote(11L, 2L, Enums.NodeVoteChoice.CANDIDATE, "城市博物馆", 31.205, 121.405, null);
    assertTrue(tally.decided());
    assertEquals("城市博物馆", tally.appliedOption());
    verify(replanning).chooseReplacement(anyLong(), any(Candidate.class));
  }

  @Test
  void tieKeepsTheNodeUndecided() {
    member(1L, "小林");
    member(2L, "阿元");
    service.vote(11L, 1L, Enums.NodeVoteChoice.CANDIDATE, "城市博物馆", 31.205, 121.405, null);
    NodeVoteTally tally =
        service.vote(11L, 2L, Enums.NodeVoteChoice.CANDIDATE, "美术馆", 31.207, 121.402, null);
    assertTrue(tally.tie());
    assertFalse(tally.decided());
    verify(replanning, never()).chooseReplacement(anyLong(), any(Candidate.class));
  }

  @Test
  void abstainCountsForQuorumButNotForAnyOption() {
    member(1L, "小林");
    member(2L, "阿元");
    NodeVoteTally first =
        service.vote(11L, 1L, Enums.NodeVoteChoice.ABSTAIN, null, null, null, "不熟");
    assertEquals(1, first.abstainCount());
    assertTrue(first.options().isEmpty());
    assertFalse(first.decided());
    NodeVoteTally tally =
        service.vote(11L, 2L, Enums.NodeVoteChoice.KEEP_PLAN, null, null, null, "先不换");
    assertTrue(tally.decided());
    assertEquals("维持方案原安排", tally.appliedOption());
    verify(replanning, never()).chooseReplacement(anyLong(), any(Candidate.class));
  }

  @Test
  void votingIsClosedOnceThePlanLeavesProposed() {
    member(1L, "小林");
    change.getPlan().setStatus(Enums.PlanStatus.VOTING);
    assertThrows(
        BusinessException.class,
        () ->
            service.vote(11L, 1L, Enums.NodeVoteChoice.CANDIDATE, "城市博物馆", 31.205, 121.405, null));
  }
}
