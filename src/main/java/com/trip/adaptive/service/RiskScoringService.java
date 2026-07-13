package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class RiskScoringService {
  private final ImpactAssessmentRepository repo;
  private final TripRepository trips;

  public RiskScoringService(ImpactAssessmentRepository r, TripRepository t) {
    repo = r;
    trips = t;
  }

  public List<ImpactAssessment> scoreTrip(Long id) {
    List<ImpactAssessment> a = repo.findByTripId(id);
    int count = a.size();
    a.forEach(x -> x.setRiskScore(Math.min(100, x.getRiskScore() + Math.min(20, count * 5))));
    return a;
  }
}
