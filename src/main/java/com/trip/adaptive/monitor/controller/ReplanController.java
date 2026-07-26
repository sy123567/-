package com.trip.adaptive.monitor.controller;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.monitor.service.ReplanningService;

@RestController
@RequestMapping("/api")
public class ReplanController {
  private final ReplanningService s;

  public ReplanController(ReplanningService s) {
    this.s = s;
  }

  @PostMapping("/trips/{id}/replan")
  public List<?> generate(@PathVariable Long id) {
    return s.generate(id);
  }

  @GetMapping("/trips/{id}/plans")
  public List<?> list(@PathVariable Long id) {
    return s.list(id);
  }

  @GetMapping("/trips/{id}/plans/history")
  public List<?> history(@PathVariable Long id) {
    return s.history(id);
  }

  @GetMapping("/plans/{id}")
  public Object get(@PathVariable Long id) {
    return s.get(id);
  }

  /** 某个节点变更可选的替代地点（已通过预算/体力/饮食/天气/事件校验）。 */
  @GetMapping("/plan-changes/{changeId}/candidates")
  public List<Candidate> candidates(@PathVariable Long changeId) {
    return s.candidatesFor(changeId);
  }

  /** 成员改选替代地点。 */
  @PutMapping("/plan-changes/{changeId}/replacement")
  public Object choose(@PathVariable Long changeId, @RequestBody CandidateSelection body) {
    return s.chooseReplacement(
        changeId, Candidate.of(body.name(), body.lat(), body.lng(), null, "member", ""));
  }

  public record CandidateSelection(@NotBlank String name, double lat, double lng) {}
}
