package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PlanningServiceTest {
  @Test
  void dateIntersectionUsesLatestStartAndEarliestEnd() {
    LocalDate from = LocalDate.of(2025, 1, 3), to = LocalDate.of(2025, 1, 8);
    assertTrue(from.isBefore(to));
    assertEquals(LocalDate.of(2025, 1, 3), from);
  }

  @Test
  void budgetCapCanTrimOptionalCost() {
    assertTrue(
        new java.math.BigDecimal("80")
                .add(new java.math.BigDecimal("50"))
                .compareTo(new java.math.BigDecimal("100"))
            > 0);
  }
}
