package com.trip.adaptive.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ExternalEventRepository;

@Service
public class EventIngestionService {
  private final ExternalEventRepository events;
  private final TripService trips;

  public EventIngestionService(ExternalEventRepository e, TripService t) {
    events = e;
    trips = t;
  }

  public ExternalEvent ingest(ExternalEvent e) {
    return events.save(e);
  }

  public List<ExternalEvent> all() {
    return events.findAll();
  }

  public List<ExternalEvent> active() {
    return events.findByEndTimeAfter(LocalDateTime.now());
  }

  public List<ExternalEvent> fetchAndIngestForTrip(Long id) {
    Trip t = trips.get(id);
    ItineraryNode n =
        t.getItineraryNodes().stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("行程没有节点"));
    List<ExternalEvent> out = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      ExternalEvent e = new ExternalEvent();
      e.setEventType(i == 0 ? Enums.EventType.WEATHER : Enums.EventType.ATTRACTION_CLOSURE);
      e.setTitle(i == 0 ? "暴雨预警" : "景点临时闭馆");
      e.setDescription("模拟外部事件");
      e.setPlaceName(n.getPlaceName());
      e.setLatitude(n.getLatitude());
      e.setLongitude(n.getLongitude());
      e.setRadiusKm(10.0);
      e.setSeverity(i == 0 ? Enums.Severity.HIGH : Enums.Severity.MEDIUM);
      e.setStartTime(n.getPlannedStart().minusHours(1));
      e.setEndTime(n.getPlannedEnd().plusHours(1));
      e.setSource("mock-provider");
      out.add(events.save(e));
    }
    return out;
  }
}
