package com.trip.adaptive.dto;

import java.util.List;

import com.trip.adaptive.domain.ImpactAssessment;

public record TripRiskResult(
    Long tripId,
    int overallScore,
    String riskLevel,
    int totalNodes,
    int affectedNodes,
    List<ImpactAssessment> assessments) {}
