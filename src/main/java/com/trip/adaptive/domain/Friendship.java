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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "friendships",
    uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
public class Friendship {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private User requester;

  @ManyToOne(optional = false)
  private User addressee;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Enums.FriendshipStatus status;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public Friendship() {}

  public Friendship(User requester, User addressee) {
    this.requester = requester;
    this.addressee = addressee;
    this.status = Enums.FriendshipStatus.PENDING;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public User getRequester() {
    return requester;
  }

  public void setRequester(User v) {
    requester = v;
  }

  public User getAddressee() {
    return addressee;
  }

  public void setAddressee(User v) {
    addressee = v;
  }

  public Enums.FriendshipStatus getStatus() {
    return status;
  }

  public void setStatus(Enums.FriendshipStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
