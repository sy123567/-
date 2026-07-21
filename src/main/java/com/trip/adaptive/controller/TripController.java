package com.trip.adaptive.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.NodeNote;
import com.trip.adaptive.domain.Route;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.service.TripService;

@RestController
@RequestMapping("/api/trips")
public class TripController {
  private final TripService s;

  public TripController(TripService s) {
    this.s = s;
  }

  @GetMapping
  public List<Trip> all(Authentication authentication) {
    return s.all(currentUser(authentication));
  }

  @GetMapping("/{id}")
  public Trip get(@PathVariable Long id) {
    return s.get(id);
  }

  @PostMapping
  public ResponseEntity<Trip> create(@RequestParam Long groupId, @RequestBody Trip t) {
    return ResponseEntity.status(201).body(s.create(t, groupId));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    s.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/nodes")
  public List<ItineraryNode> nodes(@PathVariable Long id) {
    return s.nodes(id);
  }

  @PostMapping("/{id}/nodes")
  public ItineraryNode node(@PathVariable Long id, @RequestBody ItineraryNode n) {
    return s.addNode(id, n);
  }

  @PutMapping("/{id}/nodes/{nodeId}")
  public ItineraryNode updateNode(
      @PathVariable Long id, @PathVariable Long nodeId, @RequestBody ItineraryNode n) {
    return s.updateNode(id, nodeId, n);
  }

  @DeleteMapping("/{id}/nodes/{nodeId}")
  public ResponseEntity<Void> deleteNode(@PathVariable Long id, @PathVariable Long nodeId) {
    s.deleteNode(id, nodeId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{tripId}/nodes/{nodeId}/notes")
  public List<NoteView> notes(@PathVariable Long tripId, @PathVariable Long nodeId) {
    return s.notes(tripId, nodeId).stream().map(TripController::noteView).toList();
  }

  @PostMapping("/{tripId}/nodes/{nodeId}/notes")
  public NoteView addNote(
      @PathVariable Long tripId,
      @PathVariable Long nodeId,
      @RequestBody NoteRequest request,
      Authentication authentication) {
    return noteView(s.addNote(tripId, nodeId, currentUser(authentication), request.content()));
  }

  @GetMapping("/{id}/routes")
  public List<Route> routes(@PathVariable Long id) {
    return s.routes(id);
  }

  private com.trip.adaptive.domain.User currentUser(Authentication authentication) {
    return (com.trip.adaptive.domain.User) authentication.getPrincipal();
  }

  private static NoteView noteView(NodeNote note) {
    return new NoteView(
        note.getId(),
        note.getContent(),
        note.getAuthor().getId(),
        note.getAuthor().getName(),
        note.getCreatedAt());
  }

  public record NoteRequest(String content) {}

  public record NoteView(
      Long id, String content, Long authorId, String authorName, LocalDateTime createdAt) {}
}
