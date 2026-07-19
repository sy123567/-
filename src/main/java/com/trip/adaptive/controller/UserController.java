package com.trip.adaptive.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.dto.Requests.UserRequest;
import com.trip.adaptive.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService s;
  private final PasswordEncoder passwordEncoder;

  public UserController(UserService s, PasswordEncoder passwordEncoder) {
    this.s = s;
    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping
  public ResponseEntity<User> create(@Valid @RequestBody UserRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            s.create(
                new User(r.name(), r.email(), passwordEncoder.encode(r.password()), r.phone())));
  }

  @GetMapping
  public List<User> all() {
    return s.all();
  }

  @GetMapping("/{id}")
  public User get(@PathVariable Long id) {
    return s.get(id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    s.delete(id);
    return ResponseEntity.noContent().build();
  }
}
