package com.trip.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.TripExpense;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.TripExpenseRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.service.TripExpenseService;

class TripExpenseServiceTest {

  private static User user(long id) {
    User u = new User("成员" + id, "u" + id + "@example.com", "pw", "");
    ReflectionTestUtils.setField(u, "id", id);
    return u;
  }

  private static Trip tripWithMembers(User... users) {
    TravelGroup group = new TravelGroup();
    for (User u : users) {
      group.getMembers().add(new GroupMember(group, u, Enums.MemberRole.MEMBER));
    }
    Trip trip = new Trip();
    trip.setGroup(group);
    return trip;
  }

  @Test
  void settleSplitsEquallyAndSuggestsMinimalTransfers() {
    User a = user(1);
    User b = user(2);
    User c = user(3);
    Trip trip = tripWithMembers(a, b, c);
    TripRepository trips = mock(TripRepository.class);
    TripExpenseRepository repo = mock(TripExpenseRepository.class);
    when(trips.findById(9L)).thenReturn(Optional.of(trip));
    when(repo.findByTripIdOrderByCreatedAtDesc(9L))
        .thenReturn(
            List.of(
                new TripExpense(trip, a, "住宿", "住宿", BigDecimal.valueOf(300)),
                new TripExpense(trip, b, "晚餐", "餐饮", BigDecimal.valueOf(60))));

    TripExpenseService.Settlement settlement = new TripExpenseService(repo, trips).settle(9L);

    assertEquals(0, settlement.total().compareTo(BigDecimal.valueOf(360)));
    assertEquals(0, settlement.perPerson().compareTo(BigDecimal.valueOf(120)));
    assertEquals(3, settlement.members().size());
    assertEquals(2, settlement.transfers().size());
    BigDecimal transferredToA =
        settlement.transfers().stream()
            .filter(t -> t.toId().equals(1L))
            .map(TripExpenseService.Transfer::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(0, transferredToA.compareTo(BigDecimal.valueOf(180)));
    assertTrue(settlement.transfers().stream().anyMatch(t -> t.fromId().equals(3L)));
  }

  @Test
  void settleWithoutExpensesHasNoTransfers() {
    Trip trip = tripWithMembers(user(1), user(2));
    TripRepository trips = mock(TripRepository.class);
    TripExpenseRepository repo = mock(TripExpenseRepository.class);
    when(trips.findById(3L)).thenReturn(Optional.of(trip));
    when(repo.findByTripIdOrderByCreatedAtDesc(3L)).thenReturn(List.of());

    TripExpenseService.Settlement settlement = new TripExpenseService(repo, trips).settle(3L);

    assertEquals(0, settlement.total().compareTo(BigDecimal.ZERO));
    assertTrue(settlement.transfers().isEmpty());
  }
}
