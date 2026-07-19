package com.trip.adaptive.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.MemberConstraintRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.UserRepository;

@Service
public class GroupService {
  private final TravelGroupRepository groups;
  private final UserRepository users;
  private final GroupMemberRepository members;
  private final MemberConstraintRepository constraints;

  public GroupService(
      TravelGroupRepository g,
      UserRepository u,
      GroupMemberRepository m,
      MemberConstraintRepository c) {
    groups = g;
    users = u;
    members = m;
    constraints = c;
  }

  public TravelGroup create(String name, String description, Long ownerId) {
    User owner = users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    TravelGroup g = groups.save(new TravelGroup(name, description, owner));
    GroupMember m = new GroupMember(g, owner, Enums.MemberRole.OWNER);
    members.save(m);
    return g;
  }

  public TravelGroup get(Long id) {
    return groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("群组不存在: " + id));
  }

  public List<GroupMember> members(Long id) {
    return members.findByGroupId(id);
  }

  public GroupMember addMember(Long gid, Long uid) {
    GroupMember m =
        new GroupMember(
            get(gid),
            users.findById(uid).orElseThrow(() -> new ResourceNotFoundException("用户不存在")),
            Enums.MemberRole.MEMBER);
    return members.save(m);
  }

  @Transactional
  public MemberConstraint setConstraint(Long memberId, MemberConstraint c) {
    GroupMember m =
        members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    c.setMember(m);
    return constraints.save(c);
  }

  @Transactional
  public void removeMember(Long groupId, Long memberId, Long operatorId) {
    TravelGroup group = get(groupId);
    GroupMember member =
        members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    if (!member.getGroup().getId().equals(groupId)) {
      throw new com.trip.adaptive.exception.BusinessException("成员不属于该群组");
    }
    if (member.getRole() == Enums.MemberRole.OWNER) {
      throw new com.trip.adaptive.exception.BusinessException("不能删除群主，请先转移群主");
    }
    members.delete(member);
  }

  @Transactional
  public TravelGroup transferOwner(Long groupId, Long newOwnerId, Long operatorId) {
    TravelGroup group = get(groupId);
    GroupMember newOwner =
        members.findById(newOwnerId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    if (!newOwner.getGroup().getId().equals(groupId)) {
      throw new com.trip.adaptive.exception.BusinessException("成员不属于该群组");
    }
    if (newOwner.getRole() == Enums.MemberRole.OWNER) {
      throw new com.trip.adaptive.exception.BusinessException("该成员已经是群主");
    }
    GroupMember currentOwner =
        members
            .findById(operatorId)
            .orElseThrow(() -> new ResourceNotFoundException("操作人不存在"));
    if (currentOwner.getRole() != Enums.MemberRole.OWNER) {
      throw new com.trip.adaptive.exception.BusinessException("只有群主可以转移群主身份");
    }
    currentOwner.setRole(Enums.MemberRole.MEMBER);
    members.save(currentOwner);
    newOwner.setRole(Enums.MemberRole.OWNER);
    members.save(newOwner);
    group.setOwnerUser(newOwner.getUser());
    return groups.save(group);
  }
}
