package com.trip.adaptive.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ForbiddenException;
import com.trip.adaptive.service.AdminStatsService;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final AdminStatsService s;

  public AdminController(AdminStatsService s) {
    this.s = s;
  }

  @GetMapping("/stats")
  public AdminStatsService.Stats stats(Authentication authentication) {
    User current = (User) authentication.getPrincipal();
    if (!current.isAdmin()) {
      throw new ForbiddenException("仅管理员可以查看数据看板");
    }
    return s.stats();
  }
}
