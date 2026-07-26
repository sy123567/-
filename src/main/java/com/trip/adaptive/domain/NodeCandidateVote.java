package com.trip.adaptive.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 节点级投票：成员对「这个受影响节点换成哪里」表态。
 *
 * <p>三种表态：投给某个候选地点（{@code CANDIDATE}，带地点名与坐标）、维持方案里的原安排（{@code KEEP_PLAN}）、弃权（{@code
 * ABSTAIN}）。弃权不计入有效票，但会计入参与人数。
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"node_change_id", "member_id"}))
public class NodeCandidateVote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne(optional = false)
  private NodeChange nodeChange;

  @ManyToOne(optional = false)
  private GroupMember member;

  @Enumerated(EnumType.STRING)
  private Enums.NodeVoteChoice choice;

  private String placeName;
  private Double latitude;
  private Double longitude;
  private String comment;
  private LocalDateTime votedAt;

  public NodeCandidateVote() {
    votedAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public NodeChange getNodeChange() {
    return nodeChange;
  }

  public void setNodeChange(NodeChange v) {
    nodeChange = v;
  }

  public GroupMember getMember() {
    return member;
  }

  public void setMember(GroupMember v) {
    member = v;
  }

  public Enums.NodeVoteChoice getChoice() {
    return choice;
  }

  public void setChoice(Enums.NodeVoteChoice v) {
    choice = v;
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

  public String getComment() {
    return comment;
  }

  public void setComment(String v) {
    comment = v;
  }

  public LocalDateTime getVotedAt() {
    return votedAt;
  }

  public void setVotedAt(LocalDateTime v) {
    votedAt = v;
  }
}
