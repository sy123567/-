package com.trip.adaptive.monitor.service;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.service.UserNotificationService;

@Service
public class NotificationService {
  private final SimpMessagingTemplate messaging;
  private final UserNotificationService notifications;
  private final TripRepository trips;

  public NotificationService(SimpMessagingTemplate m, UserNotificationService n, TripRepository t) {
    messaging = m;
    notifications = n;
    trips = t;
  }

  public void trip(Long id, String type, Object data) {
    messaging.convertAndSend("/topic/trips/" + id, Map.of("type", type, "data", data));
    persist(id, type);
  }

  private void persist(Long tripId, String type) {
    String title = titleFor(type);
    if (title == null) return;
    trips
        .findById(tripId)
        .ifPresent(trip -> notifications.notifyTripMembers(trip, type, title, detailFor(type)));
  }

  private static String titleFor(String type) {
    return switch (type) {
      case "new-plans" -> "有新的替代方案待比较";
      case "plan-accepted" -> "行程变更已应用";
      case "plan-rejected" -> "一个替代方案被否决";
      default -> null;
    };
  }

  private static String detailFor(String type) {
    return switch (type) {
      case "new-plans" -> "系统已根据最新影响生成替代方案，前往方案页查看。";
      case "plan-accepted" -> "被采纳的方案已应用到行程节点与路线。";
      case "plan-rejected" -> "群组可另选方案重新发起投票。";
      default -> "";
    };
  }
}
