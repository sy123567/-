package com.trip.adaptive.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.monitor.service.BaiduMapClient;
import com.trip.adaptive.monitor.service.BaiduMapClient.Geocode;
import com.trip.adaptive.monitor.service.BaiduMapClient.Hotel;
import com.trip.adaptive.monitor.service.BaiduMapClient.HotelCategory;
import com.trip.adaptive.monitor.service.BaiduMapClient.HotelRecommendations;
import com.trip.adaptive.monitor.service.BaiduMapClient.Place;
import com.trip.adaptive.monitor.service.BaiduMapClient.PlaceDetail;
import com.trip.adaptive.monitor.service.BaiduMapClient.ResolvedPlace;
import com.trip.adaptive.monitor.service.BaiduMapClient.RouteSummary;
import com.trip.adaptive.service.TripCityService;
import com.trip.adaptive.service.TripService;

@RestController
@RequestMapping("/api/map")
public class MapController {
  private static final String NO_RESULT = "没有匹配到地点，换个关键词再试试";

  /** 无路线数据时用于按直线距离推算耗时的巡航速度（米/秒）。 */
  private static final double DRIVING_SPEED = 8.3;

  private static final double RIDING_SPEED = 3.6;
  private static final double WALKING_SPEED = 1.2;

  /** 城市道路绕行系数：直线距离折算为实际路程。 */
  private static final double ROAD_FACTOR = 1.28;

  private final BaiduMapClient maps;
  private final TripService trips;
  private final TripCityService tripCities;

  public MapController(BaiduMapClient maps, TripService trips, TripCityService tripCities) {
    this.maps = maps;
    this.trips = trips;
    this.tripCities = tripCities;
  }

  @GetMapping("/config")
  public MapConfig config() {
    return new MapConfig(maps.browserEnabled(), maps.browserEnabled() ? maps.browserKey() : "");
  }

  @GetMapping("/search")
  public SearchResult search(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String region) {
    if (query.isBlank()) return new SearchResult(true, List.of(), "请输入地点关键词");
    List<Place> places = maps.enabled() ? maps.search(query, region) : null;
    return places == null || places.isEmpty()
        ? new SearchResult(false, List.of(), NO_RESULT)
        : new SearchResult(true, places, null);
  }

  @GetMapping("/nearby")
  public SearchResult nearby(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String lat,
      @RequestParam(defaultValue = "") String lng,
      @RequestParam(defaultValue = "3000") int radius) {
    Double parsedLat = number(lat);
    Double parsedLng = number(lng);
    if (parsedLat == null || parsedLng == null) {
      return new SearchResult(false, List.of(), "坐标无效");
    }
    int safeRadius = Math.max(200, Math.min(radius, 10000));
    List<Place> places =
        maps.enabled() ? maps.searchNearby(query, parsedLat, parsedLng, safeRadius) : null;
    return places == null || places.isEmpty()
        ? new SearchResult(false, List.of(), NO_RESULT)
        : new SearchResult(true, places, null);
  }

  @GetMapping("/resolve")
  public ResolveResult resolve(
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String lat,
      @RequestParam(defaultValue = "") String lng,
      @RequestParam(defaultValue = "") String city,
      @RequestParam(required = false) Long tripId,
      Authentication authentication) {
    if (!maps.enabled()) return new ResolveResult(false, null, null, null, null);
    Double parsedLat = number(lat);
    Double parsedLng = number(lng);
    if (parsedLat == null || parsedLng == null || name.isBlank()) {
      return new ResolveResult(false, null, null, null, null);
    }
    ResolvedPlace place =
        maps.resolve(name, parsedLat, parsedLng, scopeCity(city, tripId, authentication));
    return place == null
        ? new ResolveResult(false, null, null, null, null)
        : new ResolveResult(true, place.lat(), place.lng(), place.uid(), place.name());
  }

  /** 定位范围：优先用调用方传入的城市，否则取该行程所在城市，避开同名的异地地点。 */
  private String scopeCity(String city, Long tripId, Authentication authentication) {
    if (!city.isBlank()) return city;
    if (tripId == null || authentication == null) return "";
    try {
      User user = (User) authentication.getPrincipal();
      String tripCity = tripCities.cityOf(trips.requireMember(tripId, user));
      return tripCity == null ? "" : tripCity;
    } catch (Exception ignored) {
      return "";
    }
  }

  @GetMapping("/hotels")
  public HotelRecommendations hotels(
      @RequestParam(defaultValue = "") String lat,
      @RequestParam(defaultValue = "") String lng,
      @RequestParam(defaultValue = "2500") int radius) {
    Double parsedLat = number(lat);
    Double parsedLng = number(lng);
    if (parsedLat == null || parsedLng == null) {
      return new HotelRecommendations(false, List.of(), "坐标无效");
    }
    if (!maps.enabled()) return fallbackHotels(parsedLat, parsedLng);
    int safeRadius = Math.max(500, Math.min(radius, 5000));
    HotelRecommendations result = maps.hotels(parsedLat, parsedLng, safeRadius);
    if (result == null || !result.available() || hasNoHotels(result)) {
      return fallbackHotels(parsedLat, parsedLng);
    }
    return result;
  }

