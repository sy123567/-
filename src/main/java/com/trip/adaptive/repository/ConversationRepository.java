package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.Conversation;
import com.trip.adaptive.domain.Enums;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
  Optional<Conversation> findByTypeAndUserAIdAndUserBId(
      Enums.ConversationType type, Long userAId, Long userBId);

  Optional<Conversation> findByTypeAndGroupId(Enums.ConversationType type, Long groupId);

  List<Conversation> findByUserAIdOrUserBId(Long userAId, Long userBId);

  List<Conversation> findByTypeAndGroupIdIn(Enums.ConversationType type, List<Long> groupIds);
}
