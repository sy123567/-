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
public class ItineraryNode {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private Trip trip;

  private String name, placeName;
  private Double latitude, longitude;
  private Long parentId;

  @Enumerated(EnumType.STRING)
  private Enums.NodeType nodeType;

  private LocalDateTime plannedStart, plannedEnd;
  private BigDecimal cost;
  private int sequenceOrder;

  @Enumerated(EnumType.STRING)
  private Enums.NodeStatus status;

  public ItineraryNode() {
    status = Enums.NodeStatus.PLANNED;
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

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getPlaceName() {
    return placeName;
  }

  public void setPlaceName(String v) {
    placeName = v;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double v) {
    latitude = v;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double v) {
    longitude = v;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long v) {
    parentId = v;
  }

  public Enums.NodeType getNodeType() {
    return nodeType;
  }

  public void setNodeType(Enums.NodeType v) {
    nodeType = v;
  }

  public LocalDateTime getPlannedStart() {
    return plannedStart;
  }

  public void setPlannedStart(LocalDateTime v) {
    plannedStart = v;
  }

  public LocalDateTime getPlannedEnd() {
    return plannedEnd;
  }

  public void setPlannedEnd(LocalDateTime v) {
    plannedEnd = v;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal v) {
    cost = v;
  }

  public int getSequenceOrder() {
    return sequenceOrder;
  }

  public void setSequenceOrder(int v) {
    sequenceOrder = v;
  }

  public Enums.NodeStatus getStatus() {
    return status;
  }

  public void setStatus(Enums.NodeStatus v) {
    status = v;
  }
}
