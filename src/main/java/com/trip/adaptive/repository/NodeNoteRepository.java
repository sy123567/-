package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.NodeNote;

public interface NodeNoteRepository extends JpaRepository<NodeNote, Long> {
  List<NodeNote> findByNodeIdOrderByCreatedAtAsc(Long nodeId);
}
