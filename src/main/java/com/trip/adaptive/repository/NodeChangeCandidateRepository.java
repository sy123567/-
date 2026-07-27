package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.NodeChangeCandidate;

public interface NodeChangeCandidateRepository extends JpaRepository<NodeChangeCandidate, Long> {
  List<NodeChangeCandidate> findByNodeChangeIdOrderByPositionAsc(Long nodeChangeId);
}
