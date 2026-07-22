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

  public TravelGuideService(TravelGuideRepository r, GuideCommentRepository c) {
    repo = r;
    comments = c;
  }

  public List<TravelGuide> list() {
    return repo.findAllByOrderByCreatedAtDesc();
  }

  public TravelGuide get(Long id) {
    return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("攻略不存在"));
  }

  public List<TravelGuide> byAuthor(Long authorId) {
    return repo.findByAuthorIdOrderByCreatedAtDesc(authorId);
  }

  public List<GuideComment> listComments(Long guideId) {
    get(guideId);
    return comments.findByGuideIdOrderByCreatedAtDesc(guideId);
  }

  @Transactional
  public GuideComment addComment(Long guideId, User author, String body) {
    TravelGuide guide = get(guideId);
    guide.setReviews(guide.getReviews() + 1);
    repo.save(guide);
    return comments.save(new GuideComment(guide, author, body));
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
    if (guide.getSavedBy().add(user.getId())) {
      guide.setSaves(guide.getSaves() + 1);
    } else {
      guide.getSavedBy().remove(user.getId());
      guide.setSaves(Math.max(0, guide.getSaves() - 1));
    }
    return repo.save(guide);
  }
}
