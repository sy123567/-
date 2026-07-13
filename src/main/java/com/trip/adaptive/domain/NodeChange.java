package com.trip.adaptive.domain;

import java.math.BigDecimal;
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
public class NodeChange {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private AlternativePlan plan;

  @ManyToOne private ItineraryNode originalNode;

  @Enumerated(EnumType.STRING)
  private Enums.ChangeType changeType;

  private String newPlaceName;
  private LocalDateTime newStart, newEnd;
  private BigDecimal newCost;
  private String note;

  public NodeChange() {}

  public Long getId() {
    return id;
  }

  public AlternativePlan getPlan() {
    return plan;
  }

  public void setPlan(AlternativePlan v) {
    plan = v;
  }

  public ItineraryNode getOriginalNode() {
    return originalNode;
  }

  public void setOriginalNode(ItineraryNode v) {
    originalNode = v;
  }

  public Enums.ChangeType getChangeType() {
    return changeType;
  }

  public void setChangeType(Enums.ChangeType v) {
    changeType = v;
  }

  public String getNewPlaceName() {
    return newPlaceName;
  }

  public void setNewPlaceName(String v) {
    newPlaceName = v;
  }

  public LocalDateTime getNewStart() {
    return newStart;
  }

  public void setNewStart(LocalDateTime v) {
    newStart = v;
  }

  public LocalDateTime getNewEnd() {
    return newEnd;
  }

  public void setNewEnd(LocalDateTime v) {
    newEnd = v;
  }

  public BigDecimal getNewCost() {
    return newCost;
  }

  public void setNewCost(BigDecimal v) {
    newCost = v;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String v) {
    note = v;
  }
}
