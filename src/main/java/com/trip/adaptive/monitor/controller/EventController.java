package com.trip.adaptive.monitor.controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.monitor.service.EventIngestionService;
import com.trip.adaptive.service.TripService;

@RestController
@RequestMapping("/api")
public class EventController {
  private final EventIngestionService s;
  private final TripService trips;

  public EventController(EventIngestionService s, TripService trips) {
    this.s = s;
    this.trips = trips;
  }

  @PostMapping("/events")
  public ExternalEvent ingest(@RequestBody ExternalEvent e, Authentication authentication) {
    if (e.getTripId() == null) {
      throw new IllegalArgumentException("事件必须关联行程");
    }
    trips.requireMember(e.getTripId(), currentUser(authentication));
    return s.ingest(e);
  }

  @GetMapping("/events")
  public List<ExternalEvent> all(Authentication authentication) {
    return mine(authentication);
  }

  @GetMapping("/events/active")
  public List<ExternalEvent> active(Authentication authentication) {
    return mine(authentication);
  }

  @GetMapping("/events/mine")
  public List<ExternalEvent> mine(Authentication authentication) {
    Set<Long> tripIds =
        trips.all(currentUser(authentication)).stream()
            .map(trip -> trip.getId())
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    return s.activeForTrips(tripIds);
  }

  @PostMapping("/trips/{id}/events/mock")
  public List<ExternalEvent> mock(@PathVariable Long id, Authentication authentication) {
    trips.requireMember(id, currentUser(authentication));
    return s.fetchAndIngestForTrip(id);
  }

  @PostMapping("/trips/{id}/events/weather")
  public List<ExternalEvent> weather(@PathVariable Long id, Authentication authentication) {
    trips.requireMember(id, currentUser(authentication));
    return s.ingestWeatherForTrip(id);
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }
}
