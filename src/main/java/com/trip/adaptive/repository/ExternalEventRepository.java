package com.trip.adaptive.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ExternalEvent;

public interface ExternalEventRepository extends JpaRepository<ExternalEvent, Long> {
  List<ExternalEvent> findByEndTimeAfter(LocalDateTime time);

  List<ExternalEvent> findByTripIdAndEndTimeAfter(Long tripId, LocalDateTime time);

  boolean existsBySourceAndPlaceNameAndStartTimeAndTripId(
      String source, String placeName, java.time.LocalDateTime startTime, Long tripId);

  List<ExternalEvent> findBySourceStartingWithAndPlaceName(String source, String placeName);

  List<ExternalEvent> findBySourceStartingWithAndPlaceNameAndTripId(
      String source, String placeName, Long tripId);
}
