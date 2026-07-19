package com.trip.adaptive.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.repository.UserRepository;

@Service
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwtService;

  public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwtService) {
    this.users = users;
    this.encoder = encoder;
    this.jwtService = jwtService;
  }

  public AuthResponse login(String email, String password) {
    User user = users.findByEmail(email).orElseThrow(() -> new BusinessException("邮箱或密码错误"));
    if (!encoder.matches(password, user.getPassword())) {
      throw new BusinessException("邮箱或密码错误");
    }
    return response(user);
  }

  public AuthResponse register(String name, String email, String password, String phone) {
    if (users.findByEmail(email).isPresent()) {
      throw new BusinessException("该邮箱已被注册");
    }
    User user = new User(name, email, encoder.encode(password), phone);
    return response(users.save(user));
  }

  public AuthResponse response(User user) {
    return new AuthResponse(
        jwtService.issue(user), user.getId(), user.getName(), user.getEmail(), user.getPhone());
  }

  public record AuthResponse(String token, Long userId, String name, String email, String phone) {}
}
