package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.ai.AiClient;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.monitor.service.BaiduMapClient;
import com.trip.adaptive.monitor.service.ReplacementCandidateService;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.ReplanConstraints;
import com.trip.adaptive.monitor.service.WeatherClient;

class ReplacementCandidateServiceTest {

  private final AiClient ai = mock(AiClient.class);
  private final BaiduMapClient maps = mock(BaiduMapClient.class);
  private final WeatherClient weather = mock(WeatherClient.class);

  private ReplacementCandidateService service() {
    ReplacementCandidateService s = new ReplacementCandidateService(ai, maps, weather);
    ReflectionTestUtils.setField(s, "searchRadiusMeters", 8000);
    ReflectionTestUtils.setField(s, "maxCandidates", 6);
    return s;
  }

  private static ItineraryNode node() {
    ItineraryNode n = new ItineraryNode();
    n.setPlaceName("露天景点");
    n.setNodeType(Enums.NodeType.ATTRACTION);
    n.setLatitude(31.20);
    n.setLongitude(121.40);
    n.setCost(new BigDecimal("80"));
    n.setPlannedStart(LocalDateTime.of(2026, 8, 1, 9, 0));
    n.setPlannedEnd(LocalDateTime.of(2026, 8, 1, 12, 0));
    return n;
  }

  private static BaiduMapClient.Place place(String name, double lat, double lng) {
    return new BaiduMapClient.Place(
        name, lat, lng, null, null, null, null, null, null, null, null, 60d, null, null, null);
  }

  private static WeatherClient.WeatherSummary weather(boolean alert, boolean precip) {
    return new WeatherClient.WeatherSummary(true, "p", 20d, 28d, "phrase", alert, precip, "ok");
  }

  @Test
  void skipsCandidateWithPrecipitationAndReturnsSafeOne() {
    when(ai.enabled()).thenReturn(false);
    when(maps.enabled()).thenReturn(true);
    when(maps.searchNearby(anyString(), anyDouble(), anyDouble(), anyInt()))
        .thenReturn(List.of(place("雨中馆", 31.21, 121.41), place("室内馆", 31.22, 121.42)));
    when(weather.summary(eq(31.21), eq(121.41))).thenReturn(weather(false, true)); // 降水 -> 淘汰
    when(weather.summary(eq(31.22), eq(121.42))).thenReturn(weather(false, false)); // 安全

    Optional<Candidate> result =
        service()
            .findSafeReplacement(
                node(),
                node().getPlannedStart(),
                node().getPlannedEnd(),
                ReplanConstraints.none(),
                List.of(),
                true);

    assertTrue(result.isPresent());
    assertEquals("室内馆", result.get().name());
  }

  @Test
  void lowFitnessRejectsFarCandidateAndKeepsNearOne() {
    when(ai.enabled()).thenReturn(false);
    when(maps.enabled()).thenReturn(true);
    when(maps.searchNearby(anyString(), anyDouble(), anyDouble(), anyInt()))
        .thenReturn(List.of(place("远处馆", 31.25, 121.45), place("近处馆", 31.205, 121.405)));
    when(weather.summary(anyDouble(), anyDouble())).thenReturn(weather(false, false));

    ReplanConstraints low =
        new ReplanConstraints(
            null, List.of(), Enums.FitnessLevel.LOW, List.of(), List.of()); // LOW -> 2km 可达

    Optional<Candidate> result =
        service()
            .findSafeReplacement(
                node(), node().getPlannedStart(), node().getPlannedEnd(), low, List.of(), false);

    assertTrue(result.isPresent());
    assertEquals("近处馆", result.get().name());
  }

  @Test
  void vegetarianDietExcludesMeatMealCandidate() {
    ItineraryNode meal = node();
    meal.setNodeType(Enums.NodeType.MEAL);
    when(ai.enabled()).thenReturn(false);
    when(maps.enabled()).thenReturn(true);
    when(maps.searchNearby(anyString(), anyDouble(), anyDouble(), anyInt()))
        .thenReturn(List.of(place("城中烤肉店", 31.205, 121.405), place("素食餐厅", 31.206, 121.406)));
    when(weather.summary(anyDouble(), anyDouble())).thenReturn(weather(false, false));

    ReplanConstraints veg = new ReplanConstraints(null, List.of(), null, List.of("素食"), List.of());

    Optional<Candidate> result =
        service()
            .findSafeReplacement(
                meal, meal.getPlannedStart(), meal.getPlannedEnd(), veg, List.of(), false);

    assertTrue(result.isPresent());
    assertEquals("素食餐厅", result.get().name());
  }

  @Test
  void rejectsCandidateInsideHighSeverityEventArea() {
    when(ai.enabled()).thenReturn(false);
    when(maps.enabled()).thenReturn(true);
    when(maps.searchNearby(anyString(), anyDouble(), anyDouble(), anyInt()))
        .thenReturn(List.of(place("受灾馆", 31.22, 121.42)));
    when(weather.summary(anyDouble(), anyDouble())).thenReturn(weather(false, false));

    ExternalEvent event = new ExternalEvent();
    event.setEventType(Enums.EventType.TRAFFIC_CONTROL);
    event.setSeverity(Enums.Severity.CRITICAL);
    event.setLatitude(31.22);
    event.setLongitude(121.42);
    event.setRadiusKm(5d);
    event.setStartTime(LocalDateTime.of(2026, 8, 1, 8, 0));
    event.setEndTime(LocalDateTime.of(2026, 8, 1, 20, 0));

    Optional<Candidate> result =
        service()
            .findSafeReplacement(
                node(),
                node().getPlannedStart(),
                node().getPlannedEnd(),
                ReplanConstraints.none(),
                List.of(event),
                false);

    assertTrue(result.isEmpty());
  }
}
