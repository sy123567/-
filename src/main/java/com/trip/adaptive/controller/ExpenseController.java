package com.trip.adaptive.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.TripExpense;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.service.TripExpenseService;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class ExpenseController {
  private final TripExpenseService s;

  public ExpenseController(TripExpenseService s) {
    this.s = s;
  }

  @GetMapping("/expenses")
  public List<ExpenseView> list(@PathVariable Long tripId) {
    return s.list(tripId).stream().map(ExpenseController::view).toList();
  }

  @PostMapping("/expenses")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseView create(
      @PathVariable Long tripId,
      @RequestBody ExpenseRequest request,
      Authentication authentication) {
    User me = (User) authentication.getPrincipal();
    return view(s.create(tripId, me, request.label(), request.category(), request.amount()));
  }

  @GetMapping("/settlement")
  public TripExpenseService.Settlement settlement(@PathVariable Long tripId) {
    return s.settle(tripId);
  }

  private static ExpenseView view(TripExpense e) {
    return new ExpenseView(
        e.getId(),
        e.getLabel(),
        e.getCategory(),
        e.getAmount(),
        e.getPayer().getId(),
        e.getPayer().getName(),
        e.getCreatedAt());
  }

  public record ExpenseRequest(String label, String category, BigDecimal amount) {}

  public record ExpenseView(
      Long id,
      String label,
      String category,
      BigDecimal amount,
      Long payerId,
      String payerName,
      LocalDateTime createdAt) {}
}
