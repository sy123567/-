package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ExternalEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private Enums.EventType eventType;

  private String title, description, placeName;
  private Double latitude, longitude, radiusKm;

  @Enumerated(EnumType.STRING)
  private Enums.Severity severity;

  private LocalDateTime startTime, endTime, createdAt;
  private String source;
  private Long tripId;
  private String tripTitle;
  private Double tempMin;
  private Double tempMax;

  public ExternalEvent() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Enums.EventType getEventType() {
    return eventType;
  }

  public void setEventType(Enums.EventType v) {
    eventType = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
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

  public Double getRadiusKm() {
    return radiusKm;
  }

  public void setRadiusKm(Double v) {
    radiusKm = v;
  }

  public Enums.Severity getSeverity() {
    return severity;
  }

  public void setSeverity(Enums.Severity v) {
    severity = v;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime v) {
    startTime = v;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime v) {
    endTime = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String v) {
    source = v;
  }

  public Long getTripId() {
    return tripId;
  }

  public void setTripId(Long v) {
    tripId = v;
  }

  public String getTripTitle() {
    return tripTitle;
  }

  public void setTripTitle(String v) {
    tripTitle = v;
  }

  public Double getTempMin() {
    return tempMin;
  }

  public void setTempMin(Double v) {
    tempMin = v;
  }

  public Double getTempMax() {
    return tempMax;
  }

  public void setTempMax(Double v) {
    tempMax = v;
  }
}
