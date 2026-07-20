package com.trip.adaptive.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.monitor.service.WeatherClient;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {
  private final WeatherClient weather;

  public WeatherController(WeatherClient weather) {
    this.weather = weather;
  }

  @GetMapping("/preview")
  public WeatherClient.WeatherSummary preview(@RequestParam double lat, @RequestParam double lon) {
    return weather.summary(lat, lon);
  }
}
