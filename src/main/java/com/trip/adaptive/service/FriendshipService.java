package com.trip.adaptive.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Friendship;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.FriendshipRepository;
import com.trip.adaptive.repository.UserRepository;

@Service
public class FriendshipService {
  private final FriendshipRepository friendships;
  private final UserRepository users;

  public FriendshipService(FriendshipRepository f, UserRepository u) {
    friendships = f;
    users = u;
  }

  // M04-1 搜索用户
  public List<User> searchUsers(String keyword) {
    if (keyword == null || keyword.trim().isEmpty()) {
      return users.findAll();
    }
    return users.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword, keyword);
  }

  // M04-2 发起好友申请
  @Transactional
  public Friendship sendRequest(Long requesterId, Long addresseeId) {
    if (requesterId.equals(addresseeId)) {
      throw new BusinessException("不能向自己发送好友申请");
    }

    User requester =
        users.findById(requesterId).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    User addressee =
        users.findById(addresseeId).orElseThrow(() -> new ResourceNotFoundException("目标用户不存在"));

    // 检查是否已存在好友关系
    friendships
        .findByRequesterIdAndAddresseeId(requesterId, addresseeId)
        .ifPresent(
            f -> {
              if (f.getStatus() == Enums.FriendshipStatus.ACCEPTED) {
                throw new BusinessException("已经是好友了");
              } else if (f.getStatus() == Enums.FriendshipStatus.PENDING) {
                throw new BusinessException("好友申请已发送，等待对方确认");
              }
            });

    // 检查对方是否已向自己发起申请
    friendships
        .findByRequesterIdAndAddresseeId(addresseeId, requesterId)
        .ifPresent(
            f -> {
              if (f.getStatus() == Enums.FriendshipStatus.ACCEPTED) {
                throw new BusinessException("已经是好友了");
              } else if (f.getStatus() == Enums.FriendshipStatus.PENDING) {
                throw new BusinessException("对方已向你发送好友申请，请直接同意");
              }
            });

    Friendship friendship = new Friendship(requester, addressee);
    return friendships.save(friendship);
  }

  // M04-3 同意好友申请
  @Transactional
  public Friendship acceptRequest(Long friendshipId, Long currentUserId) {
    Friendship friendship =
        friendships
            .findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("好友申请不存在"));

    if (!friendship.getAddressee().getId().equals(currentUserId)) {
      throw new BusinessException("无权处理此好友申请");
    }

    if (friendship.getStatus() != Enums.FriendshipStatus.PENDING) {
      throw new BusinessException("好友申请已处理");
    }

    friendship.setStatus(Enums.FriendshipStatus.ACCEPTED);
    return friendships.save(friendship);
  }

  // M04-3 拒绝好友申请
  @Transactional
  public Friendship rejectRequest(Long friendshipId, Long currentUserId) {
    Friendship friendship =
        friendships
            .findById(friendshipId)
            .orElseThrow(() -> new ResourceNotFoundException("好友申请不存在"));

    if (!friendship.getAddressee().getId().equals(currentUserId)) {
      throw new BusinessException("无权处理此好友申请");
    }

    if (friendship.getStatus() != Enums.FriendshipStatus.PENDING) {
      throw new BusinessException("好友申请已处理");
    }

    friendship.setStatus(Enums.FriendshipStatus.REJECTED);
    return friendships.save(friendship);
  }

  // M04-4 查看好友列表
  public List<User> getFriends(Long userId) {
    List<Friendship> accepted =
        friendships.findAllByUserAndStatus(userId, Enums.FriendshipStatus.ACCEPTED);

    return accepted.stream()
        .map(f -> f.getRequester().getId().equals(userId) ? f.getAddressee() : f.getRequester())
        .collect(Collectors.toList());
  }

  // 查看收到的好友申请（待处理）
  public List<Friendship> getPendingRequests(Long userId) {
    return friendships.findByAddresseeIdAndStatus(userId, Enums.FriendshipStatus.PENDING);
  }

  // 查看发出的好友申请
  public List<Friendship> getSentRequests(Long userId) {
    return friendships.findByRequesterIdAndStatus(userId, Enums.FriendshipStatus.PENDING);
  }

  // M04-5 删除好友
  @Transactional
  public void deleteFriend(Long userId, Long friendId) {
    friendships.findByRequesterIdAndAddresseeId(userId, friendId).ifPresent(friendships::delete);

    friendships.findByRequesterIdAndAddresseeId(friendId, userId).ifPresent(friendships::delete);
  }
}
