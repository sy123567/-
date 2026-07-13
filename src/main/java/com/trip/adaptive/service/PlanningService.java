package com.trip.adaptive.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.Route;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.RouteRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class PlanningService {
  private final TravelGroupRepository groups;
  private final TripRepository trips;
  private final ItineraryNodeRepository nodes;
  private final RouteRepository routes;

  public PlanningService(
      TravelGroupRepository g, TripRepository t, ItineraryNodeRepository n, RouteRepository r) {
    groups = g;
    trips = t;
    nodes = n;
    routes = r;
  }

  @Transactional
  public PlanResult generateInitialPlan(Long gid) {
    TravelGroup g = groups.findById(gid).orElseThrow(() -> new ResourceNotFoundException("群组不存在"));
    List<MemberConstraint> cs =
        g.getMembers().stream().map(GroupMember::getConstraint).filter(Objects::nonNull).toList();
    if (cs.isEmpty()) throw new BusinessException("请先设置成员约束");
    LocalDate from =
        cs.stream()
            .map(MemberConstraint::getAvailableFrom)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(LocalDate.now().plusDays(1));
    LocalDate to =
        cs.stream()
            .map(MemberConstraint::getAvailableTo)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(from.plusDays(2));
    if (from.isAfter(to)) throw new BusinessException("成员可用日期没有交集，无法生成共同计划");
    BigDecimal budget =
        cs.stream()
            .map(MemberConstraint::getMaxBudget)
            .filter(Objects::nonNull)
            .min(BigDecimal::compareTo)
            .orElse(new BigDecimal("1000"));
    LinkedHashSet<String> places = new LinkedHashSet<>();
    cs.forEach(c -> places.addAll(c.getMustVisitPlaces()));
    Trip trip = new Trip();
    trip.setGroup(g);
    trip.setTitle("行迹应变初始计划");
    trip.setStartDate(from);
    trip.setEndDate(to);
    trip.setTotalBudget(budget);
    trip.setStatus(Enums.TripStatus.PLANNED);
    trip = trips.save(trip);
    List<ItineraryNode> created = new ArrayList<>();
    int order = 1;
    LocalDateTime cursor = from.atTime(9, 0);
    BigDecimal spent = BigDecimal.ZERO;
    for (String p : places) {
      ItineraryNode n =
          node(
              trip,
              p,
              Enums.NodeType.ATTRACTION,
              cursor,
              cursor.plusHours(2),
              new BigDecimal("80"),
              order++);
      if (spent.add(n.getCost()).compareTo(budget) <= 0) {
        n = nodes.save(n);
        created.add(n);
        trip.getItineraryNodes().add(n);
        spent = spent.add(n.getCost());
      }
      cursor = cursor.plusHours(3);
    }
    for (String p : List.of("本地特色午餐", "舒适住宿")) {
      BigDecimal cost = p.contains("住宿") ? new BigDecimal("180") : new BigDecimal("50");
      if (spent.add(cost).compareTo(budget) <= 0) {
        ItineraryNode n =
            node(
                trip,
                p,
                p.contains("住宿") ? Enums.NodeType.LODGING : Enums.NodeType.MEAL,
                cursor,
                cursor.plusHours(1),
                cost,
                order++);
        n = nodes.save(n);
        created.add(n);
        trip.getItineraryNodes().add(n);
        spent = spent.add(cost);
        cursor = cursor.plusHours(2);
      }
    }
    for (int i = 1; i < created.size(); i++) {
      ItineraryNode a = created.get(i - 1), b = created.get(i);
      Route r = new Route();
      r.setTrip(trip);
      r.setFromNode(a);
      r.setToNode(b);
      r.setTransportMode(Enums.TransportMode.TRANSIT);
      r.setDistanceKm(3.5);
      r.setDurationMinutes(25);
      r.setCost(new BigDecimal("12"));
      r = routes.save(r);
      trip.getRoutes().add(r);
    }
    return new PlanResult(
        trip,
        "已计算成员日期交集 "
            + from
            + " 至 "
            + to
            + "；预算按成员最低上限 "
            + budget
            + " 控制，优先保留必访地点，超预算时舍弃可选餐饮/住宿节点。");
  }

  private ItineraryNode node(
      Trip t,
      String p,
      Enums.NodeType type,
      LocalDateTime s,
      LocalDateTime e,
      BigDecimal c,
      int o) {
    ItineraryNode n = new ItineraryNode();
    n.setTrip(t);
    n.setName(p);
    n.setPlaceName(p);
    n.setNodeType(type);
    n.setPlannedStart(s);
    n.setPlannedEnd(e);
    n.setCost(c);
    n.setSequenceOrder(o);
    n.setLatitude(31.23);
    n.setLongitude(121.47);
    return n;
  }

  public record PlanResult(Trip trip, String explanation) {}
}
