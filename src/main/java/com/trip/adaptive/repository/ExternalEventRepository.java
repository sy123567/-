package com.trip.adaptive.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ExternalEvent;

public interface ExternalEventRepository extends JpaRepository<ExternalEvent, Long> {
  List<ExternalEvent> findByEndTimeAfter(LocalDateTime time);
}
