package com.trip.adaptive.monitor.service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.service.TripService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeatherPollingJob {
    private final TripService trips;
    private final EventIngestionService ingestion;
    public WeatherPollingJob(TripService trips, EventIngestionService ingestion) {
        this.trips = trips;
        this.ingestion = ingestion;
    }
    @Scheduled(initialDelay = 10_000, fixedDelay= 3_600_000)
    public void run() {
        System.out.println("定时轮询");
        for(Trip trip : trips.all()){
            if(trip.getStatus() == Enums.TripStatus.ONGOING) {
                System.out.println("轮询：" + trip.getId());
                ingestion.ingestWeatherForTrip(trip.getId());
            }
        }
    }
}
