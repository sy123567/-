package com.trip.adaptive.monitor.service;

import java.time.Duration;
import java.util.ArrayList;
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
              text(item, "telephone")));
    }
    return places;
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
    if (!enabled() || address == null || address.isBlank()) return null;
    JsonNode root =
        getCached(
            "baidu:geocode:" + normalize(address),
            url("/geocoding/v3/", "address", address, "output", "json"),
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
      String body = http.getForObject(requestUrl, String.class);
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
      String telephone) {}

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
}
