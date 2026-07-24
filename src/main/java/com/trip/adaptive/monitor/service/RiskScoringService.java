package com.trip.adaptive.monitor.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.dto.TripRiskResult;
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

  public TripRiskResult scoreTrip(Long id) {
    Trip t = trips.findById(id).orElseThrow(() -> new RuntimeException("Trip not found"));
    List<ImpactAssessment> a = repo.findByTripId(id);
    LocalDateTime now = LocalDateTime.now();
    int max = 0;
    for (ImpactAssessment x : a) {
      int base = x.getRiskScore();
      int score = Math.min(100, base + imminence(x, now));
      x.setRiskScore(score);
      repo.save(x);
      if (score > max) max = score;
    }
    int affected =
        (int)
            a.stream()
                .map(ImpactAssessment::getAffectedNode)
                .filter(n -> n != null && n.getId() != null)
                .map(ItineraryNode::getId)
                .distinct()
                .count();
    int totalNodes = t.getItineraryNodes().size();
    double breadth = totalNodes == 0 ? 0 : (double) affected / totalNodes; // 受影响节点占比
    int overall = affected == 0 ? 0 : (int) Math.min(100, max + breadth * 20);
    return new TripRiskResult(id, overall, level(overall), totalNodes, affected, a);
  }

  private int imminence(ImpactAssessment a, LocalDateTime now) {
    ItineraryNode n = a.getAffectedNode();
    if (n == null || n.getPlannedStart() == null) return 0;
    long h = Duration.between(now, n.getPlannedStart()).toHours();
    if (h < 0) return 0; // 已开始/已过：不额外加
    if (h <= 24) return 15;
    if (h <= 72) return 5;
    return 0;
  }

  private String level(int s) {
    if (s < 30) return "LOW";
    if (s < 60) return "MEDIUM";
    if (s < 85) return "HIGH";
    return "CRITICAL";
  }
}
