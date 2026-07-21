package com.trip.adaptive.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class DiscussionPost {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Trip trip;

  @ManyToOne(optional = false)
  private User author;

  private String body;
  private LocalDateTime createdAt;

  @ElementCollection private Set<Long> likedBy = new HashSet<>();

  public DiscussionPost() {
    createdAt = LocalDateTime.now();
  }

  public DiscussionPost(Trip trip, User author, String body) {
    this();
    this.trip = trip;
    this.author = author;
    this.body = body;
  }

  public Long getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip v) {
    trip = v;
  }

  public User getAuthor() {
    return author;
  }

  public void setAuthor(User v) {
    author = v;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String v) {
    body = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Set<Long> getLikedBy() {
    return likedBy;
  }

  public void setLikedBy(Set<Long> v) {
    likedBy = v;
  }
}
