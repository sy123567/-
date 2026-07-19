package com.trip.adaptive.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.dto.Requests.ChangePasswordRequest;
import com.trip.adaptive.dto.Requests.ProfileUpdateRequest;
import com.trip.adaptive.service.AuthService;
import com.trip.adaptive.service.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final UserService userService;

  public AuthController(AuthService authService, UserService userService) {
    this.authService = authService;
    this.userService = userService;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request.email, request.password));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(201)
        .body(authService.register(request.name, request.email, request.password, request.phone));
  }

  @GetMapping("/me")
  public User me(Authentication authentication) {
    return (User) authentication.getPrincipal();
  }

  @PatchMapping("/me")
  public User updateProfile(
      Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) {
    User current = (User) authentication.getPrincipal();
    return userService.updateProfile(current, request.name(), request.email(), request.phone());
  }

  @PostMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
    userService.changePassword(
        (User) authentication.getPrincipal(), request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  public record LoginRequest(
      @NotBlank @Email String email, @NotBlank @Size(min = 6) String password) {}

  public record RegisterRequest(
      @NotBlank String name,
      @NotBlank @Email String email,
      @NotBlank @Size(min = 6) String password,
      String phone) {}
}
