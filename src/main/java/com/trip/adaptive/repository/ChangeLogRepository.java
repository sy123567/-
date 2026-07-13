package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ChangeLog;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {
  List<ChangeLog> findByTripId(Long tripId);
}
