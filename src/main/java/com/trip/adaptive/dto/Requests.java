package com.trip.adaptive.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class Requests {
  private Requests() {}

  public record UserRequest(
      @NotBlank String name,
      @Email @NotBlank String email,
      @NotBlank String password,
      String phone) {}

  public record ProfileUpdateRequest(
      @NotBlank String name, @Email @NotBlank String email, String phone) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 6) String newPassword) {}

  public record GroupRequest(@NotBlank String name, String description, Long ownerUserId) {}

  public record JoinGroupRequest(@NotBlank String roomCode) {}

  public record MemberRequest(@NotNull Long userId) {}

  public record ConstraintRequest(
      @NotNull LocalDate availableFrom,
      @NotNull LocalDate availableTo,
      @Positive BigDecimal maxBudget,
      List<String> mustVisitPlaces,
      com.trip.adaptive.domain.Enums.FitnessLevel fitnessLevel,
      List<String> dietaryNeeds,
      List<String> accessibilityNeeds) {}

  public record VoteRequest(
      @NotNull Long memberId,
      @NotNull com.trip.adaptive.domain.Enums.VoteChoice choice,
      String comment) {}

  // M04 好友关系
  public record FriendRequest(@NotNull Long addresseeId) {}
}
