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
  private Double newLatitude, newLongitude;
  private LocalDateTime newStart, newEnd;
  private BigDecimal newCost;
  private String note;

  // 变更前快照：方案被采纳应用到节点时记录，用于「回退」恢复到变更前的行程状态。
  private boolean applied;
  private String prevPlaceName;
  private Double prevLatitude, prevLongitude;
  private LocalDateTime prevStart, prevEnd;
  private BigDecimal prevCost;

  @Enumerated(EnumType.STRING)
  private Enums.NodeStatus prevStatus;

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

  public Double getNewLatitude() {
    return newLatitude;
  }

  public void setNewLatitude(Double v) {
    newLatitude = v;
  }

  public Double getNewLongitude() {
    return newLongitude;
  }

  public void setNewLongitude(Double v) {
    newLongitude = v;
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

  public boolean isApplied() {
    return applied;
  }

  public void setApplied(boolean v) {
    applied = v;
  }

  public String getPrevPlaceName() {
    return prevPlaceName;
  }

  public void setPrevPlaceName(String v) {
    prevPlaceName = v;
  }

  public Double getPrevLatitude() {
    return prevLatitude;
  }

  public void setPrevLatitude(Double v) {
    prevLatitude = v;
  }

  public Double getPrevLongitude() {
    return prevLongitude;
  }

  public void setPrevLongitude(Double v) {
    prevLongitude = v;
  }

  public LocalDateTime getPrevStart() {
    return prevStart;
  }

  public void setPrevStart(LocalDateTime v) {
    prevStart = v;
  }

  public LocalDateTime getPrevEnd() {
    return prevEnd;
  }

  public void setPrevEnd(LocalDateTime v) {
    prevEnd = v;
  }

  public BigDecimal getPrevCost() {
    return prevCost;
  }

  public void setPrevCost(BigDecimal v) {
    prevCost = v;
  }

  public Enums.NodeStatus getPrevStatus() {
    return prevStatus;
  }

  public void setPrevStatus(Enums.NodeStatus v) {
    prevStatus = v;
  }
}
