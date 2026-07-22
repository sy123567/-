package com.trip.adaptive.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.domain.UserNotification;
import com.trip.adaptive.service.UserNotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final UserNotificationService s;

  public NotificationController(UserNotificationService s) {
    this.s = s;
  }

  @GetMapping
  public List<NotificationView> list(Authentication authentication) {
    return s.list(currentUser(authentication)).stream().map(NotificationController::view).toList();
  }

  @PostMapping("/{id}/read")
  public NotificationView read(@PathVariable Long id, Authentication authentication) {
    return view(s.markRead(currentUser(authentication), id));
  }

  @PostMapping("/read-all")
  public void readAll(Authentication authentication) {
    s.markAllRead(currentUser(authentication));
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long id, Authentication authentication) {
    s.delete(currentUser(authentication), id);
  }

  @DeleteMapping
  public void clear(
      @RequestParam(name = "onlyRead", defaultValue = "false") boolean onlyRead,
      Authentication authentication) {
    s.clear(currentUser(authentication), onlyRead);
  }

  private static NotificationView view(UserNotification n) {
    return new NotificationView(
        n.getId(),
        n.getType(),
        n.getTitle(),
        n.getDetail(),
        n.isRead(),
        n.getTrip() == null ? null : n.getTrip().getId(),
        n.getCreatedAt());
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }

  public record NotificationView(
      Long id,
      String type,
      String title,
      String detail,
      boolean read,
      Long tripId,
      LocalDateTime createdAt) {}
}
