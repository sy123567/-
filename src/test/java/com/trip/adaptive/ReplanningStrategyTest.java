package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.monitor.service.ReplanningService;
import com.trip.adaptive.monitor.service.ReplanningService.ActionOption;

class ReplanningStrategyTest {

  private static ActionOption reschedule() {
    return new ActionOption(
        Enums.ChangeType.RESCHEDULE,
        "原地点",
        0d,
        0d,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        120,
        1,
        "");
  }

  private static ActionOption replace(int deltaCost) {
    return new ActionOption(
        Enums.ChangeType.REPLACE,
        "新地点",
        1d,
        1d,
        null,
        null,
        BigDecimal.TEN,
        BigDecimal.valueOf(deltaCost),
        0,
        2,
        "");
  }

  private static ActionOption remove() {
    return new ActionOption(
        Enums.ChangeType.REMOVE, null, null, null, null, null, null, BigDecimal.ZERO, 0, 3, "");
  }

  @Test
  void minChangePrefersRescheduleOverReplaceAndRemove() {
    List<ActionOption> options = List.of(reschedule(), replace(50), remove());
    assertEquals(
        Enums.ChangeType.RESCHEDULE,
        ReplanningService.chooseOption(Enums.ReplanStrategy.MIN_CHANGE, options).type());
  }

  @Test
  void minDelayPrefersZeroDelayReplaceOverDelayedReschedule() {
    List<ActionOption> options = List.of(reschedule(), replace(50), remove());
    assertEquals(
        Enums.ChangeType.REPLACE,
        ReplanningService.chooseOption(Enums.ReplanStrategy.MIN_DELAY, options).type());
  }

  @Test
  void strategiesDivergeWhenRescheduleInfeasible() {
    List<ActionOption> options = List.of(replace(50), remove());
    // 无顺延可选：最省成本 -> 移除(0)；最小改动 -> 替换(change 2 < 3)
    assertEquals(
        Enums.ChangeType.REMOVE,
        ReplanningService.chooseOption(Enums.ReplanStrategy.MIN_EXTRA_COST, options).type());
    assertEquals(
        Enums.ChangeType.REPLACE,
        ReplanningService.chooseOption(Enums.ReplanStrategy.MIN_CHANGE, options).type());
    assertEquals(
        Enums.ChangeType.REPLACE,
        ReplanningService.chooseOption(Enums.ReplanStrategy.MIN_DELAY, options).type());
  }
}
