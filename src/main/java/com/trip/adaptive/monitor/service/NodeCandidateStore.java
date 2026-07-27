package com.trip.adaptive.monitor.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.NodeChangeCandidate;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.repository.NodeChangeCandidateRepository;
import com.trip.adaptive.repository.NodeChangeRepository;

/**
 * 节点变更的候选地点存取。
 *
 * <p>读写都用独立事务：候选是多名成员并发打开同一个节点时第一次生成的，独立事务能拿到最新快照， 让后到的成员直接读到已生成的那一份，而不是各算各的。
 */
@Service
public class NodeCandidateStore {
  private final NodeChangeCandidateRepository candidates;
  private final NodeChangeRepository changes;

  public NodeCandidateStore(
      NodeChangeCandidateRepository candidates, NodeChangeRepository changes) {
    this.candidates = candidates;
    this.changes = changes;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public List<Candidate> load(Long changeId) {
    return candidates.findByNodeChangeIdOrderByPositionAsc(changeId).stream()
        .map(NodeCandidateStore::toCandidate)
        .toList();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(Long changeId, List<Candidate> fresh) {
    NodeChange change = changes.findById(changeId).orElse(null);
    if (change == null || fresh.isEmpty()) return;
    List<NodeChangeCandidate> rows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int position = 0;
    for (Candidate candidate : fresh) {
      if (candidate.name() == null || !seen.add(candidate.name())) continue;
      rows.add(toRow(change, candidate, position++));
    }
    candidates.saveAll(rows);
  }

  private static NodeChangeCandidate toRow(NodeChange change, Candidate candidate, int position) {
    NodeChangeCandidate row = new NodeChangeCandidate();
    row.setNodeChange(change);
    row.setPosition(position);
    row.setPlaceName(candidate.name());
    row.setLatitude(candidate.lat());
    row.setLongitude(candidate.lng());
    row.setCost(candidate.cost());
    row.setSource(candidate.source());
    row.setReason(candidate.reason());
    row.setAddress(candidate.address());
    row.setCategory(candidate.category());
    row.setRating(candidate.rating());
    row.setReviewCount(candidate.reviewCount());
    row.setImage(candidate.image());
    row.setDistanceKm(candidate.distanceKm());
    row.setIndoor(candidate.indoor());
    row.setHighlightList(candidate.highlights());
    return row;
  }

  private static Candidate toCandidate(NodeChangeCandidate row) {
    return new Candidate(
        row.getPlaceName(),
        row.getLatitude(),
        row.getLongitude(),
        row.getCost(),
        row.getSource(),
        row.getReason(),
        row.getAddress(),
        row.getCategory(),
        row.getRating(),
        row.getReviewCount(),
        row.getImage(),
        row.getDistanceKm(),
        row.isIndoor(),
        row.getHighlightList());
  }
}
