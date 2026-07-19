package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.Route;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.RouteRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class TripService {
  private final TripRepository trips;
  private final TravelGroupRepository groups;
  private final GroupMemberRepository members;
  private final ItineraryNodeRepository nodes;
  private final RouteRepository routes;

  public TripService(
      TripRepository t,
      TravelGroupRepository g,
      GroupMemberRepository m,
      ItineraryNodeRepository n,
      RouteRepository r) {
    trips = t;
    groups = g;
    members = m;
    nodes = n;
    routes = r;
  }

  public Trip get(Long id) {
    return trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在: " + id));
  }

  public List<Trip> all() {
    return trips.findAll();
  }

  public List<Trip> all(User user) {
    return members.findByUserId(user.getId()).stream()
        .flatMap(member -> trips.findByGroupId(member.getGroup().getId()).stream())
        .distinct()
        .toList();
  }

  public Trip create(Trip t, Long groupId) {
    t.setGroup(groups.findById(groupId).orElseThrow(() -> new ResourceNotFoundException("群组不存在")));
    return trips.save(t);
  }

  public Trip save(Trip t) {
    return trips.save(t);
  }

  public List<ItineraryNode> nodes(Long id) {
    return nodes.findByTripIdOrderBySequenceOrderAsc(id);
  }

  public ItineraryNode addNode(Long id, ItineraryNode n) {
    n.setTrip(get(id));
    return nodes.save(n);
  }

  public List<Route> routes(Long id) {
    return routes.findByTripId(id);
  }

  public void delete(Long id) {
    trips.delete(get(id));
  }
}
