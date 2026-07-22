package com.trip.adaptive.monitor.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.monitor.service.ImpactMatchingService;
import com.trip.adaptive.monitor.service.RiskScoringService;
import com.trip.adaptive.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class ImpactController {
  private final ImpactMatchingService matching;
  private final RiskScoringService risk;
  private final TripService trips;

  public ImpactController(ImpactMatchingService m, RiskScoringService r, TripService t) {
    matching = m;
    risk = r;
    trips = t;
  }

  @PostMapping("/{id}/assess")
  public Object assess(@PathVariable Long id, Authentication authentication) {
    trips.requireMember(id, currentUser(authentication));
    return matching.assessTrip(id);
  }

  @GetMapping("/{id}/impacts")
  public Object impacts(@PathVariable Long id, Authentication authentication) {
    trips.requireMember(id, currentUser(authentication));
    return matching.assessTrip(id);
  }

  @GetMapping("/{id}/risk")
  public Object risk(@PathVariable Long id, Authentication authentication) {
    trips.requireMember(id, currentUser(authentication));
    return risk.scoreTrip(id);
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }
}
