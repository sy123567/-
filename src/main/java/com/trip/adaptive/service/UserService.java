package com.trip.adaptive.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.GroupMember;
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
public class UserService {
  private final UserRepository repo;
  private final PasswordEncoder passwordEncoder;
  private final FriendshipRepository friendships;
  private final GroupMemberRepository members;
  private final MemberConstraintRepository constraints;
  private final TravelGroupRepository groups;

  public UserService(
      UserRepository r,
      PasswordEncoder p,
      FriendshipRepository f,
      GroupMemberRepository m,
      MemberConstraintRepository c,
      TravelGroupRepository g) {
    repo = r;
    passwordEncoder = p;
    friendships = f;
    members = m;
    constraints = c;
    groups = g;
  }

  public User create(User u) {
    return repo.save(u);
  }

  public List<User> all() {
    return repo.findAll();
  }

  public User get(Long id) {
    return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + id));
  }

  public void delete(Long id) {
    repo.delete(get(id));
  }

  public User updateProfile(User current, String name, String email, String phone) {
    repo.findByEmail(email)
        .filter(existing -> !existing.getId().equals(current.getId()))
        .ifPresent(
            existing -> {
              throw new com.trip.adaptive.exception.BusinessException("该邮箱已被其他用户使用");
            });
    current.setName(name);
    current.setEmail(email);
    current.setPhone(phone);
    return repo.save(current);
  }

  public void changePassword(User current, String currentPassword, String newPassword) {
    if (!passwordEncoder.matches(currentPassword, current.getPassword())) {
      throw new com.trip.adaptive.exception.BusinessException("当前密码错误");
    }
    current.setPassword(passwordEncoder.encode(newPassword));
    repo.save(current);
  }

  @Transactional
  public void deleteAccount(User current) {
    List<TravelGroup> ownedGroups = groups.findByOwnerUserId(current.getId());
    if (!ownedGroups.isEmpty()) {
      String names =
          ownedGroups.stream().map(TravelGroup::getName).reduce((a, b) -> a + "、" + b).orElse("");
      throw new BusinessException("请先转移群主或解散以下群组：" + names);
    }

    friendships.deleteAll(
        friendships.findByRequesterIdOrAddresseeId(current.getId(), current.getId()));
    for (GroupMember member : members.findByUserId(current.getId())) {
      constraints.deleteByMemberId(member.getId());
      members.delete(member);
    }
    repo.delete(current);
  }
}
