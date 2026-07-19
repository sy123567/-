package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

  // 查找两个用户之间的好友关系（无论谁发起）
  Optional<Friendship> findByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);

  List<Friendship> findByRequesterIdOrAddresseeId(Long requesterId, Long addresseeId);

  // 查找用户发出的好友申请
  List<Friendship> findByRequesterIdAndStatus(Long requesterId, Enums.FriendshipStatus status);

  // 查找用户收到的好友申请
  List<Friendship> findByAddresseeIdAndStatus(Long addresseeId, Enums.FriendshipStatus status);

  // 查找用户的所有好友关系（已接受）
  @Query(
      "select f from Friendship f where "
          + "(f.requester.id = :userId or f.addressee.id = :userId) "
          + "and f.status = :status")
  List<Friendship> findAllByUserAndStatus(
      @Param("userId") Long userId, @Param("status") Enums.FriendshipStatus status);

  // 检查两个用户之间是否已有好友关系
  boolean existsByRequesterIdAndAddresseeId(Long requesterId, Long addresseeId);
}
