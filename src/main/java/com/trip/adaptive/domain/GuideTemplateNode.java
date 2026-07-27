package com.trip.adaptive.domain;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 攻略的路线模板节点：发布时从作者那段已完成行程原样抄下来的地点安排。
 *
 * <p>时间存成「第几天 + 时刻」的相对形式，纳用时按读者选的出发日期映射成绝对时间。
 */
@Entity
public class GuideTemplateNode {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private TravelGuide guide;

  private int sequenceOrder;

  /** 第几天，从 1 开始。 */
  private int dayIndex;

  private String name;
  private String placeName;
  private Double latitude;
  private Double longitude;

  @Enumerated(EnumType.STRING)
  private Enums.NodeType nodeType;

  /** HH:mm 形式的当天时刻。 */
  private String startTime;

  private String endTime;
  private BigDecimal cost;

  public GuideTemplateNode() {}

  public Long getId() {
    return id;
  }

  public TravelGuide getGuide() {
    return guide;
  }

  public void setGuide(TravelGuide v) {
    guide = v;
  }

  public int getSequenceOrder() {
    return sequenceOrder;
  }

  public void setSequenceOrder(int v) {
    sequenceOrder = v;
  }

  public int getDayIndex() {
    return dayIndex;
  }

  public void setDayIndex(int v) {
    dayIndex = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getPlaceName() {
    return placeName;
  }

  public void setPlaceName(String v) {
    placeName = v;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double v) {
    latitude = v;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double v) {
    longitude = v;
  }

  public Enums.NodeType getNodeType() {
    return nodeType;
  }

  public void setNodeType(Enums.NodeType v) {
    nodeType = v;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String v) {
    startTime = v;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setEndTime(String v) {
    endTime = v;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal v) {
    cost = v;
  }
}
