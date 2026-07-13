package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.PlanVote;

public interface PlanVoteRepository extends JpaRepository<PlanVote, Long> {
  List<PlanVote> findByPlanId(Long planId);

  Optional<PlanVote> findByPlanIdAndMemberId(Long planId, Long memberId);
}
