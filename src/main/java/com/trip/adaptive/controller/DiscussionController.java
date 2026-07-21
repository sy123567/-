package com.trip.adaptive.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.DiscussionPost;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.service.DiscussionService;

@RestController
@RequestMapping("/api")
public class DiscussionController {
  private final DiscussionService s;

  public DiscussionController(DiscussionService s) {
    this.s = s;
  }

  @GetMapping("/trips/{id}/discussions")
  public List<PostView> list(@PathVariable Long id, Authentication authentication) {
    Long me = currentUser(authentication).getId();
    return s.list(id).stream().map(p -> view(p, me)).toList();
  }

  @PostMapping("/trips/{id}/discussions")
  public PostView create(
      @PathVariable Long id, @RequestBody PostRequest request, Authentication authentication) {
    User me = currentUser(authentication);
    return view(s.create(id, me, request.body()), me.getId());
  }

  @PostMapping("/discussions/{postId}/like")
  public PostView like(@PathVariable Long postId, Authentication authentication) {
    User me = currentUser(authentication);
    return view(s.toggleLike(postId, me), me.getId());
  }

  private static PostView view(DiscussionPost p, Long me) {
    return new PostView(
        p.getId(),
        p.getAuthor().getId(),
        p.getAuthor().getName(),
        p.getBody(),
        p.getCreatedAt(),
        p.getLikedBy().size(),
        p.getLikedBy().contains(me));
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }

  public record PostRequest(String body) {}

  public record PostView(
      Long id,
      Long authorId,
      String authorName,
      String body,
      LocalDateTime createdAt,
      int likes,
      boolean likedByMe) {}
}
