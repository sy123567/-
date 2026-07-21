package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.TripExpense;

public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {
  List<TripExpense> findByTripIdOrderByCreatedAtDesc(Long tripId);
}
