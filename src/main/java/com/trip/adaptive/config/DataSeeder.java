package com.trip.adaptive.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.MemberConstraintRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
  private final UserRepository users;
  private final TravelGroupRepository groups;
  private final GroupMemberRepository members;
  private final MemberConstraintRepository constraints;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.seed.enabled:true}")
  boolean enabled;

  public DataSeeder(
      UserRepository u,
      TravelGroupRepository g,
      GroupMemberRepository m,
      MemberConstraintRepository c,
      PasswordEncoder p) {
    users = u;
    groups = g;
    members = m;
    constraints = c;
    passwordEncoder = p;
  }

  public void run(String... args) {
    if (!enabled || users.count() > 0) return;
    User a =
        users.save(
            new User(
                "张三",
                "zhangsan@example.com",
                passwordEncoder.encode("password123"),
                "13800000001"));
    User b =
        users.save(
            new User(
                "李四", "lisi@example.com", passwordEncoder.encode("password123"), "13800000002"));
    TravelGroup g = groups.save(new TravelGroup("上海周末小队", "演示群组", a));
    GroupMember ma = members.save(new GroupMember(g, a, Enums.MemberRole.OWNER));
    GroupMember mb = members.save(new GroupMember(g, b, Enums.MemberRole.MEMBER));
    for (GroupMember m : List.of(ma, mb)) {
      MemberConstraint c =
          new MemberConstraint(
              m, LocalDate.now().plusDays(7), LocalDate.now().plusDays(10), new BigDecimal("500"));
      c.setMustVisitPlaces(List.of("外滩", "豫园"));
      c.setFitnessLevel(Enums.FitnessLevel.MEDIUM);
      constraints.save(c);
    }
    log.info(
        "seeded users {},{}, group {} members {},{}",
        a.getId(),
        b.getId(),
        g.getId(),
        ma.getId(),
        mb.getId());
  }
}
