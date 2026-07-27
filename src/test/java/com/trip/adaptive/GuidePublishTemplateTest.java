package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GuideTemplateNode;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.GuideCommentRepository;
import com.trip.adaptive.repository.TravelGuideRepository;
import com.trip.adaptive.service.GuideEngagementService;
import com.trip.adaptive.service.TravelGuideService;
import com.trip.adaptive.service.TripService;

class GuidePublishTemplateTest {
  private final TravelGuideRepository guides = mock(TravelGuideRepository.class);
  private final GuideCommentRepository comments = mock(GuideCommentRepository.class);
  private final GuideEngagementService engagement = mock(GuideEngagementService.class);
  private final TripService trips = mock(TripService.class);
  private final TravelGuideService service =
      new TravelGuideService(guides, comments, engagement, trips);

  private ItineraryNode node(
      Trip trip,
      String place,
      Enums.NodeType type,
      LocalDateTime start,
      BigDecimal cost,
      int order) {
    ItineraryNode node = new ItineraryNode();
    node.setTrip(trip);
    node.setName(place);
    node.setPlaceName(place);
    node.setLatitude(28.68);
    node.setLongitude(115.85);
    node.setNodeType(type);
    node.setPlannedStart(start);
    node.setPlannedEnd(start.plusHours(2));
    node.setCost(cost);
    node.setSequenceOrder(order);
    trip.getItineraryNodes().add(node);
    return node;
  }

  private Trip completedTrip() {
    Trip trip = new Trip();
    ReflectionTestUtils.setField(trip, "id", 4L);
    trip.setTitle("南昌 2日 行程");
    trip.setStatus(Enums.TripStatus.COMPLETED);
    trip.setStartDate(LocalDate.of(2026, 5, 1));
    trip.setEndDate(LocalDate.of(2026, 5, 2));
    node(
        trip,
        "滕王阁",
        Enums.NodeType.ATTRACTION,
        LocalDateTime.of(2026, 5, 1, 9, 30),
        BigDecimal.valueOf(50),
        1);
    node(trip, "江西小炒", Enums.NodeType.MEAL, LocalDateTime.of(2026, 5, 2, 12, 0), null, 2);
    return trip;
  }

  @Test
  void publishCopiesTheItineraryAsTemplateAndPricesIt() {
    User author = new User("宋研", "sy@example.com", "pw", "");
    ReflectionTestUtils.setField(author, "id", 1L);
    Trip trip = completedTrip();
    when(trips.requireMember(4L, author)).thenReturn(trip);
    when(guides.save(any(TravelGuide.class))).thenAnswer(i -> i.getArgument(0));

    TravelGuide guide =
        service.publishFromTrip(author, 4L, "两天玩下来的真实路线", null, null, null, List.of());

    List<GuideTemplateNode> template = guide.getTemplateNodes();
    assertEquals(2, template.size());
    assertEquals("滕王阁", template.get(0).getPlaceName());
    assertEquals(1, template.get(0).getDayIndex());
    assertEquals("09:30", template.get(0).getStartTime());
    assertEquals(2, template.get(1).getDayIndex());
    // 未登记花费的节点按类型给出常见开销，攻略不会出现 ¥0。
    assertEquals(BigDecimal.valueOf(60), template.get(1).getCost());
    assertTrue(guide.getPrice().signum() > 0);
    assertEquals("南昌", guide.getCity());
  }
}
