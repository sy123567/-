package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "member_id"}))
public class PlanVote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private AlternativePlan plan;

  @ManyToOne(optional = false)
  private GroupMember member;

  @Enumerated(EnumType.STRING)
  private Enums.VoteChoice choice;

  private String comment;
  private LocalDateTime votedAt;

  public PlanVote() {
    votedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public AlternativePlan getPlan() {
    return plan;
  }

  public void setPlan(AlternativePlan v) {
    plan = v;
  }

  public GroupMember getMember() {
    return member;
  }

  public void setMember(GroupMember v) {
    member = v;
  }

  public Enums.VoteChoice getChoice() {
    return choice;
  }

  public void setChoice(Enums.VoteChoice v) {
    choice = v;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String v) {
    comment = v;
  }

  public LocalDateTime getVotedAt() {
    return votedAt;
  }
}
