package com.trip.adaptive.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.monitor.service.BaiduMapClient;

/** 判定一段行程属于哪座旅游城市，供节点定位限定检索范围。 */
@Service
public class TripCityService {
  private final BaiduMapClient maps;

  public TripCityService(BaiduMapClient maps) {
    this.maps = maps;
  }

  /** 取全部节点坐标的中位数反查城市：个别偏离的节点不会影响这段行程的城市判定。 */
  public String cityOf(Trip trip) {
    if (trip == null) return null;
    List<Double> latitudes = new ArrayList<>();
    List<Double> longitudes = new ArrayList<>();
    for (ItineraryNode node : trip.getItineraryNodes()) {
      if (node.getLatitude() == null || node.getLongitude() == null) continue;
      latitudes.add(node.getLatitude());
      longitudes.add(node.getLongitude());
    }
    if (latitudes.isEmpty()) return null;
    return maps.reverseCity(median(latitudes), median(longitudes));
  }

  private static double median(List<Double> values) {
    List<Double> sorted = values.stream().sorted().toList();
    return sorted.get(sorted.size() / 2);
  }
}
