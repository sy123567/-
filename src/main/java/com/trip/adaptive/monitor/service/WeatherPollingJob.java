package com.trip.adaptive.monitor.service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.service.TripService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeatherPollingJob {
    private final TripService trips;
    private final EventIngestionService ingestion;
    private final WeatherClient weather;

    @Value("${weather.poll-enabled:false}")
    private boolean pollEnabled;

    public WeatherPollingJob(TripService trips, EventIngestionService ingestion, WeatherClient weather) {
        this.trips = trips;
        this.ingestion = ingestion;
        this.weather = weather;
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "${weather.poll-interval-ms:3600000}")
    public void run() {
        if (!pollEnabled || !weather.enabled()) return;   // 开关关 or 没配 key → 不轮询,省额度
        for (Trip trip : trips.all()) {
            if (trip.getStatus() == Enums.TripStatus.ONGOING) {
                ingestion.ingestWeatherForTrip(trip.getId(), true);   // true = 只扫近未来窗口
            }
        }
    }
}
