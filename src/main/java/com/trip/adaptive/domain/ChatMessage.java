package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class ChatMessage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private Conversation conversation;

  @ManyToOne(optional = false)
  private User sender;

  @Enumerated(EnumType.STRING)
  private Enums.MessageKind kind;

  @Column(length = 2000)
  private String content;

  private Long sharedGuideId;
  private String sharedGuideTitle;
  private String sharedGuideCity;
  private String sharedGuideCover;

  private LocalDateTime createdAt;

  public ChatMessage() {
    createdAt = LocalDateTime.now();
    kind = Enums.MessageKind.TEXT;
  }

  public Long getId() {
    return id;
  }

  public Conversation getConversation() {
    return conversation;
  }

  public void setConversation(Conversation v) {
    conversation = v;
  }

  public User getSender() {
    return sender;
  }

  public void setSender(User v) {
    sender = v;
  }

  public Enums.MessageKind getKind() {
    return kind;
  }

  public void setKind(Enums.MessageKind v) {
    kind = v;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }

  public Long getSharedGuideId() {
    return sharedGuideId;
  }

  public void setSharedGuideId(Long v) {
    sharedGuideId = v;
  }

  public String getSharedGuideTitle() {
    return sharedGuideTitle;
  }

  public void setSharedGuideTitle(String v) {
    sharedGuideTitle = v;
  }

  public String getSharedGuideCity() {
    return sharedGuideCity;
  }

  public void setSharedGuideCity(String v) {
    sharedGuideCity = v;
  }

  public String getSharedGuideCover() {
    return sharedGuideCover;
  }

  public void setSharedGuideCover(String v) {
    sharedGuideCover = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
