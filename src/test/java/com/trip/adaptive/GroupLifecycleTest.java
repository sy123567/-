package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.repository.ChatMessageRepository;
import com.trip.adaptive.repository.ConversationRepository;
import com.trip.adaptive.repository.FriendshipRepository;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.MemberConstraintRepository;
import com.trip.adaptive.repository.PlanVoteRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.repository.UserRepository;
import com.trip.adaptive.service.GroupService;
import com.trip.adaptive.service.TripDeletionService;

class GroupLifecycleTest {
  private final TravelGroupRepository groups = mock(TravelGroupRepository.class);
  private final GroupMemberRepository members = mock(GroupMemberRepository.class);
  private final TripRepository trips = mock(TripRepository.class);
  private final TripDeletionService tripDeletion = mock(TripDeletionService.class);
  private final ConversationRepository conversations = mock(ConversationRepository.class);
  private final MemberConstraintRepository constraints = mock(MemberConstraintRepository.class);
  private final PlanVoteRepository votes = mock(PlanVoteRepository.class);

  private GroupService service() {
    return new GroupService(
        groups,
        mock(UserRepository.class),
        members,
        constraints,
        mock(FriendshipRepository.class),
        votes,
        trips,
        tripDeletion,
        conversations,
        mock(ChatMessageRepository.class));
  }

  private User user(long id) {
    User user = mock(User.class);
    when(user.getId()).thenReturn(id);
    return user;
  }

  private GroupMember membership(long id, Enums.MemberRole role) {
    GroupMember member = mock(GroupMember.class);
    when(member.getId()).thenReturn(id);
    when(member.getRole()).thenReturn(role);
    return member;
  }

  private TravelGroup group(long id) {
    TravelGroup group = mock(TravelGroup.class);
    when(groups.findById(id)).thenReturn(Optional.of(group));
    return group;
  }

  @Test
  void ownerCannotLeaveWithoutTransferringOwnership() {
    group(3L);
    User owner = user(1L);
    GroupMember membership = membership(10L, Enums.MemberRole.OWNER);
    when(members.findByGroupIdAndUserId(3L, 1L)).thenReturn(Optional.of(membership));

    assertThrows(BusinessException.class, () -> service().leave(3L, owner));
    verify(members, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void memberLeavingDropsConstraintsAndVotes() {
    group(3L);
    User member = user(2L);
    GroupMember membership = membership(10L, Enums.MemberRole.MEMBER);
    when(members.findByGroupIdAndUserId(3L, 2L)).thenReturn(Optional.of(membership));
    when(votes.findByMemberId(10L)).thenReturn(List.of());

    service().leave(3L, member);

    verify(constraints).deleteByMemberId(10L);
    verify(members).delete(membership);
  }

  @Test
  void onlyOwnerCanDisbandTheGroup() {
    group(3L);
    User member = user(2L);
    GroupMember membership = membership(10L, Enums.MemberRole.MEMBER);
    when(members.findByGroupIdAndUserId(3L, 2L)).thenReturn(Optional.of(membership));

    assertThrows(BusinessException.class, () -> service().disband(3L, member));
    verify(groups, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void disbandingRemovesTripsAndTheGroup() {
    TravelGroup group = group(3L);
    User owner = user(1L);
    GroupMember membership = membership(10L, Enums.MemberRole.OWNER);
    Trip trip = mock(Trip.class);
    when(members.findByGroupIdAndUserId(3L, 1L)).thenReturn(Optional.of(membership));
    when(trips.findByGroupId(3L)).thenReturn(List.of(trip));
    when(members.findByGroupId(3L)).thenReturn(List.of(membership));
    when(votes.findByMemberId(10L)).thenReturn(List.of());
    when(conversations.findByTypeAndGroupId(Enums.ConversationType.GROUP, 3L))
        .thenReturn(Optional.empty());

    service().disband(3L, owner);

    verify(tripDeletion).delete(trip);
    verify(constraints).deleteByMemberId(10L);
    verify(groups).delete(group);
  }
}
