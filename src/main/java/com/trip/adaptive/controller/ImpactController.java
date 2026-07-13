package com.trip.adaptive.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.service.ImpactMatchingService;
import com.trip.adaptive.service.RiskScoringService;

@RestController
@RequestMapping("/api/trips")
public class ImpactController {
  private final ImpactMatchingService matching;
  private final RiskScoringService risk;

  public ImpactController(ImpactMatchingService m, RiskScoringService r) {
    matching = m;
    risk = r;
  }

  @PostMapping("/{id}/assess")
  public Object assess(@PathVariable Long id) {
    return matching.assessTrip(id);
  }

  @GetMapping("/{id}/impacts")
  public Object impacts(@PathVariable Long id) {
    return matching.assessTrip(id);
  }

  @GetMapping("/{id}/risk")
  public Object risk(@PathVariable Long id) {
    return risk.scoreTrip(id);
  }
}
