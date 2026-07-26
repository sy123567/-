package com.trip.adaptive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.trip.adaptive.monitor.service.TripMonitorGuard;

class TripMonitorGuardTest {
  /** 同一行程的监测必须串行：并发重建事件会让影响评估写到已被删除的事件上。 */
  @Test
  void sameTripRunsSerially() throws Exception {
    TripMonitorGuard guard = new TripMonitorGuard();
    AtomicInteger concurrent = new AtomicInteger();
    AtomicInteger maxConcurrent = new AtomicInteger();
    CountDownLatch done = new CountDownLatch(8);
    for (int i = 0; i < 8; i++) {
      new Thread(
              () ->
                  guard.runExclusively(
                      7L,
                      () -> {
                        maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                        try {
                          Thread.sleep(5);
                        } catch (InterruptedException ignored) {
                          Thread.currentThread().interrupt();
                        }
                        concurrent.decrementAndGet();
                        done.countDown();
                      }))
          .start();
    }
    done.await();
    assertThat(maxConcurrent.get()).isEqualTo(1);
  }

  /** 不同行程之间不互相阻塞。 */
  @Test
  void differentTripsRunInParallel() throws Exception {
    TripMonitorGuard guard = new TripMonitorGuard();
    CountDownLatch bothInside = new CountDownLatch(2);
    Runnable task =
        () -> {
          bothInside.countDown();
          try {
            assertThat(bothInside.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
          }
        };
    Thread first = new Thread(() -> guard.runExclusively(1L, task));
    Thread second = new Thread(() -> guard.runExclusively(2L, task));
    first.start();
    second.start();
    first.join();
    second.join();
    assertThat(bothInside.getCount()).isZero();
  }
}
