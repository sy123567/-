package com.trip.adaptive.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ItineraryNode;
import com.trip.adaptive.domain.NodeNote;
import com.trip.adaptive.domain.Route;
import com.trip.adaptive.domain.Trip;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.ItineraryNodeRepository;
import com.trip.adaptive.repository.NodeNoteRepository;
import com.trip.adaptive.repository.RouteRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TripRepository;

@Service
public class TripService {
  private final TripRepository trips;
  private final TravelGroupRepository groups;
  private final GroupMemberRepository members;
  private final ItineraryNodeRepository nodes;
  private final NodeNoteRepository notes;
  private final RouteRepository routes;

  public TripService(
      TripRepository t,
      TravelGroupRepository g,
      GroupMemberRepository m,
      ItineraryNodeRepository n,
      NodeNoteRepository nn,
      RouteRepository r) {
    trips = t;
    groups = g;
    members = m;
    nodes = n;
    notes = nn;
    routes = r;
  }

  public Trip get(Long id) {
    return applyLifecycle(
        trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("行程不存在: " + id)));
  }

  /**
   * 按当前日期自动流转行程状态：今天落在 [start,end] 内→ ONGOING，超过 end → COMPLETED。
   *
   * <p>界面新建的行程默认是 DRAFT，若不自动流转，即使当前时间已在行程期间也会一直停在 DRAFT。 CANCELLED / 已 COMPLETED 为终态，不回改；未开始（今天早于
   * start）保持原状态。
   */
  private Trip applyLifecycle(Trip t) {
    Enums.TripStatus s = t.getStatus();
    if (s == Enums.TripStatus.CANCELLED || s == Enums.TripStatus.COMPLETED) return t;
    if (t.getStartDate() == null || t.getEndDate() == null) return t;
    LocalDate today = LocalDate.now();
    Enums.TripStatus target;
    if (today.isAfter(t.getEndDate())) target = Enums.TripStatus.COMPLETED;
    else if (!today.isBefore(t.getStartDate())) target = Enums.TripStatus.ONGOING;
    else return t;
    if (target != s) {
      t.setStatus(target);
      trips.save(t);
    }
    return t;
  }

  public Trip requireMember(Long id, User user) {
    Trip trip = get(id);
    boolean member =
        members.findByGroupIdAndUserId(trip.getGroup().getId(), user.getId()).isPresent();
    boolean owner =
        trip.getGroup().getOwnerUser() != null
            && trip.getGroup().getOwnerUser().getId().equals(user.getId());
    if (!member && !owner) {
      throw new ResourceNotFoundException("行程不存在");
    }
    return trip;
  }

  public List<Trip> all() {
    return trips.findAll().stream().map(this::applyLifecycle).toList();
  }

  public List<Trip> all(User user) {
    return members.findByUserId(user.getId()).stream()
        .flatMap(member -> trips.findByGroupId(member.getGroup().getId()).stream())
        .distinct()
        .map(this::applyLifecycle)
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
    Trip trip = get(id);
    if (n.getParentId() != null) nodeForTrip(id, n.getParentId());
    n.setTrip(trip);
    return nodes.save(n);
  }

  public ItineraryNode updateNode(Long tripId, Long nodeId, ItineraryNode payload) {
    ItineraryNode node = nodeForTrip(tripId, nodeId);
    if (payload.getName() != null) node.setName(payload.getName());
    if (payload.getPlaceName() != null) node.setPlaceName(payload.getPlaceName());
    if (payload.getLatitude() != null) node.setLatitude(payload.getLatitude());
    if (payload.getLongitude() != null) node.setLongitude(payload.getLongitude());
    if (payload.getNodeType() != null) node.setNodeType(payload.getNodeType());
    if (payload.getPlannedStart() != null) node.setPlannedStart(payload.getPlannedStart());
    if (payload.getPlannedEnd() != null) node.setPlannedEnd(payload.getPlannedEnd());
    if (payload.getCost() != null) node.setCost(payload.getCost());
    if (payload.getSequenceOrder() > 0) node.setSequenceOrder(payload.getSequenceOrder());
    if (payload.getParentId() != null) node.setParentId(payload.getParentId());
    return nodes.save(node);
  }

  public void deleteNode(Long tripId, Long nodeId) {
    nodes.delete(nodeForTrip(tripId, nodeId));
  }

  public List<NodeNote> notes(Long tripId, Long nodeId) {
    nodeForTrip(tripId, nodeId);
    return notes.findByNodeIdOrderByCreatedAtAsc(nodeId);
  }

  public NodeNote addNote(Long tripId, Long nodeId, User author, String content) {
    ItineraryNode node = nodeForTrip(tripId, nodeId);
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("备注内容不能为空");
    }
    NodeNote note = new NodeNote();
    note.setNode(node);
    note.setAuthor(author);
    note.setContent(content.trim());
    return notes.save(note);
  }

  private ItineraryNode nodeForTrip(Long tripId, Long nodeId) {
    ItineraryNode node =
        nodes.findById(nodeId).orElseThrow(() -> new ResourceNotFoundException("节点不存在: " + nodeId));
    if (node.getTrip() == null
        || node.getTrip().getId() == null
        || !node.getTrip().getId().equals(tripId)) {
      throw new ResourceNotFoundException("节点不属于该行程");
    }
    return node;
  }

  public List<Route> routes(Long id) {
    return routes.findByTripId(id);
  }

  public void delete(Long id) {
    trips.delete(get(id));
  }
}
