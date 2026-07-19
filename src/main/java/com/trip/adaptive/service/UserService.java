package com.trip.adaptive.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.UserRepository;

@Service
public class UserService {
  private final UserRepository repo;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository r, PasswordEncoder p) {
    repo = r;
    passwordEncoder = p;
  }

  public User create(User u) {
    return repo.save(u);
  }

  public List<User> all() {
    return repo.findAll();
  }

  public User get(Long id) {
    return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + id));
  }

  public void delete(Long id) {
    repo.delete(get(id));
  }

  public User updateProfile(User current, String name, String email, String phone) {
    repo.findByEmail(email)
        .filter(existing -> !existing.getId().equals(current.getId()))
        .ifPresent(
            existing -> {
              throw new com.trip.adaptive.exception.BusinessException("该邮箱已被其他用户使用");
            });
    current.setName(name);
    current.setEmail(email);
    current.setPhone(phone);
    return repo.save(current);
  }

  public void changePassword(User current, String currentPassword, String newPassword) {
    if (!passwordEncoder.matches(currentPassword, current.getPassword())) {
      throw new com.trip.adaptive.exception.BusinessException("当前密码错误");
    }
    current.setPassword(passwordEncoder.encode(newPassword));
    repo.save(current);
  }
}
