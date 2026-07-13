package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class AlternativePlan {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Trip trip;

  private String title;

  @Enumerated(EnumType.STRING)
  private Enums.ReplanStrategy strategy;

  private BigDecimal extraCost;
  private int extraDelayMinutes, changedNodeCount;

  @Column(length = 2000)
  private String summary;

  @Enumerated(EnumType.STRING)
  private Enums.PlanStatus status;

  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<NodeChange> proposedNodeChanges = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<PlanVote> votes = new ArrayList<>();

  public AlternativePlan() {
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public Enums.ReplanStrategy getStrategy() {
    return strategy;
  }

  public void setStrategy(Enums.ReplanStrategy v) {
    strategy = v;
  }

  public BigDecimal getExtraCost() {
    return extraCost;
  }

  public void setExtraCost(BigDecimal v) {
    extraCost = v;
  }

  public int getExtraDelayMinutes() {
    return extraDelayMinutes;
  }

  public void setExtraDelayMinutes(int v) {
    extraDelayMinutes = v;
  }

  public int getChangedNodeCount() {
    return changedNodeCount;
  }

  public void setChangedNodeCount(int v) {
    changedNodeCount = v;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String v) {
    summary = v;
  }

  public Enums.PlanStatus getStatus() {
    return status;
  }

  public void setStatus(Enums.PlanStatus v) {
    status = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<NodeChange> getProposedNodeChanges() {
    return proposedNodeChanges;
  }

  public List<PlanVote> getVotes() {
    return votes;
  }
}
