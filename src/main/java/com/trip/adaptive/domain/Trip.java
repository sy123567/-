package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

@Entity
public class Trip {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private TravelGroup group;

  private String title;

  @Enumerated(EnumType.STRING)
  private Enums.TripStatus status;

  private LocalDate startDate, endDate;
  private BigDecimal totalBudget;
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequenceOrder ASC")
  private List<ItineraryNode> itineraryNodes = new ArrayList<>();

  @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Route> routes = new ArrayList<>();

  public Trip() {
    createdAt = LocalDateTime.now();
    status = Enums.TripStatus.DRAFT;
  }

  public Long getId() {
    return id;
  }

  public TravelGroup getGroup() {
    return group;
  }

  public void setGroup(TravelGroup v) {
    group = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public Enums.TripStatus getStatus() {
    return status;
  }

  public void setStatus(Enums.TripStatus v) {
    status = v;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate v) {
    startDate = v;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate v) {
    endDate = v;
  }

  public BigDecimal getTotalBudget() {
    return totalBudget;
  }

  public void setTotalBudget(BigDecimal v) {
    totalBudget = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<ItineraryNode> getItineraryNodes() {
    return itineraryNodes;
  }

  public List<Route> getRoutes() {
    return routes;
  }
}
