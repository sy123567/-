package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class GuideComment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private TravelGuide guide;

  @ManyToOne(optional = false)
  private User author;

  private String body;
  private LocalDateTime createdAt;

  public GuideComment() {
    createdAt = LocalDateTime.now();
  }

  public GuideComment(TravelGuide guide, User author, String body) {
    this();
    this.guide = guide;
    this.author = author;
    this.body = body;
  }

  public Long getId() {
    return id;
  }

  public TravelGuide getGuide() {
    return guide;
  }

  public void setGuide(TravelGuide v) {
    guide = v;
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
}
