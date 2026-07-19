package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.TravelGroup;

public interface TravelGroupRepository extends JpaRepository<TravelGroup, Long> {
  List<TravelGroup> findDistinctByMembersUserId(Long userId);

  List<TravelGroup> findByOwnerUserId(Long userId);

  Optional<TravelGroup> findByRoomCode(String roomCode);

  boolean existsByRoomCode(String roomCode);
}
