package com.trip.adaptive.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.repository.UserRepository;

@Service
public class AuthService {
  private final UserRepository users;
  private final BCryptPasswordEncoder encoder;

  public AuthService(UserRepository users) {
    this.users = users;
    this.encoder = new BCryptPasswordEncoder();
  }

  public LoginResult login(String email, String password) {
    User user = users.findByEmail(email)
        .orElseThrow(() -> new BusinessException("邮箱或密码错误"));
    if (!encoder.matches(password, user.getPassword())) {
      throw new BusinessException("邮箱或密码错误");
    }
    return new LoginResult(user.getId(), user.getName(), user.getEmail(), user.getPhone());
  }

  public User register(String name, String email, String password, String phone) {
    if (users.findByEmail(email).isPresent()) {
      throw new BusinessException("该邮箱已被注册");
    }
    User user = new User(name, email, encoder.encode(password), phone);
    return users.save(user);
  }

  public record LoginResult(Long userId, String name, String email, String phone) {}
}
