package com.trip.adaptive.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.service.ChatService;
import com.trip.adaptive.service.ChatService.ConversationView;
import com.trip.adaptive.service.ChatService.MessageView;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
  private final ChatService s;

  public ChatController(ChatService s) {
    this.s = s;
  }

  @GetMapping("/conversations")
  public List<ConversationView> conversations(Authentication authentication) {
    return s.conversationsFor(currentUser(authentication));
  }

  @GetMapping("/conversations/{id}")
  public ConversationView conversation(@PathVariable Long id, Authentication authentication) {
    return s.conversationDetail(id, currentUser(authentication));
  }

  @GetMapping("/conversations/{id}/messages")
  public List<MessageView> messages(@PathVariable Long id, Authentication authentication) {
    return s.messages(id, currentUser(authentication));
  }

  @PostMapping("/conversations/{id}/messages")
  public MessageView send(
      @PathVariable Long id, @RequestBody SendRequest request, Authentication authentication) {
    return s.send(id, currentUser(authentication), request.content());
  }

  @PostMapping("/direct/{userId}")
  public ConversationView direct(@PathVariable Long userId, Authentication authentication) {
    User me = currentUser(authentication);
    return s.conversationDetail(s.directConversation(me, userId).getId(), me);
  }

  @PostMapping("/groups/{groupId}")
  public ConversationView group(@PathVariable Long groupId, Authentication authentication) {
    User me = currentUser(authentication);
    return s.conversationDetail(s.groupConversation(groupId, me).getId(), me);
  }

  @PostMapping("/groups/{groupId}/share-guide/{guideId}")
  public MessageView shareGuideToGroup(
      @PathVariable Long groupId,
      @PathVariable Long guideId,
      @RequestBody(required = false) ShareRequest request,
      Authentication authentication) {
    return s.shareGuideToGroup(
        groupId, currentUser(authentication), guideId, request != null ? request.note() : null);
  }

  @PostMapping("/direct/{userId}/share-guide/{guideId}")
  public MessageView shareGuideToFriend(
      @PathVariable Long userId,
      @PathVariable Long guideId,
      @RequestBody(required = false) ShareRequest request,
      Authentication authentication) {
    return s.shareGuideToFriend(
        userId, currentUser(authentication), guideId, request != null ? request.note() : null);
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }

  public record SendRequest(String content) {}

  public record ShareRequest(String note) {}
}
