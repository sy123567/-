package com.trip.adaptive;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.UserNotification;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ChangeLogRepository;
import com.trip.adaptive.repository.DiscussionPostRepository;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.NodeNoteRepository;
import com.trip.adaptive.repository.TripExpenseRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.repository.UserNotificationRepository;
import com.trip.adaptive.service.TripDeletionService;

class TripDeletionServiceTest {

  private final TripRepository trips = mock(TripRepository.class);
  private final UserNotificationRepository notifications = mock(UserNotificationRepository.class);

  private TripDeletionService service() {
    return new TripDeletionService(
        trips,
        mock(ChangeLogRepository.class),
        mock(AlternativePlanRepository.class),
        mock(ImpactAssessmentRepository.class),
        mock(ExternalEventRepository.class),
        mock(TripExpenseRepository.class),
        mock(DiscussionPostRepository.class),
        mock(NodeNoteRepository.class),
        notifications);
  }

  /** 站内通知引用了行程，必须先删掉，否则会撞上 user_notification.trip_id 外键。 */
  @Test
  void clearsTripNotificationsBeforeDeletingTrip() {
    Trip trip = new Trip();
    ReflectionTestUtils.setField(trip, "id", 7L);
    List<UserNotification> pending = List.of(new UserNotification());
    when(notifications.findByTripId(7L)).thenReturn(pending);

    service().delete(trip);

    InOrder order = inOrder(notifications, trips);
    order.verify(notifications).deleteAll(pending);
    order.verify(trips).delete(trip);
  }
}
