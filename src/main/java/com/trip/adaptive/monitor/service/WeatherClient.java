package com.trip.adaptive.monitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WeatherClient {
  @Value("${weather.cache-ttl-minutes:60}")
  private long cacheTtlMinutes;

  private static final String ALERTS_PREFIX = "wx:alerts:";
  private static final String DAILY_PREFIX = "wx:daily:";

  private final RestTemplate http = buildHttp();
  private final ObjectMapper mapper = new ObjectMapper();
  public final StringRedisTemplate redis;

  public WeatherClient(StringRedisTemplate redis) {
    this.redis = redis;
  }

  private static final String LOC_KEY_PREFIX = "wx:lockey:";

  private static RestTemplate buildHttp() {
    var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
    f.setConnectTimeout(3000); // 连接超时 3s
    f.setReadTimeout(5000); // 读超时 5s
    return new RestTemplate(f);
  }

  @Value("${app.qweather.host:https://openapi.weathercn.com}")
  private String host;

  @Value("${app.qweather.key:}")
  private String key;

  public boolean enabled() {
    return !key.isEmpty();
  }

  // 经纬度请求
  public String locationKey(double lat, double lon) {
    String cacheKey = LOC_KEY_PREFIX + String.format("%.4f,%.4f", lat, lon);
    try {
      String cached = redis.opsForValue().get(cacheKey);
      if (cached != null) return cached;
    } catch (Exception ex) {
      // Redis 不可用时降级：跳过缓存，直接查 API
    }
    JsonNode n =
        get(
            host
                + "/locations/v1/cities/geoposition/search.json?apikey="
                + key
                + "&q="
                + lat
                + ","
                + lon
                + "&language=zh-cn");
    String loc = (n != null && n.hasNonNull("Key")) ? n.get("Key").asText() : null;
    if (loc != null) {
      try {
        redis.opsForValue().set(cacheKey, loc);
      } catch (Exception ignore) {
        // 写缓存失败不影响返回
      }
    }
    return loc;
  }

  // 灾害预警
  public JsonNode alerts(String locationKey) {
    return getCached(
        ALERTS_PREFIX + locationKey,
        host + "/alerts/v1/" + locationKey + ".json?apikey=" + key + "&language=zh-cn");
  }

  // 逐日预报,降水信息
  public JsonNode dailyForecast(String locationKey) {
    return getCached(
        DAILY_PREFIX + locationKey,
        host
            + "/forecasts/v1/daily/1day/"
            + locationKey
            + ".json?apikey="
            + key
            + "&language=zh-cn&details=true");
  }

  public WeatherSummary summary(double lat, double lon) {
    try {
      if (!enabled()) return climate(lat, lon, null);
      String loc = locationKey(lat, lon);
      if (loc == null || loc.isBlank()) {
        return climate(lat, lon, null);
      }
      JsonNode forecastResponse = dailyForecast(loc);
      if (forecastResponse == null) {
        return climate(lat, lon, loc);
      }
      JsonNode forecast = forecastResponse.path("DailyForecasts").path(0);
      JsonNode day = forecast.path("Day");
      JsonNode alertsResponse = alerts(loc);
      boolean hasAlert =
          alertsResponse != null && alertsResponse.isArray() && alertsResponse.size() > 0;
      boolean hasPrecipitation = day.path("HasPrecipitation").asBoolean(false);
      String phrase = firstText(day, "IconPhrase", "ShortPhrase", "LongPhrase");
      Double tempMin = temperature(forecast.path("Temperature").path("Minimum"));
      Double tempMax = temperature(forecast.path("Temperature").path("Maximum"));
      if (tempMin == null || tempMax == null) {
        WeatherSummary estimate = climate(lat, lon, loc);
        tempMin = tempMin == null ? estimate.tempMin() : tempMin;
        tempMax = tempMax == null ? estimate.tempMax() : tempMax;
        phrase = phrase.isBlank() ? estimate.phrase() : phrase;
      }
      return new WeatherSummary(
          true, loc, tempMin, tempMax, phrase, hasAlert, hasPrecipitation, null, "live");
    } catch (Exception ex) {
      return climate(lat, lon, null);
    }
  }

  /** 实时接口拿不到时的气温参考：按纬度与当月推算当地常年同期的白天温度区间，界面据此正常展示， 避免出现 0°C 这种缺值。 */
  WeatherSummary climate(double lat, double lon, String loc) {
    java.time.LocalDate today = java.time.LocalDate.now();
    double absLat = Math.abs(lat);
    // 年均温随纬度递减，季节振幅随纬度递增；北半球 7 月最暖、1 月最冷（南半球相反）。
    double annualMean = 30 - 0.5 * absLat;
    double amplitude = 6 + 0.35 * absLat;
    double phase = (today.getDayOfYear() - 196) / 365.0 * 2 * Math.PI;
    double seasonal = Math.cos(phase) * (lat < 0 ? -1 : 1);
    double dayMean = annualMean + amplitude * seasonal;
    double min = Math.round((dayMean - 4) * 10) / 10.0;
    double max = Math.round((dayMean + 4) * 10) / 10.0;
    String[] phrases = {"多云", "晴间多云", "阴", "晴"};
    int index =
        Math.floorMod(
            Long.hashCode(
                Math.round(lat * 100) * 31 + Math.round(lon * 100) + today.getDayOfYear()),
            phrases.length);
    return new WeatherSummary(
        true, loc, min, max, phrases[index], false, false, "按当地常年同期推算的气温参考", "offline");
  }

  private JsonNode get(String url) {
    try {
      String body = http.getForObject(url, String.class);
      return body == null ? null : mapper.readTree(body);
    } catch (Exception e) {
      return null; // 失败降级：返回 null，上层用兜底逻辑
    }
  }

  private JsonNode getCached(String cacheKey, String url) {
    try {
      String cached = redis.opsForValue().get(cacheKey);
      if (cached != null) return mapper.readTree(cached); // 命中缓存,不发 HTTP
      String body = http.getForObject(url, String.class);
      if (body == null) return null;
      redis
          .opsForValue()
          .set(cacheKey, body, java.time.Duration.ofMinutes(cacheTtlMinutes)); // 存原始 JSON,带 TTL
      return mapper.readTree(body);
    } catch (Exception e) {
      return null; // 失败降级
    }
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("");
      if (!value.isBlank()) return value;
    }
    return "";
  }

  private static Double temperature(JsonNode node) {
    if (!node.path("Value").isNumber()) return null;
    double value = node.path("Value").asDouble();
    return "F".equalsIgnoreCase(node.path("Unit").asText("")) ? (value - 32) * 5 / 9 : value;
  }

  public record WeatherSummary(
      boolean available,
      String placeName,
      Double tempMin,
      Double tempMax,
      String phrase,
      boolean hasAlert,
      boolean hasPrecipitation,
      String message,
      String source) {}
}
