package com.trip.adaptive.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.monitor.service.BaiduMapClient;
import com.trip.adaptive.monitor.service.BaiduMapClient.Geocode;
import com.trip.adaptive.monitor.service.BaiduMapClient.Place;
import com.trip.adaptive.monitor.service.BaiduMapClient.PlaceDetail;
import com.trip.adaptive.monitor.service.BaiduMapClient.RouteSummary;

@RestController
@RequestMapping("/api/map")
public class MapController {
  private static final String UNAVAILABLE = "地图服务暂不可用，可稍后再试";
  private final BaiduMapClient maps;

  public MapController(BaiduMapClient maps) {
    this.maps = maps;
  }

  @GetMapping("/config")
  public MapConfig config() {
    return new MapConfig(maps.enabled(), maps.enabled() ? maps.accessKey() : "");
  }

  @GetMapping("/search")
  public SearchResult search(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String region) {
    if (!maps.enabled()) return new SearchResult(false, List.of(), UNAVAILABLE);
    if (query.isBlank()) return new SearchResult(true, List.of(), "请输入地点关键词");
    List<Place> places = maps.search(query, region);
    return places == null
        ? new SearchResult(false, List.of(), UNAVAILABLE)
        : new SearchResult(true, places, null);
  }

  @GetMapping("/place")
  public PlaceResult place(@RequestParam(defaultValue = "") String uid) {
    if (!maps.enabled()) return new PlaceResult(false, null, UNAVAILABLE);
    PlaceDetail place = maps.placeDetail(uid);
    return place == null
        ? new PlaceResult(false, null, UNAVAILABLE)
        : new PlaceResult(true, place, null);
  }

  @GetMapping("/route")
  public RouteResult route(
      @RequestParam(defaultValue = "") String fromLat,
      @RequestParam(defaultValue = "") String fromLng,
      @RequestParam(defaultValue = "") String toLat,
      @RequestParam(defaultValue = "") String toLng,
      @RequestParam(defaultValue = "driving") String mode) {
    String normalizedMode = normalizeMode(mode);
    if (!maps.enabled()) return new RouteResult(false, normalizedMode, null, null, UNAVAILABLE);
    Double parsedFromLat = number(fromLat);
    Double parsedFromLng = number(fromLng);
    Double parsedToLat = number(toLat);
    Double parsedToLng = number(toLng);
    if (parsedFromLat == null
        || parsedFromLng == null
        || parsedToLat == null
        || parsedToLng == null) {
      return new RouteResult(false, normalizedMode, null, null, "路线坐标无效");
    }
    RouteSummary route =
        maps.route(parsedFromLat, parsedFromLng, parsedToLat, parsedToLng, normalizedMode);
    return route == null
        ? new RouteResult(false, normalizedMode, null, null, UNAVAILABLE)
        : new RouteResult(
            true, normalizedMode, route.distanceMeters(), route.durationSeconds(), null);
  }

  @GetMapping("/geocode")
  public GeocodeResult geocode(@RequestParam(defaultValue = "") String address) {
    if (!maps.enabled()) return new GeocodeResult(false, null, null, UNAVAILABLE);
    Geocode geocode = maps.geocode(address);
    return geocode == null
        ? new GeocodeResult(false, null, null, UNAVAILABLE)
        : new GeocodeResult(true, geocode.lat(), geocode.lng(), null);
  }

  private static String normalizeMode(String mode) {
    return switch (mode == null ? "" : mode.trim().toLowerCase()) {
      case "riding" -> "riding";
      case "walking" -> "walking";
      default -> "driving";
    };
  }

  private static Double number(String value) {
    try {
      double parsed = Double.parseDouble(value);
      return Double.isFinite(parsed) ? parsed : null;
    } catch (Exception ignored) {
      return null;
    }
  }

  public record MapConfig(boolean available, String ak) {}

  public record SearchResult(boolean available, List<Place> places, String message) {}

  public record PlaceResult(boolean available, PlaceDetail place, String message) {}

  public record RouteResult(
      boolean available, String mode, Long distanceMeters, Long durationSeconds, String message) {}

  public record GeocodeResult(boolean available, Double lat, Double lng, String message) {}
}
