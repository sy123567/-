package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 某个节点变更的候选替代地点。
 *
 * <p>候选原来是每次请求现算的（AI 提名 + 地图检索），同一个节点在不同成员、不同刷新之间拿到的列表都不一样，
 * 于是没人能对同一个地点投票。首次生成后固定落库，全组共用同一份候选，投票才有共同的选项。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"node_change_id", "placeName"}))
public class NodeChangeCandidate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private NodeChange nodeChange;

  /** 生成时的排序，保证所有成员看到的顺序一致。 */
  private int position;

  private String placeName;
  private double latitude;
  private double longitude;
  private BigDecimal cost;
  private String source;

  @Column(length = 512)
  private String reason;

  @Column(length = 512)
  private String address;

  private String category;
  private Double rating;
  private Integer reviewCount;

  @Column(length = 512)
  private String image;

  private double distanceKm;
  private boolean indoor;

  /** 亮点标签，落库时用 | 连接。 */
  @Column(length = 512)
  private String highlights;

  public NodeChangeCandidate() {}

  public Long getId() {
    return id;
  }

  public NodeChange getNodeChange() {
    return nodeChange;
  }

  public void setNodeChange(NodeChange v) {
    nodeChange = v;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(int v) {
    position = v;
  }

  public String getPlaceName() {
    return placeName;
  }

  public void setPlaceName(String v) {
    placeName = v;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double v) {
    latitude = v;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double v) {
    longitude = v;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal v) {
    cost = v;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String v) {
    source = v;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String v) {
    reason = v;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String v) {
    address = v;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String v) {
    category = v;
  }

  public Double getRating() {
    return rating;
  }

  public void setRating(Double v) {
    rating = v;
  }

  public Integer getReviewCount() {
    return reviewCount;
  }

  public void setReviewCount(Integer v) {
    reviewCount = v;
  }

  public String getImage() {
    return image;
  }

  public void setImage(String v) {
    image = v;
  }

  public double getDistanceKm() {
    return distanceKm;
  }

  public void setDistanceKm(double v) {
    distanceKm = v;
  }

  public boolean isIndoor() {
    return indoor;
  }

  public void setIndoor(boolean v) {
    indoor = v;
  }

  public List<String> getHighlightList() {
    if (highlights == null || highlights.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    for (String tag : highlights.split("\\|")) {
      if (!tag.isBlank()) out.add(tag);
    }
    return out;
  }

  public void setHighlightList(List<String> tags) {
    highlights = tags == null || tags.isEmpty() ? null : String.join("|", tags);
  }
}
