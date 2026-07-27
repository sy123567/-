package com.trip.adaptive.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GuideComment;
import com.trip.adaptive.domain.GuideTemplateNode;
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
  /** 行程标题开头的 2~4 个汉字通常就是目的地城市。 */
  private static final Pattern CITY_IN_TITLE = Pattern.compile("^([\\u4e00-\\u9fa5]{2,4})");

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
    guide.setPrice(derivePrice(trip));
    guide.setTheme(theme != null && !theme.isBlank() ? theme.trim() : "城市漫游");
    guide.setDescription(note.trim());
    guide.setCover(cover != null && !cover.isBlank() ? cover.trim() : null);
    guide.setTags(tags != null ? tags : new ArrayList<>());
    // 新发布攻略无虚假评分，收藏/评论从 0 起步，由真实互动累积。
    guide.setRating(0);
    copyTemplate(trip, guide);
    return repo.save(guide);
  }

  /** 把原行程的节点抄成攻略的路线模板，时间转成「第几天 + 时刻」，纳用时再按读者的出发日期展开。 */
  private static void copyTemplate(Trip trip, TravelGuide guide) {
    List<ItineraryNode> nodes =
        trip.getItineraryNodes().stream()
            .sorted(
                Comparator.comparing(
                        ItineraryNode::getPlannedStart,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(ItineraryNode::getSequenceOrder))
            .toList();
    LocalDate firstDay = firstDay(trip, nodes);
    int sequence = 1;
    for (ItineraryNode node : nodes) {
      GuideTemplateNode template = new GuideTemplateNode();
      template.setGuide(guide);
      template.setSequenceOrder(sequence++);
      template.setDayIndex(dayIndex(firstDay, node));
      template.setName(node.getName());
      template.setPlaceName(node.getPlaceName() != null ? node.getPlaceName() : node.getName());
      template.setLatitude(node.getLatitude());
      template.setLongitude(node.getLongitude());
      template.setNodeType(node.getNodeType() != null ? node.getNodeType() : Enums.NodeType.OTHER);
      template.setStartTime(timeOf(node.getPlannedStart()));
      template.setEndTime(timeOf(node.getPlannedEnd()));
      template.setCost(node.getCost() != null ? node.getCost() : nodeEstimate(node.getNodeType()));
      guide.getTemplateNodes().add(template);
    }
  }

  private static LocalDate firstDay(Trip trip, List<ItineraryNode> nodes) {
    if (trip.getStartDate() != null) return trip.getStartDate();
    for (ItineraryNode node : nodes) {
      if (node.getPlannedStart() != null) return node.getPlannedStart().toLocalDate();
    }
    return LocalDate.now();
  }

  private static int dayIndex(LocalDate firstDay, ItineraryNode node) {
    if (node.getPlannedStart() == null) return 1;
    long offset = ChronoUnit.DAYS.between(firstDay, node.getPlannedStart().toLocalDate());
    return (int) Math.max(1, offset + 1);
  }

  private static String timeOf(LocalDateTime value) {
    return value == null ? null : value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  /** 人均预算：优先用行程节点上的真实花费，行程只填了总预算时按小组人数摊到人头。 */
  private static BigDecimal derivePrice(Trip trip) {
    BigDecimal nodeSum = BigDecimal.ZERO;
    for (ItineraryNode node : trip.getItineraryNodes()) {
      BigDecimal cost = node.getCost() != null ? node.getCost() : nodeEstimate(node.getNodeType());
      nodeSum = nodeSum.add(cost);
    }
    if (nodeSum.signum() > 0) return nodeSum;
    BigDecimal budget = trip.getTotalBudget();
    if (budget == null || budget.signum() <= 0) return BigDecimal.ZERO;
    int headcount = trip.getGroup() == null ? 1 : Math.max(1, trip.getGroup().getMembers().size());
    return budget.divide(BigDecimal.valueOf(headcount), 0, RoundingMode.HALF_UP);
  }

  /** 节点没登记花费时按类型给一个常见的人均开销，避免攻略上出现 ¥0。 */
  private static BigDecimal nodeEstimate(Enums.NodeType type) {
    if (type == null) return BigDecimal.valueOf(50);
    return switch (type) {
      case ATTRACTION -> BigDecimal.valueOf(80);
      case MEAL -> BigDecimal.valueOf(60);
      case LODGING -> BigDecimal.valueOf(300);
      case TRANSPORT -> BigDecimal.valueOf(30);
      case OTHER -> BigDecimal.valueOf(50);
    };
  }

  private static int deriveDays(Trip trip) {
    if (trip.getStartDate() != null && trip.getEndDate() != null) {
      long days = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
      if (days > 0) return (int) days;
    }
    return Math.max(1, trip.getItineraryNodes().size());
  }

  /** 作者没填城市时从行程标题里取城市名（如「南昌 2日 行程」→南昌），取不到才退回行程标题。 */
  private static String deriveCity(Trip trip) {
    String title = trip.getTitle() == null ? "" : trip.getTitle().trim();
    Matcher matcher = CITY_IN_TITLE.matcher(title);
    if (matcher.find()) return matcher.group(1);
    return title.isBlank() ? "待定城市" : title;
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
