package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class TripExpense {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Trip trip;

  @ManyToOne(optional = false)
  private User payer;

  private String label;
  private String category;
  private BigDecimal amount;
  private LocalDateTime createdAt;

  public TripExpense() {
    createdAt = LocalDateTime.now();
  }

  public TripExpense(Trip trip, User payer, String label, String category, BigDecimal amount) {
    this();
    this.trip = trip;
    this.payer = payer;
    this.label = label;
    this.category = category;
    this.amount = amount;
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

  public User getPayer() {
    return payer;
  }

  public void setPayer(User v) {
    payer = v;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String v) {
    label = v;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String v) {
    category = v;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal v) {
    amount = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
