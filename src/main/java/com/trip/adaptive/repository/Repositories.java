package com.trip.adaptive.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.User;

public final class Repositories {
  private Repositories() {}
}

interface UserRepositoryBase extends JpaRepository<User, Long> {}
