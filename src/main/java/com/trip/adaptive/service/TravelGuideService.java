package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.GuideComment;
import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GuideCommentRepository;
import com.trip.adaptive.repository.TravelGuideRepository;

@Service
public class TravelGuideService {
  private final TravelGuideRepository repo;
  private final GuideCommentRepository comments;
  private final GuideEngagementService engagement;

  public TravelGuideService(
      TravelGuideRepository r, GuideCommentRepository c, GuideEngagementService engagement) {
    repo = r;
    comments = c;
    this.engagement = engagement;
  }

  public List<TravelGuide> list() {
    return repo.findAllByOrderByCreatedAtDesc().stream().peek(engagement::decorate).toList();
  }

  public TravelGuide get(Long id) {
    TravelGuide guide = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("攻略不存在"));
    engagement.decorate(guide);
    return guide;
  }

  public List<TravelGuide> byAuthor(Long authorId) {
    return repo.findByAuthorIdOrderByCreatedAtDesc(authorId).stream()
        .peek(engagement::decorate)
        .toList();
  }

  public List<GuideComment> listComments(Long guideId) {
    get(guideId);
    return comments.findByGuideIdOrderByCreatedAtDesc(guideId);
  }

  @Transactional
  public GuideComment addComment(Long guideId, User author, String body) {
    TravelGuide guide = get(guideId);
    GuideComment comment = comments.save(new GuideComment(guide, author, body));
    engagement.recordReview(guideId, (int) comments.countByGuideId(guideId));
    return comment;
  }

  @Transactional
  public TravelGuide create(User author, TravelGuide payload) {
    payload.setAuthor(author);
    if (payload.getRating() <= 0) payload.setRating(5.0);
    return repo.save(payload);
  }

  @Transactional
  public TravelGuide toggleSave(Long id, User user) {
    TravelGuide guide = get(id);
    boolean added = guide.getSavedBy().add(user.getId());
    if (!added) {
      guide.getSavedBy().remove(user.getId());
    }
    repo.save(guide);
    guide.setSaves(engagement.adjustSaves(guide, added ? 1 : -1));
    return guide;
  }
}
