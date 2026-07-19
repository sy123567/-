package com.trip.adaptive.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.trip.adaptive.domain.GroupMember;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
  List<GroupMember> findByGroupId(Long groupId);

  List<GroupMember> findByUserId(Long userId);

  Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
}
