package com.trip.adaptive.controller;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;

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

import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.dto.Requests.ConstraintRequest;
import com.trip.adaptive.dto.Requests.GroupRequest;
import com.trip.adaptive.dto.Requests.JoinGroupRequest;
import com.trip.adaptive.dto.Requests.MemberRequest;
import com.trip.adaptive.service.GroupService;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
  private final GroupService s;

  public GroupController(GroupService s) {
    this.s = s;
  }

  @PostMapping
  public ResponseEntity<TravelGroup> create(
      Authentication authentication, @Valid @RequestBody GroupRequest r) {
    return ResponseEntity.status(201)
        .body(s.create(r.name(), r.description(), currentUser(authentication)));
  }

  @PostMapping("/join")
  public TravelGroup join(Authentication authentication, @Valid @RequestBody JoinGroupRequest r) {
    return s.join(r.roomCode(), currentUser(authentication));
  }

  @GetMapping
  public List<TravelGroup> all(Authentication authentication) {
    return s.forUser(((com.trip.adaptive.domain.User) authentication.getPrincipal()).getId());
  }

  @GetMapping("/{id}")
  public TravelGroup get(@PathVariable Long id) {
    return s.get(id);
  }

  @GetMapping("/{id}/members")
  public List<GroupMember> members(@PathVariable Long id) {
    return s.members(id);
  }

  @PostMapping("/{id}/members")
  public GroupMember add(@PathVariable Long id, @Valid @RequestBody MemberRequest r) {
    return s.addMember(id, r.userId());
  }

  @PutMapping("/{id}/members/{memberId}/constraint")
  public MemberConstraint constraint(
      @PathVariable Long memberId, @Valid @RequestBody ConstraintRequest r) {
    MemberConstraint c = new MemberConstraint();
    c.setAvailableFrom(r.availableFrom());
    c.setAvailableTo(r.availableTo());
    c.setMaxBudget(r.maxBudget());
    c.setMustVisitPlaces(r.mustVisitPlaces() == null ? new ArrayList<>() : r.mustVisitPlaces());
    c.setFitnessLevel(r.fitnessLevel());
    c.setDietaryNeeds(r.dietaryNeeds() == null ? new ArrayList<>() : r.dietaryNeeds());
    c.setAccessibilityNeeds(
        r.accessibilityNeeds() == null ? new ArrayList<>() : r.accessibilityNeeds());
    return s.setConstraint(memberId, c);
  }

  @DeleteMapping("/{id}/members/{memberId}")
  public ResponseEntity<Void> remove(
      Authentication authentication, @PathVariable Long id, @PathVariable Long memberId) {
    s.removeMember(id, memberId, currentUser(authentication));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/transfer")
  public TravelGroup transfer(
      Authentication authentication, @PathVariable Long id, @RequestParam Long newOwnerId) {
    return s.transferOwner(id, newOwnerId, currentUser(authentication));
  }

  private com.trip.adaptive.domain.User currentUser(Authentication authentication) {
    return (com.trip.adaptive.domain.User) authentication.getPrincipal();
  }
}
