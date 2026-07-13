package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.Enums;

public interface AlternativePlanRepository extends JpaRepository<AlternativePlan, Long> {
  List<AlternativePlan> findByTripId(Long tripId);

  List<AlternativePlan> findByTripIdAndStatus(Long tripId, Enums.PlanStatus status);
}
