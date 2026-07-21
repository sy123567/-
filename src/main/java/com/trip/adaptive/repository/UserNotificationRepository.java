package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
  List<UserNotification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

  long countByRecipientIdAndReadFalse(Long recipientId);
}
