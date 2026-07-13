package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class ImpactAssessment {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private Trip trip;

  @ManyToOne(optional = false)
  private ExternalEvent event;

  @ManyToOne private ItineraryNode affectedNode;
  private int riskScore;

  @Enumerated(EnumType.STRING)
  private Enums.ImpactLevel impactLevel;

  private String description;
  private LocalDateTime createdAt;

  public ImpactAssessment() {
    createdAt = LocalDateTime.now();
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

  public ExternalEvent getEvent() {
    return event;
  }

  public void setEvent(ExternalEvent v) {
    event = v;
  }

  public ItineraryNode getAffectedNode() {
    return affectedNode;
  }

  public void setAffectedNode(ItineraryNode v) {
    affectedNode = v;
  }

  public int getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(int v) {
    riskScore = v;
  }

  public Enums.ImpactLevel getImpactLevel() {
    return impactLevel;
  }

  public void setImpactLevel(Enums.ImpactLevel v) {
    impactLevel = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
