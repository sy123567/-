package com.trip.adaptive.monitor.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.monitor.service.NodeCandidateVotingService;
import com.trip.adaptive.monitor.service.NodeCandidateVotingService.NodeVoteTally;

/** 节点级投票：成员共同决定某个受影响节点换到哪里。 */
@RestController
@RequestMapping("/api/plan-changes/{changeId}/node-votes")
public class NodeVoteController {
  private final NodeCandidateVotingService s;

  public NodeVoteController(NodeCandidateVotingService s) {
    this.s = s;
  }

  @GetMapping
  public NodeVoteTally summary(@PathVariable Long changeId) {
    return s.summary(changeId);
  }

  @PostMapping
  public NodeVoteTally vote(@PathVariable Long changeId, @Valid @RequestBody NodeVoteRequest r) {
    return s.vote(changeId, r.memberId(), r.choice(), r.placeName(), r.lat(), r.lng(), r.comment());
  }

  /** 提前计票（例如群主想在全员投完前定案）。 */
  @PostMapping("/tally")
  public NodeVoteTally tally(@PathVariable Long changeId) {
    return s.tally(changeId);
  }

  public record NodeVoteRequest(
      @NotNull Long memberId,
      @NotNull Enums.NodeVoteChoice choice,
      String placeName,
      Double lat,
      Double lng,
      String comment) {}
}
