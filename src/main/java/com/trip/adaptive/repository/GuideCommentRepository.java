package com.trip.adaptive.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.GuideComment;

public interface GuideCommentRepository extends JpaRepository<GuideComment, Long> {
  List<GuideComment> findByGuideIdOrderByCreatedAtDesc(Long guideId);

  long countByGuideId(Long guideId);
}
