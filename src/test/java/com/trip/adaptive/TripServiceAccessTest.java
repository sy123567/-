package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.NodeNoteRepository;
import com.trip.adaptive.repository.RouteRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.service.TripDeletionService;
import com.trip.adaptive.service.TripService;

class TripServiceAccessTest {
  @Test
  void rejectsUsersOutsideTheTripGroup() {
    TripRepository trips = mock(TripRepository.class);
    GroupMemberRepository members = mock(GroupMemberRepository.class);
    TravelGroup group = mock(TravelGroup.class);
    Trip trip = mock(Trip.class);
    User user = mock(User.class);

    when(trips.findById(7L)).thenReturn(Optional.of(trip));
    when(trip.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(11L);
    when(group.getOwnerUser()).thenReturn(null);
    when(user.getId()).thenReturn(99L);
    when(members.findByGroupIdAndUserId(11L, 99L)).thenReturn(Optional.empty());

    TripService service =
        new TripService(
            trips,
            mock(TravelGroupRepository.class),
            members,
            mock(ItineraryNodeRepository.class),
            mock(NodeNoteRepository.class),
            mock(RouteRepository.class),
            mock(TripDeletionService.class));

    assertThrows(ResourceNotFoundException.class, () -> service.requireMember(7L, user));
  }

  @Test
  void allowsMembersOfTheTripGroup() {
    TripRepository trips = mock(TripRepository.class);
    GroupMemberRepository members = mock(GroupMemberRepository.class);
    TravelGroup group = mock(TravelGroup.class);
    Trip trip = mock(Trip.class);
    User user = mock(User.class);

    when(trips.findById(7L)).thenReturn(Optional.of(trip));
    when(trip.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(11L);
    when(user.getId()).thenReturn(99L);
    when(members.findByGroupIdAndUserId(11L, 99L)).thenReturn(Optional.of(mock(GroupMember.class)));

    TripService service =
        new TripService(
            trips,
            mock(TravelGroupRepository.class),
            members,
            mock(ItineraryNodeRepository.class),
            mock(NodeNoteRepository.class),
            mock(RouteRepository.class),
            mock(TripDeletionService.class));

    assertDoesNotThrow(() -> service.requireMember(7L, user));
  }

  @Test
  void onlyTheGroupOwnerCanDeleteATrip() {
    TripRepository trips = mock(TripRepository.class);
    GroupMemberRepository members = mock(GroupMemberRepository.class);
    TripDeletionService deletion = mock(TripDeletionService.class);
    TravelGroup group = mock(TravelGroup.class);
    Trip trip = mock(Trip.class);
    User user = mock(User.class);
    GroupMember membership = mock(GroupMember.class);

    when(trips.findById(7L)).thenReturn(Optional.of(trip));
    when(trip.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(11L);
    when(user.getId()).thenReturn(99L);
    when(members.findByGroupIdAndUserId(11L, 99L)).thenReturn(Optional.of(membership));
    when(membership.getRole()).thenReturn(Enums.MemberRole.MEMBER);

    TripService service = newService(trips, members, deletion);

    assertThrows(BusinessException.class, () -> service.delete(7L, user));
    verify(deletion, never()).delete(trip);
  }

  @Test
  void deletesTheTripForTheGroupOwner() {
    TripRepository trips = mock(TripRepository.class);
    GroupMemberRepository members = mock(GroupMemberRepository.class);
    TripDeletionService deletion = mock(TripDeletionService.class);
    TravelGroup group = mock(TravelGroup.class);
    Trip trip = mock(Trip.class);
    User user = mock(User.class);
    GroupMember membership = mock(GroupMember.class);

    when(trips.findById(7L)).thenReturn(Optional.of(trip));
    when(trip.getGroup()).thenReturn(group);
    when(group.getId()).thenReturn(11L);
    when(user.getId()).thenReturn(99L);
    when(members.findByGroupIdAndUserId(11L, 99L)).thenReturn(Optional.of(membership));
    when(membership.getRole()).thenReturn(Enums.MemberRole.OWNER);

    newService(trips, members, deletion).delete(7L, user);

    verify(deletion).delete(trip);
  }

  private static TripService newService(
      TripRepository trips, GroupMemberRepository members, TripDeletionService deletion) {
    return new TripService(
        trips,
        mock(TravelGroupRepository.class),
        members,
        mock(ItineraryNodeRepository.class),
        mock(NodeNoteRepository.class),
        mock(RouteRepository.class),
        deletion);
  }
}
