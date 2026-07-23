package com.trip.adaptive.monitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.service.TripService;

@Component
public class WeatherPollingJob {
  private final TripService trips;
  private final EventIngestionService ingestion;

  @Value("${weather.poll-enabled:false}")
  private boolean pollEnabled;

  public WeatherPollingJob(TripService trips, EventIngestionService ingestion) {
    this.trips = trips;
    this.ingestion = ingestion;
  }

  @Scheduled(initialDelay = 10_000, fixedDelayString = "${weather.poll-interval-ms:3600000}")
  public void run() {
    if (!pollEnabled) return; // 轮询开关关闭
    // 天气有 key 才产出（内部已判断，省额度）；城市路况/公告事件不依赖外部 key，始终接入。
    for (Trip trip : trips.all()) {
      // 只跳过已结束/已取消的行程；DRAFT、PLANNED、ONGOING 都扫描。
      // 用户在界面新建的行程默认是 DRAFT，若只扫 ONGOING 会导致这些行程永远扫不到事件。
      // force=true 已按 [now, now+forecastWindowDays] 窗口过滤节点，不会为过期/远期节点做无谓处理。
      Enums.TripStatus status = trip.getStatus();
      if (status != Enums.TripStatus.COMPLETED && status != Enums.TripStatus.CANCELLED) {
        ingestion.ingestAllForTrip(trip.getId(), true); // true = 只扫近未来窗口
      }
    }
  }
}
