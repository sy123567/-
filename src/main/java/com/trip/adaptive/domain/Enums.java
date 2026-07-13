package com.trip.adaptive.domain;

public final class Enums {
  private Enums() {}

  public enum MemberRole {
    OWNER,
    MEMBER
  }

  public enum FitnessLevel {
    LOW,
    MEDIUM,
    HIGH
  }

  public enum TripStatus {
    DRAFT,
    PLANNED,
    ONGOING,
    COMPLETED,
    CANCELLED
  }

  public enum NodeType {
    ATTRACTION,
    MEAL,
    LODGING,
    TRANSPORT,
    OTHER
  }

  public enum NodeStatus {
    PLANNED,
    CONFIRMED,
    AFFECTED,
    CANCELLED,
    REPLACED
  }

  public enum TransportMode {
    WALK,
    DRIVE,
    TRANSIT,
    TRAIN,
    FLIGHT
  }

  public enum EventType {
    WEATHER,
    ROAD_WORK,
    TRAFFIC_CONTROL,
    ATTRACTION_CLOSURE,
    LARGE_EVENT
  }

  public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
  }

  public enum ImpactLevel {
    NONE,
    MINOR,
    MODERATE,
    SEVERE
  }

  public enum ReplanStrategy {
    MIN_EXTRA_COST,
    MIN_DELAY,
    MIN_CHANGE
  }

  public enum PlanStatus {
    PROPOSED,
    VOTING,
    ACCEPTED,
    REJECTED
  }

  public enum ChangeType {
    RESCHEDULE,
    REPLACE,
    REMOVE,
    ADD
  }

  public enum VoteChoice {
    APPROVE,
    REJECT,
    ABSTAIN
  }
}
