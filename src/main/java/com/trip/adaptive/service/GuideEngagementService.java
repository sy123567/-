package com.trip.adaptive.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.repository.GuideCommentRepository;

@Service
public class GuideEngagementService {
  private static final String REVIEWS = "guide:reviews:";
  private static final String SAVES = "guide:saves:";

  private final StringRedisTemplate redis;
  private final GuideCommentRepository comments;
  private final GuideStatsPersistenceService persistence;

  public GuideEngagementService(
      StringRedisTemplate redis,
      GuideCommentRepository comments,
      GuideStatsPersistenceService persistence) {
    this.redis = redis;
    this.comments = comments;
    this.persistence = persistence;
  }

  public void decorate(TravelGuide guide) {
    guide.setReviews(reviewCount(guide));
    guide.setSaves(saveCount(guide));
  }

  public int reviewCount(TravelGuide guide) {
    return read(REVIEWS + guide.getId(), (int) comments.countByGuideId(guide.getId()));
  }

  public int saveCount(TravelGuide guide) {
    return read(SAVES + guide.getId(), guide.getSavedBy().size());
  }

  public void recordReview(Long guideId, int count) {
    write(REVIEWS + guideId, count);
    persistence.persistReviews(guideId, count);
  }

  public int adjustSaves(TravelGuide guide, int delta) {
    String key = SAVES + guide.getId();
    try {
      if (redis.opsForValue().get(key) == null) {
        redis.opsForValue().set(key, String.valueOf(guide.getSavedBy().size()));
      }
      Long value = redis.opsForValue().increment(key, delta);
      int count = Math.max(0, value == null ? guide.getSavedBy().size() : value.intValue());
      persistence.persistSaves(guide.getId(), count);
      return count;
    } catch (DataAccessException exception) {
      int count = Math.max(0, guide.getSavedBy().size());
      persistence.persistSaves(guide.getId(), count);
      return count;
    }
  }

  private int read(String key, int fallback) {
    try {
      String value = redis.opsForValue().get(key);
      if (value == null) {
        redis.opsForValue().set(key, String.valueOf(fallback));
        return fallback;
      }
      return Integer.parseInt(value);
    } catch (DataAccessException | NumberFormatException exception) {
      return fallback;
    }
  }

  private void write(String key, int value) {
    try {
      redis.opsForValue().set(key, String.valueOf(Math.max(0, value)));
    } catch (DataAccessException ignored) {
      // Database persistence remains the source of truth if Redis is unavailable.
    }
  }
}
