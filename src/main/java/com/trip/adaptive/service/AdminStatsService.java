package com.trip.adaptive.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.repository.AlternativePlanRepository;
import com.trip.adaptive.repository.ExternalEventRepository;
import com.trip.adaptive.repository.ImpactAssessmentRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class AdminStatsService {
  private final TripRepository trips;
  private final ItineraryNodeRepository nodes;
  private final ExternalEventRepository events;
  private final ImpactAssessmentRepository impacts;
  private final AlternativePlanRepository plans;

  public AdminStatsService(
      TripRepository trips,
      ItineraryNodeRepository nodes,
      ExternalEventRepository events,
      ImpactAssessmentRepository impacts,
      AlternativePlanRepository plans) {
    this.trips = trips;
    this.nodes = nodes;
    this.events = events;
    this.impacts = impacts;
    this.plans = plans;
  }

  public record PlaceCount(String label, long count) {}

  public record BudgetBucket(String label, long count) {}

  public record Stats(
      long totalTrips,
      BigDecimal budgetMedian,
      BigDecimal totalBudget,
      long totalEvents,
      long hitEvents,
      long totalPlans,
      long acceptedPlans,
      List<PlaceCount> topPlaces,
      List<BudgetBucket> budgetBuckets) {}

  public Stats stats() {
    List<BigDecimal> budgets =
        trips.findAll().stream()
            .map(t -> t.getTotalBudget())
            .filter(b -> b != null)
            .sorted()
            .toList();
    BigDecimal median = BigDecimal.ZERO;
    if (!budgets.isEmpty()) {
      int mid = budgets.size() / 2;
      median =
          budgets.size() % 2 == 1
              ? budgets.get(mid)
              : budgets
                  .get(mid - 1)
                  .add(budgets.get(mid))
                  .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
    BigDecimal total = budgets.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

    long hitEvents = impacts.findAll().stream().map(i -> i.getEvent().getId()).distinct().count();

    List<Enums.PlanStatus> statuses = plans.findAll().stream().map(p -> p.getStatus()).toList();
    long accepted = statuses.stream().filter(s -> s == Enums.PlanStatus.ACCEPTED).count();

    Map<String, Long> byPlace =
        nodes.findAll().stream()
            .filter(n -> n.getPlaceName() != null && !n.getPlaceName().isBlank())
            .collect(Collectors.groupingBy(n -> n.getPlaceName(), Collectors.counting()));
    List<PlaceCount> topPlaces =
        byPlace.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(5)
            .map(e -> new PlaceCount(e.getKey(), e.getValue()))
            .toList();

    long low = budgets.stream().filter(b -> b.compareTo(BigDecimal.valueOf(1000)) < 0).count();
    long mid =
        budgets.stream()
            .filter(
                b ->
                    b.compareTo(BigDecimal.valueOf(1000)) >= 0
                        && b.compareTo(BigDecimal.valueOf(3000)) < 0)
            .count();
    long high =
        budgets.stream()
            .filter(
                b ->
                    b.compareTo(BigDecimal.valueOf(3000)) >= 0
                        && b.compareTo(BigDecimal.valueOf(8000)) < 0)
            .count();
    long top = budgets.stream().filter(b -> b.compareTo(BigDecimal.valueOf(8000)) >= 0).count();
    List<BudgetBucket> buckets =
        List.of(
            new BudgetBucket("<¥1k", low),
            new BudgetBucket("¥1k-3k", mid),
            new BudgetBucket("¥3k-8k", high),
            new BudgetBucket("≥¥8k", top));

    return new Stats(
        trips.count(),
        median,
        total,
        events.count(),
        hitEvents,
        statuses.size(),
        accepted,
        topPlaces,
        buckets);
  }
}
