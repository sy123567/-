package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.domain.UserNotification;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.UserNotificationRepository;

@Service
public class UserNotificationService {
  private final UserNotificationRepository repo;

  public UserNotificationService(UserNotificationRepository r) {
    repo = r;
  }

  public List<UserNotification> list(User user) {
    return repo.findByRecipientIdOrderByCreatedAtDesc(user.getId());
  }

  public long unread(User user) {
    return repo.countByRecipientIdAndReadFalse(user.getId());
  }

  @Transactional
  public UserNotification markRead(User user, Long id) {
    UserNotification n =
        repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("通知不存在"));
    if (!n.getRecipient().getId().equals(user.getId())) {
      throw new ResourceNotFoundException("通知不存在");
    }
    n.setRead(true);
    return repo.save(n);
  }

  @Transactional
  public void markAllRead(User user) {
    List<UserNotification> all = repo.findByRecipientIdOrderByCreatedAtDesc(user.getId());
    for (UserNotification n : all) {
      n.setRead(true);
    }
    repo.saveAll(all);
  }

  /** 给行程所属小组的每位成员各写一条通知。 */
  @Transactional
  public void notifyTripMembers(Trip trip, String type, String title, String detail) {
    if (trip == null || trip.getGroup() == null) return;
    for (GroupMember member : trip.getGroup().getMembers()) {
      repo.save(new UserNotification(member.getUser(), trip, type, title, detail));
    }
  }
}
