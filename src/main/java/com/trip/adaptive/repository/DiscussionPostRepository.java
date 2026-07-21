package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.DiscussionPost;

public interface DiscussionPostRepository extends JpaRepository<DiscussionPost, Long> {
  List<DiscussionPost> findByTripIdOrderByCreatedAtDesc(Long tripId);
}
