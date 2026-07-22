package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.monitor.service.ImpactMatchingService;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.TripRepository;

class ImpactMatchingServiceTest {
  @Test
  void haversineDistanceIsReasonable() {
    assertEquals(0, ImpactMatchingService.distance(31.23, 121.47, 31.23, 121.47), 0.001);
    assertTrue(ImpactMatchingService.distance(31.23, 121.47, 31.24, 121.47) < 2);
  }

  @Test
  void assessesOnlyEventsBelongingToTheTrip() {
    TripRepository trips = mock(TripRepository.class);
    ExternalEventRepository events = mock(ExternalEventRepository.class);
    ImpactAssessmentRepository assessments = mock(ImpactAssessmentRepository.class);
    ItineraryNodeRepository nodes = mock(ItineraryNodeRepository.class);
    Trip trip = mock(Trip.class);

    when(trips.findById(7L)).thenReturn(Optional.of(trip));
    when(trip.getItineraryNodes()).thenReturn(List.of());
    when(assessments.findByTripId(7L)).thenReturn(List.of());
    when(events.findByTripIdAndEndTimeAfter(eq(7L), any())).thenReturn(List.of());

    new ImpactMatchingService(trips, events, assessments, nodes).assessTrip(7L);

    verify(events).findByTripIdAndEndTimeAfter(eq(7L), any());
    verify(events, never()).findByEndTimeAfter(any());
  }
}
