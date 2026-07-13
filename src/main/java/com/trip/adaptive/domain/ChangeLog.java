package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class ChangeLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Trip trip;

  private String description;
  private BigDecimal extraCost;
  private LocalDateTime refundDeadline;
  @ManyToOne private AlternativePlan relatedPlan;
  private LocalDateTime createdAt;

  public ChangeLog() {
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public BigDecimal getExtraCost() {
    return extraCost;
  }

  public void setExtraCost(BigDecimal v) {
    extraCost = v;
  }

  public LocalDateTime getRefundDeadline() {
    return refundDeadline;
  }

  public void setRefundDeadline(LocalDateTime v) {
    refundDeadline = v;
  }

  public AlternativePlan getRelatedPlan() {
    return relatedPlan;
  }

  public void setRelatedPlan(AlternativePlan v) {
    relatedPlan = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
