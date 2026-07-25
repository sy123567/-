package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

  Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
