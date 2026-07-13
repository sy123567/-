package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
  List<Trip> findByGroupId(Long groupId);
}
