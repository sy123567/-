package com.trip.adaptive.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.MemberConstraint;

public interface MemberConstraintRepository extends JpaRepository<MemberConstraint, Long> {
  void deleteByMemberId(Long memberId);
}
