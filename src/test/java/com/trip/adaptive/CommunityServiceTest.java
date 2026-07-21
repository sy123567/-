package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.domain.DiscussionPost;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.domain.UserNotification;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.DiscussionPostRepository;
import com.trip.adaptive.repository.UserNotificationRepository;
import com.trip.adaptive.service.DiscussionService;
import com.trip.adaptive.service.UserNotificationService;

class CommunityServiceTest {

  private static User user(long id) {
    User u = new User("成员" + id, "u" + id + "@example.com", "pw", "");
    org.springframework.test.util.ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  @Test
  void toggleLikeAddsThenRemovesSameUser() {
    DiscussionPostRepository repo = mock(DiscussionPostRepository.class);
    DiscussionService service =
        new DiscussionService(repo, mock(com.trip.adaptive.repository.TripRepository.class));
    DiscussionPost post = new DiscussionPost(new Trip(), user(1), "hi");
    when(repo.findById(7L)).thenReturn(Optional.of(post));
    when(repo.save(any(DiscussionPost.class))).thenAnswer(i -> i.getArgument(0));

    service.toggleLike(7L, user(2));
    assertTrue(post.getLikedBy().contains(2L));
    service.toggleLike(7L, user(2));
    assertFalse(post.getLikedBy().contains(2L));
  }

  @Test
  void markReadRejectsOtherUsersNotification() {
    UserNotificationRepository repo = mock(UserNotificationRepository.class);
    UserNotificationService service = new UserNotificationService(repo);
    UserNotification n = new UserNotification(user(1), new Trip(), "new-plans", "t", "d");
    when(repo.findById(5L)).thenReturn(Optional.of(n));

    assertThrows(ResourceNotFoundException.class, () -> service.markRead(user(2), 5L));
  }

  @Test
  void notifyTripMembersWritesOnePerMember() {
    UserNotificationRepository repo = mock(UserNotificationRepository.class);
    UserNotificationService service = new UserNotificationService(repo);
    User owner = user(1);
    TravelGroup group = new TravelGroup("g", "d", owner);
    group.getMembers().add(new GroupMember(group, owner, Enums.MemberRole.OWNER));
    group.getMembers().add(new GroupMember(group, user(2), Enums.MemberRole.MEMBER));
    Trip trip = new Trip();
    trip.setGroup(group);

    service.notifyTripMembers(trip, "new-plans", "标题", "详情");

    verify(repo, times(2)).save(any(UserNotification.class));
  }

  @Test
  void notifyTripMembersIgnoresTripWithoutGroup() {
    UserNotificationRepository repo = mock(UserNotificationRepository.class);
    UserNotificationService service = new UserNotificationService(repo);

    service.notifyTripMembers(new Trip(), "new-plans", "标题", "详情");

    verify(repo, times(0)).save(any(UserNotification.class));
    assertEquals(0, 0);
  }
}
