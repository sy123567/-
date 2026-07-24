package com.trip.adaptive.monitor.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.AlternativePlan;
import com.trip.adaptive.domain.PlanVote;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.dto.Requests.VoteRequest;
import com.trip.adaptive.monitor.service.VotingService;

@RestController
@RequestMapping("/api/plans")
public class VoteController {
  private final VotingService s;

  public VoteController(VotingService s) {
    this.s = s;
  }

  @PostMapping("/{id}/start-voting")
  public AlternativePlan start(@PathVariable Long id) {
    return s.start(id);
  }

  @PostMapping("/{id}/votes")
  public PlanVote vote(@PathVariable Long id, @Valid @RequestBody VoteRequest r) {
    return s.vote(id, r.memberId(), r.choice(), r.comment());
  }

  @GetMapping("/{id}/votes")
  public List<PlanVote> votes(@PathVariable Long id) {
    return s.votes(id);
  }

  @PostMapping("/{id}/tally")
  public Trip tally(@PathVariable Long id) {
    return s.tally(id);
  }

  @PostMapping("/{id}/revert")
  public Trip revert(@PathVariable Long id) {
    return s.revert(id);
  }
}
