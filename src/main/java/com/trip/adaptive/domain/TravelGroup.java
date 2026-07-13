package com.trip.adaptive.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class TravelGroup {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  private String description;

  @ManyToOne(optional = false)
  private User ownerUser;

  private LocalDateTime createdAt;

  @JsonIgnore
  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<GroupMember> members = new ArrayList<>();

  @JsonIgnore
  @OneToMany(mappedBy = "group", cascade = CascadeType.ALL)
  private List<Trip> trips = new ArrayList<>();

  public TravelGroup() {
    createdAt = LocalDateTime.now();
  }

  public TravelGroup(String name, String description, User owner) {
    this();
    this.name = name;
    this.description = description;
    this.ownerUser = owner;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public User getOwnerUser() {
    return ownerUser;
  }

  public void setOwnerUser(User v) {
    ownerUser = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public List<GroupMember> getMembers() {
    return members;
  }

  public List<Trip> getTrips() {
    return trips;
  }
}
