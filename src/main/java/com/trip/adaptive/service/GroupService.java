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
}
