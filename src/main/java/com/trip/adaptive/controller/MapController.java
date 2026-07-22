package com.trip.adaptive.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.monitor.service.BaiduMapClient;
import com.trip.adaptive.monitor.service.BaiduMapClient.Geocode;
import com.trip.adaptive.monitor.service.BaiduMapClient.Hotel;
import com.trip.adaptive.monitor.service.BaiduMapClient.HotelCategory;
import com.trip.adaptive.monitor.service.BaiduMapClient.HotelRecommendations;
import com.trip.adaptive.monitor.service.BaiduMapClient.Place;
import com.trip.adaptive.monitor.service.BaiduMapClient.PlaceDetail;
import com.trip.adaptive.monitor.service.BaiduMapClient.ResolvedPlace;
import com.trip.adaptive.monitor.service.BaiduMapClient.RouteSummary;

@RestController
@RequestMapping("/api/map")
public class MapController {
  private static final String UNAVAILABLE = "地图服务暂不可用，可稍后再试";
  private static final String MAP_FALLBACK_NOTE = "地图服务未接入，以下为本地兜底住宿参考，接入后自动切换实时结果";
  private final BaiduMapClient maps;

  public MapController(BaiduMapClient maps) {
    this.maps = maps;
  }

  @GetMapping("/config")
  public MapConfig config() {
    return new MapConfig(maps.browserEnabled(), maps.browserEnabled() ? maps.browserKey() : "");
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

  @GetMapping("/nearby")
  public SearchResult nearby(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "") String lat,
      @RequestParam(defaultValue = "") String lng,
      @RequestParam(defaultValue = "3000") int radius) {
    if (!maps.enabled()) return new SearchResult(false, List.of(), UNAVAILABLE);
    Double parsedLat = number(lat);
    Double parsedLng = number(lng);
    if (parsedLat == null || parsedLng == null) {
      return new SearchResult(false, List.of(), "坐标无效");
    }
    int safeRadius = Math.max(200, Math.min(radius, 10000));
    List<Place> places = maps.searchNearby(query, parsedLat, parsedLng, safeRadius);
    return places == null
        ? new SearchResult(false, List.of(), UNAVAILABLE)
        : new SearchResult(true, places, null);
  }

  @GetMapping("/resolve")
  public ResolveResult resolve(
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String lat,
      @RequestParam(defaultValue = "") String lng) {
    if (!maps.enabled()) return new ResolveResult(false, null, null, null, null);
    Double parsedLat = number(lat);
    Double parsedLng = number(lng);
    if (parsedLat == null || parsedLng == null || name.isBlank()) {
      return new ResolveResult(false, null, null, null, null);
    }
    ResolvedPlace place = maps.resolve(name, parsedLat, parsedLng);
    return place == null
        ? new ResolveResult(false, null, null, null, null)
        : new ResolveResult(true, place.lat(), place.lng(), place.uid(), place.name());
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
    // 地图服务未接入或返回失败/空结果时，退回本地兜底住宿，避免"住宿推荐"整块无内容。
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

  /** 本地兜底住宿：围绕节点坐标生成不同档位的示例酒店，供地图服务不可用时展示与加入行程。 */
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
              "本地推荐地址 · 节点周边",
              spec.price(),
              spec.rating(),
              spec.label(),
              null,
              distance,
              distance <= 1200,
              "本地兜底 · 距节点约 " + formatDistance(distance),
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
    return new HotelRecommendations(true, List.copyOf(byKey.values()), MAP_FALLBACK_NOTE);
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

  public record ResolveResult(boolean available, Double lat, Double lng, String uid, String name) {}

  public record PlaceResult(boolean available, PlaceDetail place, String message) {}

  public record RouteResult(
      boolean available, String mode, Long distanceMeters, Long durationSeconds, String message) {}

  public record GeocodeResult(boolean available, Double lat, Double lng, String message) {}
}
