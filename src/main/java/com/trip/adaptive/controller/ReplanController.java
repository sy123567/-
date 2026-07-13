package com.trip.adaptive.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.service.ReplanningService;

@RestController
@RequestMapping("/api")
public class ReplanController {
  private final ReplanningService s;

  public ReplanController(ReplanningService s) {
    this.s = s;
  }

  @PostMapping("/trips/{id}/replan")
  public List<?> generate(@PathVariable Long id) {
    return s.generate(id);
  }

  @GetMapping("/trips/{id}/plans")
  public List<?> list(@PathVariable Long id) {
    return s.list(id);
  }

  @GetMapping("/plans/{id}")
  public Object get(@PathVariable Long id) {
    return s.get(id);
  }
}
