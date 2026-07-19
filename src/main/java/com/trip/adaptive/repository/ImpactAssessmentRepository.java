package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ImpactAssessment;

public interface ImpactAssessmentRepository extends JpaRepository<ImpactAssessment, Long> {
  List<ImpactAssessment> findByTripId(Long tripId);

  void deleteByEvent_Id(Long eventId);
}
