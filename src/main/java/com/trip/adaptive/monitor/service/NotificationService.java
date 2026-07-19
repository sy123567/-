package com.trip.adaptive.monitor.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final SimpMessagingTemplate messaging;

  public NotificationService(SimpMessagingTemplate m) {
    messaging = m;
  }

  public void trip(Long id, String type, Object data) {
    messaging.convertAndSend("/topic/trips/" + id, java.util.Map.of("type", type, "data", data));
  }
}
