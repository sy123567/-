package com.trip.adaptive.monitor.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ImpactAssessment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class ImpactMatchingService {
  private final TripRepository trips;
  private final ExternalEventRepository events;
  private final ImpactAssessmentRepository assessments;
  private final ItineraryNodeRepository nodes;

  public ImpactMatchingService(
      TripRepository t,
      ExternalEventRepository e,
      ImpactAssessmentRepository a,
      ItineraryNodeRepository n) {
    trips = t;
    events = e;
    assessments = a;
    nodes = n;
  }

  @Transactional
  public List<ImpactAssessment> assessTrip(Long id) {
    Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    assessments.deleteAll(assessments.findByTripId(id));
    for (ItineraryNode n : t.getItineraryNodes()) {
      if (n.getStatus() == Enums.NodeStatus.AFFECTED) {
        n.setStatus(Enums.NodeStatus.PLANNED); // 事件消失后恢复,本轮命中的会再次标记
        nodes.save(n);
      }
    }
    List<ImpactAssessment> out = new ArrayList<>();
    for (ExternalEvent e :
        events.findByTripIdAndEndTimeAfter(id, LocalDateTime.now().minusDays(1))) {
      for (ItineraryNode n : t.getItineraryNodes()) {
        if (overlap(e, n)
            && distance(e.getLatitude(), e.getLongitude(), n.getLatitude(), n.getLongitude())
                <= e.getRadiusKm()) {
          ImpactAssessment a = new ImpactAssessment();
          a.setTrip(t);
          a.setEvent(e);
          a.setAffectedNode(n);
          a.setImpactLevel(level(e.getSeverity()));
          a.setRiskScore(base(e.getSeverity()));
          a.setDescription("事件与节点时间及地理范围重叠");
          n.setStatus(Enums.NodeStatus.AFFECTED);
          nodes.save(n);
          out.add(assessments.save(a));
        }
      }
    }
    return out;
  }

  private boolean overlap(ExternalEvent e, ItineraryNode n) {
    return e.getStartTime().isBefore(n.getPlannedEnd())
        && e.getEndTime().isAfter(n.getPlannedStart());
  }

  private Enums.ImpactLevel level(Enums.Severity s) {
    return switch (s) {
      case LOW -> Enums.ImpactLevel.MINOR;
      case MEDIUM -> Enums.ImpactLevel.MODERATE;
      default -> Enums.ImpactLevel.SEVERE;
    };
  }

  private int base(Enums.Severity s) {
    return switch (s) {
      case LOW -> 25;
      case MEDIUM -> 50;
      case HIGH -> 75;
      case CRITICAL -> 95;
    };
  }

  public static double distance(double lat1, double lon1, double lat2, double lon2) {
    double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
