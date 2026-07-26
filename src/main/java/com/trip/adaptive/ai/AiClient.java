package com.trip.adaptive.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AiClient {
  private final RestTemplate http = buildHttp();
  private final ObjectMapper mapper;

  @Value("${app.deepseek.key:}")
  private String key;

  @Value("${app.deepseek.host:https://api.deepseek.com}")
  private String host;

  @Value("${app.deepseek.model:deepseek-chat}")
  private String model;

  public AiClient(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public boolean enabled() {
    return key != null && !key.isBlank();
  }

  /** 自由文本对话，用于智能助手；不可用或调用失败时返回 null，由调用方降级。 */
  public String chatText(String systemPrompt, String userPrompt) {
    if (!enabled()) return null;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(key);
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "messages",
              List.of(
                  Map.of("role", "system", "content", systemPrompt),
                  Map.of("role", "user", "content", userPrompt)),
              "temperature",
              0.6);
      String endpoint = host.replaceAll("/+$", "") + "/chat/completions";
      JsonNode response =
          http.postForObject(endpoint, new HttpEntity<>(body, headers), JsonNode.class);
      String content =
          response == null
              ? ""
              : response.path("choices").path(0).path("message").path("content").asText("");
      return content.isBlank() ? null : content.trim();
    } catch (Exception ignored) {
      return null;
    }
  }

  public JsonNode chatJson(String systemPrompt, String userPrompt) {
    if (!enabled()) return null;
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(key);
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "messages",
              List.of(
                  Map.of("role", "system", "content", systemPrompt),
                  Map.of("role", "user", "content", userPrompt)),
              "response_format",
              Map.of("type", "json_object"),
              "temperature",
              0.7);
      String endpoint = host.replaceAll("/+$", "") + "/chat/completions";
      HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
      JsonNode response = http.postForObject(endpoint, request, JsonNode.class);
      String content =
          response == null
              ? ""
              : response.path("choices").path(0).path("message").path("content").asText("");
      return content.isBlank() ? null : mapper.readTree(content);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static RestTemplate buildHttp() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);
    factory.setReadTimeout(40_000);
    return new RestTemplate(factory);
  }
}
