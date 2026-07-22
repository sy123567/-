package com.trip.adaptive.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.repository.TravelGuideRepository;

@Service
public class GuideStatsPersistenceService {
  private final TravelGuideRepository guides;

  public GuideStatsPersistenceService(TravelGuideRepository guides) {
    this.guides = guides;
  }

  @Async
  @Transactional
  public void persistReviews(Long guideId, int count) {
    guides
        .findById(guideId)
        .ifPresent(
            guide -> {
              guide.setReviews(count);
              guides.save(guide);
            });
  }

  @Async
  @Transactional
  public void persistSaves(Long guideId, int count) {
    guides
        .findById(guideId)
        .ifPresent(
            guide -> {
              guide.setSaves(count);
              guides.save(guide);
            });
  }
}
