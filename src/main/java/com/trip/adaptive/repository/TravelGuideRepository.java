package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.TravelGuide;

public interface TravelGuideRepository extends JpaRepository<TravelGuide, Long> {
  List<TravelGuide> findAllByOrderByCreatedAtDesc();

  boolean existsByTitle(String title);
}
