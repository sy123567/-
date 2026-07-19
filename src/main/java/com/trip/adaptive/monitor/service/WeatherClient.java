package com.trip.adaptive.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
        f.setReadTimeout(5000);    // 读超时 5s
        return new RestTemplate(f);
    }
    @Value("${app.qweather.host:https://openapi.weathercn.com}")
    private String host;
    @Value("${app.qweather.key:}")
    private String key;

    public boolean enabled() {
        return !key.isEmpty();
    }

    //经纬度请求
    public String locationKey(double lat, double lon) {
        String cacheKey = LOC_KEY_PREFIX + String.format("%.4f,%.4f", lat, lon);
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception ex) {
            // Redis 不可用时降级：跳过缓存，直接查 API
        }
        JsonNode n = get(host + "/locations/v1/cities/geoposition/search.json?apikey=" + key + "&q=" + lat + "," + lon + "&language=zh-cn");
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
    //灾害预警
    public JsonNode alerts(String locationKey) {
        return getCached(ALERTS_PREFIX + locationKey,
                host + "/alerts/v1/" + locationKey + ".json?apikey=" + key + "&language=zh-cn");
    }

    //逐日预报,降水信息
    public JsonNode dailyForecast(String locationKey) {
        return getCached(DAILY_PREFIX + locationKey,
                host + "/forecasts/v1/daily/1day/" + locationKey + ".json?apikey=" + key + "&language=zh-cn&details=true");
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
            if (cached != null) return mapper.readTree(cached);   // 命中缓存,不发 HTTP
            String body = http.getForObject(url, String.class);
            if (body == null) return null;
            redis.opsForValue().set(cacheKey, body, java.time.Duration.ofMinutes(cacheTtlMinutes)); // 存原始 JSON,带 TTL
            return mapper.readTree(body);
        } catch (Exception e) {
            return null; // 失败降级
        }
    }
}
