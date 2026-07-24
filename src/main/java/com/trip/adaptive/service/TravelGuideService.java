package com.trip.adaptive.service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GuideComment;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GuideCommentRepository;
import com.trip.adaptive.repository.TravelGuideRepository;

@Service
public class TravelGuideService {
  private final TravelGuideRepository repo;
  private final GuideCommentRepository comments;
  private final GuideEngagementService engagement;
  private final TripService trips;

  public TravelGuideService(
      TravelGuideRepository r,
      GuideCommentRepository c,
      GuideEngagementService engagement,
      TripService trips) {
    repo = r;
    comments = c;
    this.engagement = engagement;
    this.trips = trips;
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
    // 不预置虚假评分：评分、收藏、评论均由真实互动累积。
    if (payload.getRating() < 0) payload.setRating(0);
    return repo.save(payload);
  }

  /** 由作者"已完成"的行程发布攻略：行程标题/天数/预算等元数据直接取自行程，作者只需补充备注说明与主题/标签。 服务端强校验行程归属与状态，避免用任意 tripId 或未完成行程发布。 */
  @Transactional
  public TravelGuide publishFromTrip(
      User author,
      Long tripId,
      String note,
      String city,
      String theme,
      String cover,
      List<String> tags) {
    if (tripId == null) throw new BusinessException("请选择要发布的已完成行程");
    Trip trip = trips.requireMember(tripId, author);
    if (trip.getStatus() != Enums.TripStatus.COMPLETED) {
      throw new BusinessException("只能把已完成的行程发布为攻略");
    }
    if (note == null || note.isBlank()) throw new BusinessException("请填写攻略备注说明");
    TravelGuide guide = new TravelGuide();
    guide.setAuthor(author);
    guide.setTripId(tripId);
    guide.setTitle(trip.getTitle());
    guide.setCity(city != null && !city.isBlank() ? city.trim() : deriveCity(trip));
    guide.setDays(deriveDays(trip));
    guide.setPrice(trip.getTotalBudget() != null ? trip.getTotalBudget() : BigDecimal.ZERO);
    guide.setTheme(theme != null && !theme.isBlank() ? theme.trim() : "城市漫游");
    guide.setDescription(note.trim());
    guide.setCover(cover != null && !cover.isBlank() ? cover.trim() : null);
    guide.setTags(tags != null ? tags : new ArrayList<>());
    // 新发布攻略无虚假评分，收藏/评论从 0 起步，由真实互动累积。
    guide.setRating(0);
    return repo.save(guide);
  }

  private static int deriveDays(Trip trip) {
    if (trip.getStartDate() != null && trip.getEndDate() != null) {
      long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
      if (days > 0) return (int) days;
    }
    return Math.max(1, trip.getItineraryNodes().size());
  }

  private static String deriveCity(Trip trip) {
    for (ItineraryNode node : trip.getItineraryNodes()) {
      if (node.getPlaceName() != null && !node.getPlaceName().isBlank()) {
        return node.getPlaceName().trim();
      }
    }
    return trip.getTitle();
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
