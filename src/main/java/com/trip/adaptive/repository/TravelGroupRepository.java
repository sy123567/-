package com.trip.adaptive.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.TravelGroup;

public interface TravelGroupRepository extends JpaRepository<TravelGroup, Long> {}
