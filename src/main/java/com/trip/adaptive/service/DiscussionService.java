package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.DiscussionPost;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.DiscussionPostRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class DiscussionService {
  private final DiscussionPostRepository repo;
  private final TripRepository trips;

  public DiscussionService(DiscussionPostRepository r, TripRepository t) {
    repo = r;
    trips = t;
  }

  public List<DiscussionPost> list(Long tripId) {
    return repo.findByTripIdOrderByCreatedAtDesc(tripId);
  }

  @Transactional
  public DiscussionPost create(Long tripId, User author, String body) {
    Trip trip = trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    return repo.save(new DiscussionPost(trip, author, body.trim()));
  }

  @Transactional
  public DiscussionPost toggleLike(Long postId, User user) {
    DiscussionPost post =
        repo.findById(postId).orElseThrow(() -> new ResourceNotFoundException("讨论不存在"));
    if (!post.getLikedBy().add(user.getId())) {
      post.getLikedBy().remove(user.getId());
    }
    return repo.save(post);
  }
}
