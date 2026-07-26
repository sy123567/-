package com.trip.adaptive.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.GroupMember;
import com.trip.adaptive.domain.MemberConstraint;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.FriendshipRepository;
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
  private final FriendshipRepository friendships;
  private static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final SecureRandom ROOM_CODE_RANDOM = new SecureRandom();

  public GroupService(
      TravelGroupRepository g,
      UserRepository u,
      GroupMemberRepository m,
      MemberConstraintRepository c,
      FriendshipRepository f) {
    groups = g;
    users = u;
    members = m;
    constraints = c;
    friendships = f;
  }

  @Transactional
  public TravelGroup create(String name, String description, User owner) {
    return create(name, description, owner, null);
  }

  /** 创建小组，并把所选好友一并拉入群。仅与群主为好友关系的用户会被加入。 */
  @Transactional
  public TravelGroup create(String name, String description, User owner, List<Long> memberIds) {
    TravelGroup g = new TravelGroup(name, description, owner);
    g.setRoomCode(newRoomCode());
    g = groups.save(g);
    members.save(new GroupMember(g, owner, Enums.MemberRole.OWNER));
    if (memberIds != null) {
      for (Long uid : memberIds.stream().filter(id -> id != null).distinct().toList()) {
        if (uid.equals(owner.getId())) {
          continue;
        }
        if (!isFriend(owner.getId(), uid)) {
          throw new BusinessException("只能拉自己的好友进群");
        }
        User friend = users.findById(uid).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        members.save(new GroupMember(g, friend, Enums.MemberRole.MEMBER));
      }
    }
    return g;
  }

  private boolean isFriend(Long a, Long b) {
    return friendships
            .findByRequesterIdAndAddresseeId(a, b)
            .filter(f -> f.getStatus() == Enums.FriendshipStatus.ACCEPTED)
            .isPresent()
        || friendships
            .findByRequesterIdAndAddresseeId(b, a)
            .filter(f -> f.getStatus() == Enums.FriendshipStatus.ACCEPTED)
            .isPresent();
  }

  @Transactional
  public TravelGroup join(String roomCode, User currentUser) {
    TravelGroup group =
        groups
            .findByRoomCode(roomCode.trim().toUpperCase())
            .orElseThrow(() -> new BusinessException("房间码不存在"));
    if (members.findByGroupIdAndUserId(group.getId(), currentUser.getId()).isPresent()) {
      throw new BusinessException("你已经是该小组成员");
    }
    members.save(new GroupMember(group, currentUser, Enums.MemberRole.MEMBER));
    return group;
  }

  public TravelGroup get(Long id) {
    return groups.findById(id).orElseThrow(() -> new ResourceNotFoundException("群组不存在: " + id));
  }

  public List<TravelGroup> forUser(Long userId) {
    return groups.findDistinctByMembersUserId(userId);
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
  public void removeMember(Long groupId, Long memberId, User operator) {
    TravelGroup group = get(groupId);
    GroupMember member =
        members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    if (!member.getGroup().getId().equals(groupId)) {
      throw new BusinessException("成员不属于该群组");
    }
    GroupMember operatorMember = operatorMember(groupId, operator);
    if (operatorMember.getRole() != Enums.MemberRole.OWNER) {
      throw new BusinessException("只有群主可以移除成员");
    }
    if (member.getRole() == Enums.MemberRole.OWNER) {
      throw new BusinessException("不能删除群主，请先转移群主");
    }
    members.delete(member);
  }

  @Transactional
  public TravelGroup transferOwner(Long groupId, Long newOwnerId, User operator) {
    TravelGroup group = get(groupId);
    GroupMember newOwner =
        members.findById(newOwnerId).orElseThrow(() -> new ResourceNotFoundException("成员不存在"));
    if (!newOwner.getGroup().getId().equals(groupId)) {
      throw new BusinessException("成员不属于该群组");
    }
    if (newOwner.getRole() == Enums.MemberRole.OWNER) {
      throw new BusinessException("该成员已经是群主");
    }
    GroupMember currentOwner = operatorMember(groupId, operator);
    if (currentOwner.getRole() != Enums.MemberRole.OWNER) {
      throw new BusinessException("只有群主可以转移群主身份");
    }
    currentOwner.setRole(Enums.MemberRole.MEMBER);
    members.save(currentOwner);
    newOwner.setRole(Enums.MemberRole.OWNER);
    members.save(newOwner);
    group.setOwnerUser(newOwner.getUser());
    return groups.save(group);
  }

  private GroupMember operatorMember(Long groupId, User operator) {
    return members
        .findByGroupIdAndUserId(groupId, operator.getId())
        .orElseThrow(() -> new BusinessException("你不是该小组成员"));
  }

  private String newRoomCode() {
    String code;
    do {
      StringBuilder value = new StringBuilder(7);
      for (int i = 0; i < 7; i++) {
        value.append(
            ROOM_CODE_ALPHABET.charAt(ROOM_CODE_RANDOM.nextInt(ROOM_CODE_ALPHABET.length())));
      }
      code = value.toString();
    } while (groups.existsByRoomCode(code));
    return code;
  }
}
