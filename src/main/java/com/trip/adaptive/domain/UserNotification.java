package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class UserNotification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private User recipient;

  @ManyToOne private Trip trip;

  private String type;
  private String title;
  private String detail;

  @Column(name = "is_read")
  private boolean read;

  private LocalDateTime createdAt;

  public UserNotification() {
    createdAt = LocalDateTime.now();
  }

  public UserNotification(User recipient, Trip trip, String type, String title, String detail) {
    this();
    this.recipient = recipient;
    this.trip = trip;
    this.type = type;
    this.title = title;
    this.detail = detail;
  }

  public Long getId() {
    return id;
  }

  public User getRecipient() {
    return recipient;
  }

  public void setRecipient(User v) {
    recipient = v;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip v) {
    trip = v;
  }

  public String getType() {
    return type;
  }

  public void setType(String v) {
    type = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String v) {
    detail = v;
  }

  public boolean isRead() {
    return read;
  }

  public void setRead(boolean v) {
    read = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