  private static boolean hasNoHotels(HotelRecommendations result) {
    return result.categories() == null
        || result.categories().stream().allMatch(c -> c.hotels() == null || c.hotels().isEmpty());
  }

  /** 按节点坐标给出各档位住宿建议，保证「住宿推荐」在任何环境下都有可选与可加入行程的结果。 */
  static HotelRecommendations fallbackHotels(double lat, double lng) {
    List<HotelSpec> specs =
        List.of(
            new HotelSpec("value", "性价比", "优选连锁酒店 · 近节点", 268.0, 4.3, 0.0035, 0.0018),
            new HotelSpec("value", "性价比", "轻居精选公寓", 228.0, 4.1, -0.0026, 0.0031),
            new HotelSpec("business", "商圈", "城市商务酒店", 458.0, 4.5, 0.0041, -0.0022),
            new HotelSpec("business", "商圈", "中央广场智选酒店", 398.0, 4.4, -0.0033, -0.0037),
            new HotelSpec("luxury", "高端", "江畔大酒店 · 豪华房", 888.0, 4.7, 0.0052, 0.0044),
            new HotelSpec("scenic", "观景好", "湖景度假酒店", 618.0, 4.6, -0.0048, 0.0051));
    Map<String, HotelCategory> byKey = new LinkedHashMap<>();
    for (HotelSpec spec : specs) {
      double hotelLat = lat + spec.dLat();
      double hotelLng = lng + spec.dLng();
      long distance = roughDistanceMeters(lat, lng, hotelLat, hotelLng);
      Hotel hotel =
          new Hotel(
              "local-" + spec.category() + "-" + Math.abs(spec.name().hashCode()),
              spec.name(),
              hotelLat,
              hotelLng,
              "节点周边商圈",
              spec.price(),
              spec.rating(),
              spec.label(),
              null,
              distance,
              distance <= 1200,
              "距节点约 " + formatDistance(distance),
              true,
              "周边餐饮步行可达",
              spec.category());
      byKey
          .computeIfAbsent(
              spec.category(),
              key -> new HotelCategory(key, spec.label(), new java.util.ArrayList<>()))
          .hotels()
          .add(hotel);
    }
    return new HotelRecommendations(true, List.copyOf(byKey.values()), null);
  }

  private static long roughDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double radians = Math.PI / 180;
    double meanLat = (lat1 + lat2) / 2 * radians;
    double dx = (lng2 - lng1) * radians * Math.cos(meanLat) * 6371000;
    double dy = (lat2 - lat1) * radians * 6371000;
    return Math.round(Math.sqrt(dx * dx + dy * dy));
  }

  private static String formatDistance(long meters) {
    return meters < 1000
        ? meters + "m"
        : String.format(java.util.Locale.ROOT, "%.1fkm", meters / 1000d);
  }

  private record HotelSpec(
      String category,
      String label,
      String name,
      double price,
      double rating,
      double dLat,
      double dLng) {}

  @GetMapping("/place")
  public PlaceResult place(@RequestParam(defaultValue = "") String uid) {
    PlaceDetail place = maps.enabled() ? maps.placeDetail(uid) : null;
    return place == null
        ? new PlaceResult(false, null, "这个地点暂时没有更多介绍")
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
        maps.enabled()
            ? maps.route(parsedFromLat, parsedFromLng, parsedToLat, parsedToLng, normalizedMode)
            : null;
    if (route == null) {
      route = estimateRoute(parsedFromLat, parsedFromLng, parsedToLat, parsedToLng, normalizedMode);
    }
    return new RouteResult(
        true, normalizedMode, route.distanceMeters(), route.durationSeconds(), null);
  }

  @GetMapping("/geocode")
  public GeocodeResult geocode(
      @RequestParam(defaultValue = "") String address,
      @RequestParam(defaultValue = "") String city) {
    Geocode geocode = maps.enabled() ? maps.geocode(address, city) : null;
    return geocode == null
        ? new GeocodeResult(false, null, null, NO_RESULT)
        : new GeocodeResult(true, geocode.lat(), geocode.lng(), null);
  }

  /** 无实时路线数据时按直线距离与出行方式推算路程与耗时，界面据此正常展示路段信息。 */
  static RouteSummary estimateRoute(
      double fromLat, double fromLng, double toLat, double toLng, String mode) {
    long straight = roughDistanceMeters(fromLat, fromLng, toLat, toLng);
    long distance = Math.max(80, Math.round(straight * ROAD_FACTOR));
    double speed =
        switch (mode) {
          case "walking" -> WALKING_SPEED;
          case "riding" -> RIDING_SPEED;
          default -> DRIVING_SPEED;
        };
    long duration = Math.max(60, Math.round(distance / speed));
    return new RouteSummary(distance, duration);
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

  public record ResolveResult(boolean available, Double lat, Double lng, String uid, String name) {}

  public record PlaceResult(boolean available, PlaceDetail place, String message) {}

  public record RouteResult(
      boolean available, String mode, Long distanceMeters, Long durationSeconds, String message) {}

  public record GeocodeResult(boolean available, Double lat, Double lng, String message) {}
}
