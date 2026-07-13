package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ItineraryNode;

public interface ItineraryNodeRepository extends JpaRepository<ItineraryNode, Long> {
  List<ItineraryNode> findByTripIdOrderBySequenceOrderAsc(Long tripId);
}
