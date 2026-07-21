package com.trip.adaptive.monitor.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Route;
import com.trip.adaptive.domain.Trip;

/**
 * 路线重算：节点的地点/坐标变化后，重新估算相邻路段的距离、耗时与成本。 优先调用百度地图真实路线；未配 key 时退回 Haversine 距离估算，保证离线/测试环境也能得到确定性结果。
 */
@Service
public class RouteRecalculationService {
  private final BaiduMapClient maps;

  @Value("${replan.route-cost-per-km:3}")
  private double costPerKm;

  @Value("${replan.route-avg-speed-kmh:30}")
  private double avgSpeedKmh;

  public RouteRecalculationService(BaiduMapClient maps) {
    this.maps = maps;
  }

  /** 两点间的估算路段（距离/耗时/成本），坐标缺失时返回 null。 */
  public Segment segment(Double fromLat, Double fromLng, Double toLat, Double toLng, String mode) {
    if (fromLat == null || fromLng == null || toLat == null || toLng == null) return null;
    if (maps.enabled()) {
      BaiduMapClient.RouteSummary summary = maps.route(fromLat, fromLng, toLat, toLng, mode);
      if (summary != null
          && summary.distanceMeters() != null
          && summary.durationSeconds() != null) {
        double km = summary.distanceMeters() / 1000d;
        int minutes = (int) Math.round(summary.durationSeconds() / 60d);
        return new Segment(round(km), Math.max(1, minutes), cost(km));
      }
    }
    double km = ImpactMatchingService.distance(fromLat, fromLng, toLat, toLng);
    int minutes = avgSpeedKmh <= 0 ? 0 : (int) Math.round(km / avgSpeedKmh * 60);
    return new Segment(round(km), Math.max(1, minutes), cost(km));
  }

  public BigDecimal cost(double km) {
    return BigDecimal.valueOf(km * costPerKm).setScale(2, RoundingMode.HALF_UP);
  }

  /** 行程内所有起讫点坐标齐全的路段全部按当前节点坐标重算。 */
  public void recompute(Trip trip) {
    for (Route route : trip.getRoutes()) {
      ItineraryNode from = route.getFromNode();
      ItineraryNode to = route.getToNode();
      if (from == null || to == null) continue;
      Segment segment =
          segment(
              from.getLatitude(),
              from.getLongitude(),
              to.getLatitude(),
              to.getLongitude(),
              route.getTransportMode() == null ? null : route.getTransportMode().name());
      if (segment == null) continue;
      route.setDistanceKm(segment.distanceKm());
      route.setDurationMinutes(segment.durationMinutes());
      route.setCost(segment.cost());
    }
  }

  private static double round(double km) {
    return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  public record Segment(double distanceKm, int durationMinutes, BigDecimal cost) {}
}
