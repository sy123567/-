package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.domain.NodeChange;
import com.trip.adaptive.domain.NodeChangeCandidate;
import com.trip.adaptive.monitor.service.NodeCandidateStore;
import com.trip.adaptive.monitor.service.ReplacementCandidateService.Candidate;
import com.trip.adaptive.repository.NodeChangeCandidateRepository;
import com.trip.adaptive.repository.NodeChangeRepository;

class NodeCandidateStoreTest {
  private final NodeChangeCandidateRepository candidates =
      mock(NodeChangeCandidateRepository.class);
  private final NodeChangeRepository changes = mock(NodeChangeRepository.class);
  private final NodeCandidateStore store = new NodeCandidateStore(candidates, changes);
  private final List<NodeChangeCandidate> rows = new ArrayList<>();

  private void wire() {
    NodeChange change = new NodeChange();
    ReflectionTestUtils.setField(change, "id", 11L);
    when(changes.findById(11L)).thenReturn(Optional.of(change));
    when(candidates.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<NodeChangeCandidate> saved = invocation.getArgument(0);
              rows.addAll(saved);
              return saved;
            });
    when(candidates.findByNodeChangeIdOrderByPositionAsc(11L)).thenReturn(rows);
  }

  @Test
  void savedCandidatesAreReturnedInTheSameOrderForEveryMember() {
    wire();
    store.save(
        11L,
        List.of(
            new Candidate(
                "城市博物馆",
                31.2,
                121.4,
                BigDecimal.TEN,
                "nearby",
                "室内可避雨",
                "南京西路 1 号",
                "博物馆",
                4.6,
                120,
                null,
                1.2,
                true,
                List.of("室内", "可讲解")),
            Candidate.of("美术馆", 31.21, 121.41, null, "ai", "室内展览")));

    List<Candidate> first = store.load(11L);
    List<Candidate> second = store.load(11L);
    assertEquals(List.of("城市博物馆", "美术馆"), first.stream().map(Candidate::name).toList());
    assertEquals(first, second);
    assertEquals(List.of("室内", "可讲解"), first.get(0).highlights());
    assertEquals(true, first.get(0).indoor());
  }

  @Test
  void duplicateNamesAreStoredOnce() {
    wire();
    store.save(
        11L,
        List.of(
            Candidate.of("美术馆", 31.21, 121.41, null, "ai", "室内展览"),
            Candidate.of("美术馆", 31.21, 121.41, null, "nearby", "重复提名")));

    assertEquals(1, store.load(11L).size());
  }
}
