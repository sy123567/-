package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class MemberConstraint {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @OneToOne(optional = false)
  private GroupMember member;

  private LocalDate availableFrom;
  private LocalDate availableTo;
  private BigDecimal maxBudget;
  @ElementCollection private List<String> mustVisitPlaces = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  private Enums.FitnessLevel fitnessLevel;

  @ElementCollection private List<String> dietaryNeeds = new ArrayList<>();
  @ElementCollection private List<String> accessibilityNeeds = new ArrayList<>();

  public MemberConstraint() {}

  public MemberConstraint(GroupMember m, LocalDate f, LocalDate t, BigDecimal b) {
    member = m;
    availableFrom = f;
    availableTo = t;
    maxBudget = b;
  }

  public Long getId() {
    return id;
  }

  public GroupMember getMember() {
    return member;
  }

  public void setMember(GroupMember v) {
    member = v;
  }

  public LocalDate getAvailableFrom() {
    return availableFrom;
  }

  public void setAvailableFrom(LocalDate v) {
    availableFrom = v;
  }

  public LocalDate getAvailableTo() {
    return availableTo;
  }

  public void setAvailableTo(LocalDate v) {
    availableTo = v;
  }

  public BigDecimal getMaxBudget() {
    return maxBudget;
  }

  public void setMaxBudget(BigDecimal v) {
    maxBudget = v;
  }

  public List<String> getMustVisitPlaces() {
    return mustVisitPlaces;
  }

  public void setMustVisitPlaces(List<String> v) {
    mustVisitPlaces = v;
  }

  public Enums.FitnessLevel getFitnessLevel() {
    return fitnessLevel;
  }

  public void setFitnessLevel(Enums.FitnessLevel v) {
    fitnessLevel = v;
  }

  public List<String> getDietaryNeeds() {
    return dietaryNeeds;
  }

  public void setDietaryNeeds(List<String> v) {
    dietaryNeeds = v;
  }

  public List<String> getAccessibilityNeeds() {
    return accessibilityNeeds;
  }

  public void setAccessibilityNeeds(List<String> v) {
    accessibilityNeeds = v;
  }
}
