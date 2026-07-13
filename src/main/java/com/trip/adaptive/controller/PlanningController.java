package com.trip.adaptive.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.service.PlanningService;

@RestController
@RequestMapping("/api/groups")
public class PlanningController {
  private final PlanningService s;

  public PlanningController(PlanningService s) {
    this.s = s;
  }

  @PostMapping("/{id}/plan")
  public Map<String, Object> plan(@PathVariable Long id) {
    var r = s.generateInitialPlan(id);
    return Map.of("trip", r.trip(), "explanation", r.explanation());
  }
}
