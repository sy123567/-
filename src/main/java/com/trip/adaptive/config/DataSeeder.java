package com.trip.adaptive.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Friendship;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.repository.FriendshipRepository;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.MemberConstraintRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;
import com.trip.adaptive.repository.UserRepository;

@Component
public class DataSeeder implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
  private final UserRepository users;
  private final TravelGroupRepository groups;
  private final GroupMemberRepository members;
  private final MemberConstraintRepository constraints;
  private final FriendshipRepository friendships;
  private final TripRepository trips;
  private final ItineraryNodeRepository nodes;
  private final PasswordEncoder passwordEncoder;

  @Value("${app.seed.enabled:true}")
  boolean enabled;

  public DataSeeder(
      UserRepository u,
      TravelGroupRepository g,
      GroupMemberRepository m,
      MemberConstraintRepository c,
      FriendshipRepository f,
      TripRepository t,
      ItineraryNodeRepository n,
      PasswordEncoder p) {
    users = u;
    groups = g;
    members = m;
    constraints = c;
    friendships = f;
    trips = t;
    nodes = n;
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
    User wangwu =
        users.save(
            new User(
                "王五", "wangwu@example.com", passwordEncoder.encode("password123"), "13800000005"));
    User zhaoliu =
        users.save(
            new User(
                "赵六", "zhaoliu@example.com", passwordEncoder.encode("password123"), "13800000006"));

    Friendship accepted = new Friendship(a, b);
    accepted.setStatus(Enums.FriendshipStatus.ACCEPTED);
    friendships.save(accepted);
    Friendship pending = friendships.save(new Friendship(wangwu, a));

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

    LocalDate tripStart = LocalDate.now().plusDays(2);
    Trip trip = new Trip();
    trip.setGroup(g);
    trip.setTitle("上海春日漫游");
    trip.setStatus(Enums.TripStatus.ONGOING);
    trip.setStartDate(tripStart);
    trip.setEndDate(tripStart.plusDays(2));
    trip.setTotalBudget(new BigDecimal("1800"));
    trip = trips.save(trip);
    saveNode(
        trip,
        "外滩晨光",
        "外滩",
        31.2304,
        121.4737,
        Enums.NodeType.ATTRACTION,
        tripStart.atTime(9, 0),
        tripStart.atTime(11, 0),
        1,
        new BigDecimal("0"));
    saveNode(
        trip,
        "豫园午后",
        "豫园",
        31.2271,
        121.492,
        Enums.NodeType.ATTRACTION,
        tripStart.atTime(13, 0),
        tripStart.atTime(15, 0),
        2,
        new BigDecimal("80"));
    saveNode(
        trip,
        "田子坊漫步",
        "田子坊",
        31.2086,
        121.4692,
        Enums.NodeType.ATTRACTION,
        tripStart.plusDays(1).atTime(10, 0),
        tripStart.plusDays(1).atTime(12, 0),
        3,
        new BigDecimal("60"));

    log.info(
        "seeded users {},{},{},{}; accepted friendship {}, pending friendship {}; group {} members {},{}; trip {} with 3 nodes",
        a.getId(),
        b.getId(),
        wangwu.getId(),
        zhaoliu.getId(),
        accepted.getId(),
        pending.getId(),
        g.getId(),
        ma.getId(),
        mb.getId(),
        trip.getId());
  }

  private void saveNode(
      Trip trip,
      String name,
      String placeName,
      double latitude,
      double longitude,
      Enums.NodeType type,
      LocalDateTime start,
      LocalDateTime end,
      int sequence,
      BigDecimal cost) {
    ItineraryNode node = new ItineraryNode();
    node.setTrip(trip);
    node.setName(name);
    node.setPlaceName(placeName);
    node.setLatitude(latitude);
    node.setLongitude(longitude);
    node.setNodeType(type);
    node.setPlannedStart(start);
    node.setPlannedEnd(end);
    node.setSequenceOrder(sequence);
    node.setCost(cost);
    node.setStatus(Enums.NodeStatus.PLANNED);
    nodes.save(node);
  }
}
