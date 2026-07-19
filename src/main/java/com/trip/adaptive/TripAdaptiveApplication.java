package com.trip.adaptive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@EnableScheduling
@SpringBootApplication
public class TripAdaptiveApplication {
  public static void main(String[] args) {
    SpringApplication.run(TripAdaptiveApplication.class, args);
  }
}
