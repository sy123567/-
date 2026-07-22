package com.trip.adaptive.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.GuideComment;
import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.service.TravelGuideService;

@RestController
@RequestMapping("/api/guides")
public class GuideController {
  private final TravelGuideService s;

  public GuideController(TravelGuideService s) {
    this.s = s;
  }

  @GetMapping
  public List<TravelGuide> list() {
    return s.list();
  }

  @GetMapping("/author/{authorId}")
  public List<TravelGuide> byAuthor(@PathVariable Long authorId) {
    return s.byAuthor(authorId);
  }

  @GetMapping("/{id}")
  public TravelGuide get(@PathVariable Long id) {
    return s.get(id);
  }

  @GetMapping("/{id}/comments")
  public List<CommentView> listComments(@PathVariable Long id) {
    return s.listComments(id).stream().map(GuideController::view).toList();
  }

  @PostMapping("/{id}/comments")
  public CommentView addComment(
      @PathVariable Long id, @RequestBody CommentRequest request, Authentication authentication) {
    return view(s.addComment(id, currentUser(authentication), request.body()));
  }

  @PostMapping("/{id}/save")
  public TravelGuide toggleSave(@PathVariable Long id, Authentication authentication) {
    return s.toggleSave(id, currentUser(authentication));
  }

  @PostMapping
  public ResponseEntity<TravelGuide> create(
      @RequestBody TravelGuide payload, Authentication authentication) {
    User me = (User) authentication.getPrincipal();
    return ResponseEntity.status(201).body(s.create(me, payload));
  }

  private static CommentView view(GuideComment comment) {
    return new CommentView(
        comment.getId(),
        comment.getAuthor().getId(),
        comment.getAuthor().getName(),
        comment.getBody(),
        comment.getCreatedAt());
  }

  private User currentUser(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }

  public record CommentRequest(String body) {}

  public record CommentView(
      Long id, Long authorId, String authorName, String body, LocalDateTime createdAt) {}
}
