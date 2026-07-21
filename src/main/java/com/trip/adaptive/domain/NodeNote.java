package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class NodeNote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private ItineraryNode node;

  @JsonIgnore
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private User author;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  public NodeNote() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public ItineraryNode getNode() {
    return node;
  }

  public void setNode(ItineraryNode v) {
    node = v;
  }

  public User getAuthor() {
    return author;
  }

  public void setAuthor(User v) {
    author = v;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String v) {
    content = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime v) {
    createdAt = v;
  }
}
