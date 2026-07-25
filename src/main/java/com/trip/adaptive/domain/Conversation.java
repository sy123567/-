package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/** 会话。DIRECT 为两名用户之间的私聊（userA/userB 按用户 ID 升序存放，保证唯一）， GROUP 为某个旅行小组的群聊（关联 group）。 */
@Entity
public class Conversation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private Enums.ConversationType type;

  @ManyToOne private User userA;

  @ManyToOne private User userB;

  @ManyToOne private TravelGroup group;

  private LocalDateTime createdAt;
  private LocalDateTime lastMessageAt;

  public Conversation() {
    createdAt = LocalDateTime.now();
    lastMessageAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Enums.ConversationType getType() {
    return type;
  }

  public void setType(Enums.ConversationType v) {
    type = v;
  }

  public User getUserA() {
    return userA;
  }

  public void setUserA(User v) {
    userA = v;
  }

  public User getUserB() {
    return userB;
  }

  public void setUserB(User v) {
    userB = v;
  }

  public TravelGroup getGroup() {
    return group;
  }

  public void setGroup(TravelGroup v) {
    group = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getLastMessageAt() {
    return lastMessageAt;
  }

  public void setLastMessageAt(LocalDateTime v) {
    lastMessageAt = v;
  }
}
