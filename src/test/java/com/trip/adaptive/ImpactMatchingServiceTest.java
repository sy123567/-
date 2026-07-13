package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.service.ImpactMatchingService;

class ImpactMatchingServiceTest {
  @Test
  void haversineDistanceIsReasonable() {
    assertEquals(0, ImpactMatchingService.distance(31.23, 121.47, 31.23, 121.47), 0.001);
    assertTrue(ImpactMatchingService.distance(31.23, 121.47, 31.24, 121.47) < 2);
  }
}
