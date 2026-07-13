package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.NodeChange;

public interface NodeChangeRepository extends JpaRepository<NodeChange, Long> {
  List<NodeChange> findByPlanId(Long planId);
}
