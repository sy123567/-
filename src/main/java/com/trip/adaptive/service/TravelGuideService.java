package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.TravelGuideRepository;

@Service
public class TravelGuideService {
  private final TravelGuideRepository repo;

  public TravelGuideService(TravelGuideRepository r) {
    repo = r;
  }

  public List<TravelGuide> list() {
    return repo.findAllByOrderByCreatedAtDesc();
  }

  public TravelGuide get(Long id) {
    return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("攻略不存在"));
  }

  @Transactional
  public TravelGuide create(User author, TravelGuide payload) {
    payload.setAuthor(author);
    if (payload.getRating() <= 0) payload.setRating(5.0);
    return repo.save(payload);
  }
}
