package com.trip.adaptive.domain;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class Route {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private Trip trip;

  @ManyToOne private ItineraryNode fromNode, toNode;

  @Enumerated(EnumType.STRING)
  private Enums.TransportMode transportMode;

  private Double distanceKm;
  private int durationMinutes;
  private BigDecimal cost;

  public Route() {}

  public Long getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip v) {
    trip = v;
  }

  public ItineraryNode getFromNode() {
    return fromNode;
  }

  public void setFromNode(ItineraryNode v) {
    fromNode = v;
  }

  public ItineraryNode getToNode() {
    return toNode;
  }

  public void setToNode(ItineraryNode v) {
    toNode = v;
  }

  public Enums.TransportMode getTransportMode() {
    return transportMode;
  }

  public void setTransportMode(Enums.TransportMode v) {
    transportMode = v;
  }

  public Double getDistanceKm() {
    return distanceKm;
  }

  public void setDistanceKm(Double v) {
    distanceKm = v;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(int v) {
    durationMinutes = v;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal v) {
    cost = v;
  }
}
