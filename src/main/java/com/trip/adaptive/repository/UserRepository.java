package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  // M04-1 搜索用户（按名称或邮箱模糊匹配）
  List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);

  // 按名称模糊搜索
  List<User> findByNameContainingIgnoreCase(String name);
}
