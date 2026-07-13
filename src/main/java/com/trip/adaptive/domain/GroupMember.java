package com.trip.adaptive.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class GroupMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private TravelGroup group;

  @ManyToOne(optional = false)
  private User user;

  @Enumerated(EnumType.STRING)
  private Enums.MemberRole role;

  @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
  private MemberConstraint constraint;

  public GroupMember() {}

  public GroupMember(TravelGroup g, User u, Enums.MemberRole r) {
    group = g;
    user = u;
    role = r;
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

  public User getUser() {
    return user;
  }

  public void setUser(User v) {
    user = v;
  }

  public Enums.MemberRole getRole() {
    return role;
  }

  public void setRole(Enums.MemberRole v) {
    role = v;
  }

  public MemberConstraint getConstraint() {
    return constraint;
  }

  public void setConstraint(MemberConstraint v) {
    constraint = v;
  }
}
