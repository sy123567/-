package com.trip.adaptive.monitor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.ExternalEvent;
import com.trip.adaptive.domain.ItineraryNode;

/**
 * 城市路况与公告类外部事件接入。产出施工、交通管制、景区闭馆、大型活动等事件。
 *
 * <p>以节点自身属性做确定性选择：同一节点多次轮询产出稳定一致的事件，配合去重键不会重复入库； 事件分布稀疏（部分节点无事件），贴近真实城市信号的形态。
 */
@Component
public class CityEventProvider {

  /** 各来源前缀统一以 city 开头，便于按来源刷新/清理旧事件。 */
  public static final String SOURCE_PREFIX = "city";

  public List<ExternalEvent> eventsForNode(ItineraryNode node) {
    List<ExternalEvent> out = new ArrayList<>();
    if (node.getLatitude() == null
        || node.getLongitude() == null
        || node.getPlannedStart() == null
        || node.getPlannedEnd() == null
        || node.getPlaceName() == null) {
      return out;
    }
    long seed =
        node.getId() != null
            ? node.getId()
            : Objects.hash(node.getPlaceName(), node.getPlannedStart());
    String place = node.getPlaceName();
    switch (Math.floorMod(seed, 5)) {
      case 0 ->
          out.add(
              build(
                  node,
                  Enums.EventType.ROAD_WORK,
                  "city-roadwork",
                  place + "周边道路施工",
                  "市政道路施工，部分车道封闭，周边通行缓慢，建议预留时间或绕行。",
                  Enums.Severity.MEDIUM));
      case 1 ->
          out.add(
              build(
                  node,
                  Enums.EventType.TRAFFIC_CONTROL,
                  "city-traffic",
                  place + "临时交通管制",
                  "受周边活动影响，部分路段实施临时交通管制，网约车与自驾请留意绕行提示。",
                  Enums.Severity.MEDIUM));
      case 2 -> {
        if (node.getNodeType() == Enums.NodeType.ATTRACTION) {
          out.add(
              build(
                  node,
                  Enums.EventType.ATTRACTION_CLOSURE,
                  "city-venue",
                  place + "开放时间调整",
                  "场馆设备维护，当日开放时间调整，入场前建议确认预约与场次。",
                  Enums.Severity.LOW));
        }
      }
      case 3 ->
          out.add(
              build(
                  node,
                  Enums.EventType.LARGE_EVENT,
                  "city-event",
                  place + "周边大型活动",
                  "周边举办大型活动，人流与车流密集，周边餐饮与停车较为紧张。",
                  Enums.Severity.LOW));
      default -> {
        // 稀疏留白：该节点本轮无城市事件。
      }
    }
    return out;
  }

  private ExternalEvent build(
      ItineraryNode n,
      Enums.EventType type,
      String source,
      String title,
      String description,
      Enums.Severity severity) {
    ExternalEvent e = new ExternalEvent();
    e.setEventType(type);
    e.setTitle(title);
    e.setDescription(description);
    e.setPlaceName(n.getPlaceName());
    e.setLatitude(n.getLatitude());
    e.setLongitude(n.getLongitude());
    e.setRadiusKm(10.0);
    e.setStartTime(n.getPlannedStart());
    e.setEndTime(n.getPlannedEnd());
    e.setSeverity(severity);
    e.setSource(source);
    e.setTripId(n.getTrip().getId());
    e.setTripTitle(n.getTrip().getTitle());
    return e;
  }
}
