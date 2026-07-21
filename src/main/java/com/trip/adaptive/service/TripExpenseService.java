package com.trip.adaptive.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.TripExpense;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.TripExpenseRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class TripExpenseService {
  private final TripExpenseRepository repo;
  private final TripRepository trips;

  public TripExpenseService(TripExpenseRepository r, TripRepository t) {
    repo = r;
    trips = t;
  }

  public List<TripExpense> list(Long tripId) {
    return repo.findByTripIdOrderByCreatedAtDesc(tripId);
  }

  @Transactional
  public TripExpense create(
      Long tripId, User payer, String label, String category, BigDecimal amount) {
    Trip trip = trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    return repo.save(new TripExpense(trip, payer, label.trim(), category, amount));
  }

  public record MemberShare(Long userId, String name, BigDecimal paid, BigDecimal balance) {}

  public record Transfer(
      Long fromId, String fromName, Long toId, String toName, BigDecimal amount) {}

  public record Settlement(
      BigDecimal total,
      BigDecimal perPerson,
      List<MemberShare> members,
      List<Transfer> transfers) {}

  public Settlement settle(Long tripId) {
    Trip trip = trips.findById(tripId).orElseThrow(() -> new ResourceNotFoundException("行程不存在"));
    Map<Long, User> members = new LinkedHashMap<>();
    if (trip.getGroup() != null) {
      trip.getGroup().getMembers().forEach(m -> members.put(m.getUser().getId(), m.getUser()));
    }
    List<TripExpense> expenses = repo.findByTripIdOrderByCreatedAtDesc(tripId);
    expenses.forEach(e -> members.putIfAbsent(e.getPayer().getId(), e.getPayer()));

    Map<Long, BigDecimal> paid = new LinkedHashMap<>();
    members.keySet().forEach(id -> paid.put(id, BigDecimal.ZERO));
    BigDecimal total = BigDecimal.ZERO;
    for (TripExpense expense : expenses) {
      total = total.add(expense.getAmount());
      paid.merge(expense.getPayer().getId(), expense.getAmount(), BigDecimal::add);
    }
    int count = Math.max(1, members.size());
    BigDecimal perPerson = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

    List<MemberShare> shares = new ArrayList<>();
    Map<Long, BigDecimal> balances = new LinkedHashMap<>();
    for (Map.Entry<Long, User> entry : members.entrySet()) {
      BigDecimal memberPaid = paid.getOrDefault(entry.getKey(), BigDecimal.ZERO);
      BigDecimal balance = memberPaid.subtract(perPerson);
      balances.put(entry.getKey(), balance);
      shares.add(new MemberShare(entry.getKey(), entry.getValue().getName(), memberPaid, balance));
    }
    return new Settlement(total, perPerson, shares, minimizeTransfers(members, balances));
  }

  private static List<Transfer> minimizeTransfers(
      Map<Long, User> members, Map<Long, BigDecimal> balances) {
    List<Map.Entry<Long, BigDecimal>> debtors = new ArrayList<>();
    List<Map.Entry<Long, BigDecimal>> creditors = new ArrayList<>();
    for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
      if (entry.getValue().compareTo(BigDecimal.valueOf(-0.01)) < 0) {
        debtors.add(entry);
      } else if (entry.getValue().compareTo(BigDecimal.valueOf(0.01)) > 0) {
        creditors.add(entry);
      }
    }
    debtors.sort(Map.Entry.comparingByValue());
    creditors.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
    List<Transfer> transfers = new ArrayList<>();
    int di = 0;
    int ci = 0;
    BigDecimal debt = di < debtors.size() ? debtors.get(di).getValue().negate() : BigDecimal.ZERO;
    BigDecimal credit = ci < creditors.size() ? creditors.get(ci).getValue() : BigDecimal.ZERO;
    while (di < debtors.size() && ci < creditors.size()) {
      BigDecimal amount = debt.min(credit);
      if (amount.compareTo(BigDecimal.valueOf(0.01)) >= 0) {
        User from = members.get(debtors.get(di).getKey());
        User to = members.get(creditors.get(ci).getKey());
        transfers.add(
            new Transfer(
                from.getId(),
                from.getName(),
                to.getId(),
                to.getName(),
                amount.setScale(2, RoundingMode.HALF_UP)));
      }
      debt = debt.subtract(amount);
      credit = credit.subtract(amount);
      if (debt.compareTo(BigDecimal.valueOf(0.01)) < 0) {
        di++;
        if (di < debtors.size()) debt = debtors.get(di).getValue().negate();
      }
      if (credit.compareTo(BigDecimal.valueOf(0.01)) < 0) {
        ci++;
        if (ci < creditors.size()) credit = creditors.get(ci).getValue();
      }
    }
    return transfers;
  }
}
