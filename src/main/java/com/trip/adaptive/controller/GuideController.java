package com.trip.adaptive.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.service.TravelGuideService;

@RestController
@RequestMapping("/api/guides")
public class GuideController {
  private final TravelGuideService s;

  public GuideController(TravelGuideService s) {
    this.s = s;
  }

  @GetMapping
  public List<TravelGuide> list() {
    return s.list();
  }

  @GetMapping("/{id}")
  public TravelGuide get(@PathVariable Long id) {
    return s.get(id);
  }

  @PostMapping("/{id}/save")
  public TravelGuide toggleSave(@PathVariable Long id, Authentication authentication) {
    return s.toggleSave(id, (User) authentication.getPrincipal());
  }

  @PostMapping
  public ResponseEntity<TravelGuide> create(
      @RequestBody TravelGuide payload, Authentication authentication) {
    User me = (User) authentication.getPrincipal();
    return ResponseEntity.status(201).body(s.create(me, payload));
  }
}
