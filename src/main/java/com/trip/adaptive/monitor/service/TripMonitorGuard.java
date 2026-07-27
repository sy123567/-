package com.trip.adaptive.monitor.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * 同一行程的监测流水线串行执行。
 *
 * <p>事件接入与影响评估都会先清理该行程的旧事件再重新落库；两个请求同时跑同一行程时，一方读到的事件 可能已被另一方删除，写 impact_assessment 时就会撞上 event_id
 * 外键（SQLState 23000 / 错误码 1452）。 锁必须在事务开启之前获取，否则后到的事务已经拿到旧快照，串行化也救不回来。
 */
@Component
public class TripMonitorGuard {
  private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

  public <T> T runExclusively(Long tripId, Supplier<T> action) {
    if (tripId == null) return action.get();
    ReentrantLock lock = locks.computeIfAbsent(tripId, id -> new ReentrantLock());
    lock.lock();
    try {
      return action.get();
    } finally {
      lock.unlock();
    }
  }

  public void runExclusively(Long tripId, Runnable action) {
    runExclusively(
        tripId,
        () -> {
          action.run();
          return null;
        });
  }
}
