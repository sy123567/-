package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.UserRepository;

@Service
public class UserService {
  private final UserRepository repo;

  public UserService(UserRepository r) {
    repo = r;
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
}
