package com.trip.adaptive.monitor.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.monitor.service.EventIngestionService;

@RestController
@RequestMapping("/api")
public class EventController {
  private final EventIngestionService s;

  public EventController(EventIngestionService s) {
    this.s = s;
  }

  @PostMapping("/events")
  public ExternalEvent ingest(@RequestBody ExternalEvent e) {
    return s.ingest(e);
  }

  @GetMapping("/events")
  public List<ExternalEvent> all() {
    return s.all();
  }

  @GetMapping("/events/active")
  public List<ExternalEvent> active() {
    return s.active();
  }

  @PostMapping("/trips/{id}/events/mock")
  public List<ExternalEvent> mock(@PathVariable Long id) {
    return s.fetchAndIngestForTrip(id);
  }

  @PostMapping("/trips/{id}/events/weather")
  public List<ExternalEvent> weather(@PathVariable Long id) {
    return s.ingestWeatherForTrip(id);
  }
}
