package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class ChangeLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private Trip trip;

  private String description;

  /** 变更类型，供前端直接区分“已应用 / 已回退”，无需再从描述文本里猜。 */
  @Enumerated(EnumType.STRING)
  private Enums.ChangeLogType type = Enums.ChangeLogType.APPLIED;

  /** 逐节点的变更明细（每行一条），形如“外滩晨光：外滩 → 田子坊·换地点”。 */
  @Column(length = 2000)
  private String details;

  private BigDecimal extraCost;
  private LocalDateTime refundDeadline;
  @ManyToOne private AlternativePlan relatedPlan;
  private LocalDateTime createdAt;

  public ChangeLog() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip v) {
    trip = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public BigDecimal getExtraCost() {
    return extraCost;
  }

  public void setExtraCost(BigDecimal v) {
    extraCost = v;
  }

  public LocalDateTime getRefundDeadline() {
    return refundDeadline;
  }

  public void setRefundDeadline(LocalDateTime v) {
    refundDeadline = v;
  }

  public Enums.ChangeLogType getType() {
    return type;
  }

  public void setType(Enums.ChangeLogType v) {
    type = v;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String v) {
    details = v;
  }

  public AlternativePlan getRelatedPlan() {
    return relatedPlan;
  }

  public void setRelatedPlan(AlternativePlan v) {
    relatedPlan = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
