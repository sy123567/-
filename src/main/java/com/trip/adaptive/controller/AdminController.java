package com.trip.adaptive.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.service.AdminStatsService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final AdminStatsService s;

  public AdminController(AdminStatsService s) {
    this.s = s;
  }

  @GetMapping("/stats")
  public AdminStatsService.Stats stats() {
    return s.stats();
  }
}
