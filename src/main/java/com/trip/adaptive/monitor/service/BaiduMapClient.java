package com.trip.adaptive.monitor.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BaiduMapClient {
  private static final Duration SEARCH_TTL = Duration.ofHours(24);
  private static final Duration DETAIL_TTL = Duration.ofHours(24);
  private static final Duration ROUTE_TTL = Duration.ofHours(6);
  private static final Duration GEOCODE_TTL = Duration.ofDays(30);

  /** 节点定位允许偏离原坐标的上限：超过这个距离说明命中了同名的异地地点。 */
  private static final long MAX_RESOLVE_METERS = 30_000;

  /** 判定一个坐标是否仍在目的地城市范围内的半径。 */
  public static final long CITY_RADIUS_METERS = 80_000;

  private final RestTemplate http = buildHttp();
  private final ObjectMapper mapper = new ObjectMapper();
  private final StringRedisTemplate redis;

  @Value("${app.baidu.ak:}")
  private String ak;

  @Value("${app.baidu.js-ak:}")
  private String jsAk;

  @Value("${app.baidu.host:https://api.map.baidu.com}")
  private String host;

  public BaiduMapClient(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public boolean enabled() {
    return ak != null && !ak.isBlank();
  }

  public String accessKey() {
    return ak == null ? "" : ak;
  }

  public boolean browserEnabled() {
    return browserKey() != null && !browserKey().isBlank();
  }

  public String browserKey() {
    return jsAk != null && !jsAk.isBlank() ? jsAk : accessKey();
  }

  public List<Place> search(String query, String region) {
    if (!enabled() || query == null || query.isBlank()) return List.of();
    String normalizedQuery = normalize(query);
    String normalizedRegion = normalize(region);
    String cacheKey = "baidu:search:" + normalizedQuery + ":" + normalizedRegion;
    JsonNode root =
        getCached(
            cacheKey,
            url(
                "/place/v2/search",
                "query",
                query,
                "region",
                region == null ? "" : region,
                "output",
                "json",
                "city_limit",
                "true"),
            SEARCH_TTL);
    if (!ok(root)) return null;
    List<Place> places = new ArrayList<>();
    for (JsonNode item : root.path("results")) {
      JsonNode location = item.path("location");
      places.add(
          new Place(
              text(item, "name"),
              number(location, "lat"),
              number(location, "lng"),
              text(item, "address"),
              text(item, "province"),
              text(item, "city"),
              text(item, "area"),
              text(item, "uid"),
              text(item, "telephone"),
              null,
              null,
              null,
              null,
              null,
              null));
    }
    return places;
  }

  public List<Place> searchNearby(String query, double lat, double lng, int radius) {
    List<Place> places = searchNearbyRaw(query, lat, lng, radius);
    if (places == null) return null;
    List<Place> consumerPlaces =
        places.stream()
            .filter(BaiduMapClient::isConsumerPlace)
            .sorted(BaiduMapClient::compareConsumerPlaces)
            .toList();
    return consumerPlaces.isEmpty()
        ? places.stream()
            .sorted(Comparator.comparing(BaiduMapClient::distanceOrMax))
            .limit(8)
            .toList()
        : consumerPlaces;
  }

  private List<Place> searchNearbyRaw(String query, double lat, double lng, int radius) {
    if (!enabled() || query == null || query.isBlank()) return List.of();
    String normalizedQuery = normalize(query);
    String roundedLat = String.format(Locale.ROOT, "%.4f", lat);
    String roundedLng = String.format(Locale.ROOT, "%.4f", lng);
    String cacheKey =
        "baidu:nearby:" + normalizedQuery + ":" + roundedLat + ":" + roundedLng + ":" + radius;
    JsonNode root =
        getCached(
            cacheKey,
            url(
                "/place/v2/search",
                "query",
                query,
                "location",
                coordinate(lat, lng),
                "radius",
                String.valueOf(radius),
                "radius_limit",
                "true",
                "scope",
                "2",
                "output",
                "json"),
            SEARCH_TTL);
    if (!ok(root)) return null;
    List<Place> places = new ArrayList<>();
    for (JsonNode item : root.path("results")) {
      JsonNode location = item.path("location");
      JsonNode detail = item.path("detail_info");
      places.add(
          new Place(
              text(item, "name"),
              number(location, "lat"),
              number(location, "lng"),
              text(item, "address"),
              text(item, "province"),
              text(item, "city"),
              text(item, "area"),
              text(item, "uid"),
              text(item, "telephone"),
              number(detail, "overall_rating"),
              integer(detail, "comment_num"),
              number(detail, "price"),
              text(detail, "tag"),
              text(detail, "image"),
              longValue(detail, "distance")));
    }
    return places;
  }

  private static boolean isConsumerPlace(Place place) {
    String text =
        normalize(
            (place.name() == null ? "" : place.name())
                + " "
                + (place.tag() == null ? "" : place.tag()));
    return !containsAny(
        text, "公司", "有限公司", "企业", "集团", "大厦", "写字楼", "办公", "科技园", "产业园", "工业园", "厂", "事务所", "银行后台",
        "银行后勤", "营业部办公室");
  }

  private static int compareConsumerPlaces(Place left, Place right) {
    int metadata = Integer.compare(metadataScore(right), metadataScore(left));
    return metadata != 0 ? metadata : Long.compare(distanceOrMax(left), distanceOrMax(right));
  }

  private static int metadataScore(Place place) {
    return (place.tag() == null || place.tag().isBlank() ? 0 : 1)
        + (place.overallRating() != null ? 1 : 0);
  }

  private static long distanceOrMax(Place place) {
    return place.distanceMeters() == null ? Long.MAX_VALUE : place.distanceMeters();
  }

  private static boolean containsAny(String value, String... terms) {
    for (String term : terms) {
      if (value.contains(normalize(term))) return true;
    }
    return false;
  }

  public ResolvedPlace resolve(String name, double lat, double lng) {
    return resolve(name, lat, lng, null);
  }

  /** 按名称定位到节点周边的真实坐标；city 非空时只接受该城市内的结果，避免定位到同名异地地点。 */
  public ResolvedPlace resolve(String name, double lat, double lng, String city) {
    if (!enabled() || name == null || name.isBlank()) return null;
    ResolvedPlace snapped = snapIntoCity(name, lat, lng, city);
    if (snapped != null) return snapped;
    String roundedLat = String.format(Locale.ROOT, "%.4f", lat);
    String roundedLng = String.format(Locale.ROOT, "%.4f", lng);
    String cacheKey =
        "baidu:resolve:"
            + normalize(name)
            + ":"
            + roundedLat
            + ":"
            + roundedLng
            + ":"
            + cityToken(city);
    JsonNode root =
        getCached(
            cacheKey,
            url(
                "/place/v2/search",
                "query",
                name,
                "location",
                coordinate(lat, lng),
                "radius",
                "10000",
                "radius_limit",
                "true",
                "scope",
                "2",
                "output",
                "json"),
            SEARCH_TTL);
    if (!ok(root)) return geocodeFallback(name, city, lat, lng);
    String normalizedName = normalize(name);
    String normalizedCity = cityToken(city);
    JsonNode best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    int bestMatchTier = -1;
    int bestResultIndex = Integer.MAX_VALUE;
    int resultIndex = 0;
    for (JsonNode item : root.path("results")) {
      JsonNode location = item.path("location");
      Double candidateLat = number(location, "lat");
      Double candidateLng = number(location, "lng");
      String candidateName = text(item, "name");
      if (candidateLat == null || candidateLng == null || candidateName == null) {
        resultIndex++;
        continue;
      }
      if (!withinCity(normalizedCity, text(item, "city"))
          || placeDistanceMeters(lat, lng, candidateLat, candidateLng) > MAX_RESOLVE_METERS) {
        resultIndex++;
        continue;
      }
      String normalizedCandidateName = normalize(candidateName);
      int matchTier =
          normalizedCandidateName.equals(normalizedName)
              ? 2
              : normalizedCandidateName.contains(normalizedName)
                      || normalizedName.contains(normalizedCandidateName)
                  ? 1
                  : 0;
      double distance = Math.pow(candidateLat - lat, 2) + Math.pow(candidateLng - lng, 2);
      if (matchTier > bestMatchTier
          || (matchTier == bestMatchTier && resultIndex < bestResultIndex)
          || (matchTier == bestMatchTier
              && resultIndex == bestResultIndex
              && distance < bestDistance)) {
        best = item;
        bestDistance = distance;
        bestMatchTier = matchTier;
        bestResultIndex = resultIndex;
      }
      resultIndex++;
    }
    if (best == null) return geocodeFallback(name, city, lat, lng);
    JsonNode location = best.path("location");
    return new ResolvedPlace(
        number(location, "lat"), number(location, "lng"), text(best, "uid"), text(best, "name"));
  }

  /** 坐标已经落到城市之外时（例如同名异地地点），按城市重新定位这个地点，把它拉回城市内。 */
  private ResolvedPlace snapIntoCity(String name, double lat, double lng, String city) {
    Geocode center = cityCenter(city);
    if (center == null || center.lat() == null || center.lng() == null) return null;
    if (placeDistanceMeters(center.lat(), center.lng(), lat, lng) <= CITY_RADIUS_METERS)
      return null;
    Geocode located = locateInCity(name, city);
    if (located == null || located.lat() == null || located.lng() == null) return null;
    return placeDistanceMeters(center.lat(), center.lng(), located.lat(), located.lng())
            > CITY_RADIUS_METERS
        ? null
        : new ResolvedPlace(located.lat(), located.lng(), null, name);
  }

  /** 地点检索无命中时退回地理编码，仍限定在同一城市且不能偏离节点太远。 */
  private ResolvedPlace geocodeFallback(String name, String city, double lat, double lng) {
    Geocode geo = geocode(name, city);
    if (geo == null || geo.lat() == null || geo.lng() == null) return null;
    return placeDistanceMeters(lat, lng, geo.lat(), geo.lng()) > MAX_RESOLVE_METERS
        ? null
        : new ResolvedPlace(geo.lat(), geo.lng(), null, name);
  }

  /** 把候选地点限定在指定城市内：百度返回的 city 带“市”后缀，取前缀比对。 */
  private static boolean withinCity(String normalizedCity, String candidateCity) {
    if (normalizedCity.isBlank()) return true;
    String candidate = cityToken(candidateCity);
    return candidate.isBlank()
        || candidate.contains(normalizedCity)
        || normalizedCity.contains(candidate);
  }

  /** 去掉“市/市区/自治州”等后缀，使“北京”与“北京市”能匹配。 */
  private static String cityToken(String city) {
    String normalized = normalize(city);
    for (String suffix : List.of("自治州", "市辖区", "地区", "市")) {
      if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
        return normalized.substring(0, normalized.length() - suffix.length());
      }
    }
    return normalized;
  }

  /** 反向地理编码取城市名，用于判断一段行程落在哪座城市。 */
  public String reverseCity(double lat, double lng) {
    if (!enabled()) return null;
    String location = coordinate(lat, lng);
    JsonNode root =
        getCached(
            "baidu:reverse-city:" + location,
            url("/reverse_geocoding/v3/", "location", location, "output", "json"),
            GEOCODE_TTL);
    if (!ok(root)) return null;
    String city = text(root.path("result").path("addressComponent"), "city");
    return city.isBlank() ? null : city;
  }

  /** 城市中心坐标，用于判断某个地点是否落在目的地城市内。 */
  public Geocode cityCenter(String city) {
    return city == null || city.isBlank() ? null : geocode(city.trim(), city.trim());
  }

  /** 在指定城市内按名称定位地点，命中不了返回 null。 */
  public Geocode locateInCity(String name, String city) {
    List<Place> places = search(name, city);
    if (places == null) return null;
    return places.stream()
        .filter(place -> place.lat() != null && place.lng() != null)
        .findFirst()
        .map(place -> new Geocode(place.lat(), place.lng()))
        .orElseGet(() -> geocode(name, city));
  }

  /** 两点间的球面距离（米）。 */
  public static long distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    return placeDistanceMeters(lat1, lng1, lat2, lng2);
  }

  public HotelRecommendations hotels(double lat, double lng, int radius) {
    if (!enabled()) return null;
    List<Place> hotels = searchNearbyRaw("酒店", lat, lng, radius);
    if (hotels == null) return null;
    List<Place> metros = searchNearbyRaw("地铁站", lat, lng, radius);
    List<Place> food = searchNearbyRaw("小吃 美食", lat, lng, radius);
    List<Hotel> classified = new ArrayList<>();
    for (Place hotel : hotels) {
      if (hotel.lat() == null || hotel.lng() == null || hotel.name() == null) continue;
      long distance = placeDistanceMeters(lat, lng, hotel.lat(), hotel.lng());
      Long nearestMetro = nearestDistance(metros, hotel.lat(), hotel.lng());
      Long nearestFood = nearestDistance(food, hotel.lat(), hotel.lng());
      boolean hasMetro = metros != null && !metros.isEmpty();
      boolean transitConvenient =
          hasMetro ? nearestMetro != null && nearestMetro <= 800 : distance <= 1500;
      String transitNote =
          hasMetro && nearestMetro != null
              ? "距地铁 " + formatDistance(nearestMetro)
              : "距节点约 " + formatDistance(distance);
      boolean foodNearby = nearestFood != null && nearestFood <= 250;
      String foodNote = foodNearby ? "楼下约 " + formatDistance(nearestFood) + " 有小吃" : null;
      String category = hotelCategory(hotel);
      classified.add(
          new Hotel(
              hotel.uid(),
              hotel.name(),
              hotel.lat(),
              hotel.lng(),
              hotel.address(),
              hotel.price(),
              hotel.overallRating(),
              hotel.tag(),
              hotel.image(),
              distance,
              transitConvenient,
              transitNote,
              foodNearby,
              foodNote,
              category));
    }
    List<HotelCategory> categories = new ArrayList<>();
    for (String key : List.of("value", "business", "luxury", "scenic")) {
      List<Hotel> categoryHotels =
          classified.stream().filter(hotel -> hotel.category().equals(key)).limit(8).toList();
      categories.add(new HotelCategory(key, hotelCategoryLabel(key), categoryHotels));
    }
    return new HotelRecommendations(true, categories, null);
  }

  private static String hotelCategory(Place hotel) {
    String text =
        normalize(
            (hotel.name() == null ? "" : hotel.name())
                + " "
                + (hotel.tag() == null ? "" : hotel.tag()));
    double rating = hotel.overallRating() == null ? 0 : hotel.overallRating();
    double price = hotel.price() == null ? Double.NaN : hotel.price();
    if ((Double.isFinite(price) && price >= 500)
        || rating >= 4.8
        || containsAny(text, "五星", "豪华", "度假", "大酒店", "希尔顿", "万豪", "洲际", "凯悦")) {
      return "luxury";
    }
    if (containsAny(text, "观景", "湖", "江", "山", "景区", "景观", "度假区")) return "scenic";
    if (Double.isFinite(price) && price <= 300 && rating >= 3.8) return "value";
    if (containsAny(text, "商务", "购物中心", "万达", "cbd", "商圈") || rating >= 4.3) {
      return "business";
    }
    return Double.isFinite(price) && price <= 350 ? "value" : "business";
  }

  private static String hotelCategoryLabel(String key) {
    return switch (key) {
      case "value" -> "性价比";
      case "business" -> "商圈";
      case "luxury" -> "高端";
      case "scenic" -> "观景好";
      default -> key;
    };
  }

  private static Long nearestDistance(List<Place> places, double lat, double lng) {
    if (places == null || places.isEmpty()) return null;
    return places.stream()
        .filter(place -> place.lat() != null && place.lng() != null)
        .map(place -> placeDistanceMeters(lat, lng, place.lat(), place.lng()))
        .min(Long::compareTo)
        .orElse(null);
  }

  private static long placeDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double radians = Math.PI / 180;
    double deltaLat = (lat2 - lat1) * radians;
    double deltaLng = (lng2 - lng1) * radians;
    double value =
        Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(lat1 * radians)
                * Math.cos(lat2 * radians)
                * Math.sin(deltaLng / 2)
                * Math.sin(deltaLng / 2);
    return Math.round(6371000 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value)));
  }

  private static String formatDistance(long meters) {
    return meters < 1000 ? meters + "m" : String.format(Locale.ROOT, "%.1fkm", meters / 1000d);
  }

  public PlaceDetail placeDetail(String uid) {
    if (!enabled() || uid == null || uid.isBlank()) return null;
    JsonNode root =
        getCached(
            "baidu:place:" + normalize(uid),
            url("/place/v2/detail", "uid", uid, "scope", "2", "output", "json"),
            DETAIL_TTL);
    if (!ok(root)) return null;
    JsonNode result = root.path("result");
    JsonNode detail = result.path("detail_info");
    JsonNode location = result.path("location");
    return new PlaceDetail(
        uid,
        text(result, "name"),
        number(location, "lat"),
        number(location, "lng"),
        text(result, "address"),
        text(result, "telephone"),
        number(detail, "overall_rating"),
        integer(detail, "comment_num"),
        number(detail, "price"),
        text(detail, "tag"),
        text(detail, "image"));
  }

  public RouteSummary route(
      double fromLat, double fromLng, double toLat, double toLng, String mode) {
    if (!enabled()) return null;
    String normalizedMode = normalizeMode(mode);
    String origin = coordinate(fromLat, fromLng);
    String destination = coordinate(toLat, toLng);
    String cacheKey = "baidu:route:" + normalizedMode + ":" + origin + ":" + destination;
    JsonNode root =
        getCached(
            cacheKey,
            url(
                "/directionlite/v1/" + normalizedMode,
                "origin",
                origin,
                "destination",
                destination),
            ROUTE_TTL);
    if (!ok(root)) return null;
    JsonNode route = root.path("result").path("routes").path(0);
    if (!route.isObject()) return null;
    Long distance = longValue(route, "distance");
    Long duration = longValue(route, "duration");
    if (distance == null || duration == null) return null;
    return new RouteSummary(distance, duration);
  }

  public Geocode geocode(String address) {
    return geocode(address, null);
  }

  /** city 非空时限定在该城市内做地理编码，同名地点不会落到其它城市。 */
  public Geocode geocode(String address, String city) {
    if (!enabled() || address == null || address.isBlank()) return null;
    String scope = city == null ? "" : city.trim();
    JsonNode root =
        getCached(
            "baidu:geocode:" + normalize(scope) + ":" + normalize(address),
            url("/geocoding/v3/", "address", address, "city", scope, "output", "json"),
            GEOCODE_TTL);
    if (!ok(root)) return null;
    JsonNode location = root.path("result").path("location");
    Double lat = number(location, "lat");
    Double lng = number(location, "lng");
    return lat == null || lng == null ? null : new Geocode(lat, lng);
  }

  private JsonNode getCached(String cacheKey, String requestUrl, Duration ttl) {
    try {
      String cached = redis.opsForValue().get(cacheKey);
      if (cached != null) {
        try {
          JsonNode root = mapper.readTree(cached);
          if (ok(root)) return root;
        } catch (Exception ignored) {
          // 缓存内容损坏时继续请求百度接口。
        }
      }
    } catch (Exception ignored) {
      // Redis 读取失败时跳过缓存，仍然请求百度接口。
    }
    try {
      // requestUrl 已由 url() 完成百分号编码，须以 URI 发送，避免 RestTemplate 二次编码破坏中文参数。
      String body = http.getForObject(java.net.URI.create(requestUrl), String.class);
      if (body == null || body.isBlank()) return null;
      JsonNode root = mapper.readTree(body);
      if (ok(root)) {
        try {
          redis.opsForValue().set(cacheKey, body, ttl);
        } catch (Exception ignored) {
          // Redis 写入失败不影响百度接口结果。
        }
      }
      return root;
    } catch (Exception ignored) {
      return null;
    }
  }

  private String url(String path, String... parameters) {
    try {
      UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(host).path(path);
      for (int i = 0; i < parameters.length; i += 2) {
        builder.queryParam(parameters[i], parameters[i + 1]);
      }
      return builder.queryParam("ak", ak).build().encode().toUriString();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static RestTemplate buildHttp() {
    var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    return new RestTemplate(factory);
  }

  private static boolean ok(JsonNode root) {
    return root != null && root.path("status").asInt(-1) == 0;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static String normalizeMode(String mode) {
    return switch (normalize(mode)) {
      case "riding" -> "riding";
      case "walking" -> "walking";
      default -> "driving";
    };
  }

  private static String coordinate(double latitude, double longitude) {
    return String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isValueNode() && !value.isNull() ? value.asText(null) : null;
  }

  private static Double number(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (value.isNumber()) return value.asDouble();
    if (!value.isTextual()) return null;
    try {
      return Double.valueOf(value.asText());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private static Integer integer(JsonNode node, String field) {
    Double value = number(node, field);
    return value == null ? null : value.intValue();
  }

  private static Long longValue(JsonNode node, String field) {
    Double value = number(node, field);
    return value == null ? null : value.longValue();
  }

  public record Place(
      String name,
      Double lat,
      Double lng,
      String address,
      String province,
      String city,
      String area,
      String uid,
      String telephone,
      Double overallRating,
      Integer commentNum,
      Double price,
      String tag,
      String image,
      Long distanceMeters) {}

  public record PlaceDetail(
      String uid,
      String name,
      Double lat,
      Double lng,
      String address,
      String telephone,
      Double overallRating,
      Integer commentNum,
      Double price,
      String tag,
      String image) {}

  public record RouteSummary(Long distanceMeters, Long durationSeconds) {}

  public record Geocode(Double lat, Double lng) {}

  public record ResolvedPlace(Double lat, Double lng, String uid, String name) {}

  public record HotelRecommendations(
      boolean available, List<HotelCategory> categories, String message) {}

  public record HotelCategory(String key, String label, List<Hotel> hotels) {}

  public record Hotel(
      String uid,
      String name,
      Double lat,
      Double lng,
      String address,
      Double price,
      Double rating,
      String tag,
      String image,
      Long distanceMeters,
      boolean transitConvenient,
      String transitNote,
      boolean foodNearby,
      String foodNote,
      String category) {}
}
