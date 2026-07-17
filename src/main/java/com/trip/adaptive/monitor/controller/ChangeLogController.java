package com.trip.adaptive.monitor.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.repository.ChangeLogRepository;

@RestController
@RequestMapping("/api/trips")
public class ChangeLogController {
  private final ChangeLogRepository repo;

  public ChangeLogController(ChangeLogRepository r) {
    repo = r;
  }

  @GetMapping("/{id}/changelogs")
  public List<?> list(@PathVariable Long id) {
    return repo.findByTripId(id);
  }
}
