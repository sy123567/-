package com.trip.adaptive.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trip.adaptive.domain.Friendship;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.dto.Requests.FriendRequest;
import com.trip.adaptive.service.FriendshipService;

@RestController
@RequestMapping("/api/friends")
public class FriendshipController {
  private final FriendshipService s;

  public FriendshipController(FriendshipService s) {
    this.s = s;
  }

  // M04-1 搜索用户
  @GetMapping("/search")
  public List<User> search(@RequestParam(required = false) String keyword) {
    return s.searchUsers(keyword);
  }

  // M04-2 发起好友申请
  @PostMapping("/request")
  public ResponseEntity<Friendship> sendRequest(
      @RequestParam @NotNull Long requesterId, @Valid @RequestBody FriendRequest r) {
    Friendship f = s.sendRequest(requesterId, r.addresseeId());
    return ResponseEntity.status(201).body(f);
  }

  // M04-3 同意好友申请
  @PostMapping("/requests/{id}/accept")
  public Friendship accept(@PathVariable Long id, @RequestParam @NotNull Long userId) {
    return s.acceptRequest(id, userId);
  }

  // M04-3 拒绝好友申请
  @PostMapping("/requests/{id}/reject")
  public Friendship reject(@PathVariable Long id, @RequestParam @NotNull Long userId) {
    return s.rejectRequest(id, userId);
  }

  // M04-4 查看好友列表
  @GetMapping
  public List<User> getFriends(@RequestParam @NotNull Long userId) {
    return s.getFriends(userId);
  }

  // 查看收到的好友申请（待处理）
  @GetMapping("/requests/incoming")
  public List<Friendship> getIncomingRequests(@RequestParam @NotNull Long userId) {
    return s.getPendingRequests(userId);
  }

  // 查看发出的好友申请
  @GetMapping("/requests/outgoing")
  public List<Friendship> getOutgoingRequests(@RequestParam @NotNull Long userId) {
    return s.getSentRequests(userId);
  }

  // M04-5 删除好友
  @DeleteMapping("/{friendId}")
  public ResponseEntity<Void> deleteFriend(
      @RequestParam @NotNull Long userId, @PathVariable Long friendId) {
    s.deleteFriend(userId, friendId);
    return ResponseEntity.noContent().build();
  }
}
