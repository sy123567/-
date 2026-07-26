package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.NodeCandidateVote;

public interface NodeCandidateVoteRepository extends JpaRepository<NodeCandidateVote, Long> {
  List<NodeCandidateVote> findByNodeChangeIdOrderByVotedAtAsc(Long nodeChangeId);

  Optional<NodeCandidateVote> findByNodeChangeIdAndMemberId(Long nodeChangeId, Long memberId);

  List<NodeCandidateVote> findByMemberId(Long memberId);
}
