package com.trip.adaptive.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WeatherClient {
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("${app.qweather.host:https://openapi.weathercn.com}")
    private String host;
    @Value("${app.qweather.key:}")
    private String key;

    public boolean enabled() {
        return !key.isEmpty();
    }

    //经纬度请求
    public String locationKey(double lat, double lon) {
        JsonNode n = get(host + "/locations/v1/cities/geoposition/search.json?apikey=" + key + "&q=" + lat + "," + lon + "&language=zh-cn");
        return (n != null && n.hasNonNull("Key")) ? n.get("Key").asText() : null;
    }

    //灾害预警
    public JsonNode alerts(String locationKey) {
        return get(host + "/alerts/v1/" + locationKey + ".json?apikey=" + key + "&language=zh-cn");
    }

    //逐日预报,降水信息
    public JsonNode dailyForecast(String locationKey) {
        return get(host + "/forecasts/v1/daily/1day/" + locationKey + ".json?apikey=" + key + "&language=zh-cn&details=true");
    }
    private JsonNode get(String url) {
        try {
            String body = http.getForObject(url, String.class);
            return body == null ? null : mapper.readTree(body);
        } catch (Exception e) {
            return null; // 失败降级：返回 null，上层用兜底逻辑
        }
    }
}
