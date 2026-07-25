package com.trip.adaptive.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trip.adaptive.domain.ChatMessage;
import com.trip.adaptive.domain.Conversation;
import com.trip.adaptive.domain.Enums;
import com.trip.adaptive.domain.TravelGroup;
import com.trip.adaptive.domain.TravelGuide;
import com.trip.adaptive.domain.User;
import com.trip.adaptive.exception.BusinessException;
import com.trip.adaptive.exception.ResourceNotFoundException;
import com.trip.adaptive.repository.ChatMessageRepository;
import com.trip.adaptive.repository.ConversationRepository;
import com.trip.adaptive.repository.GroupMemberRepository;
import com.trip.adaptive.repository.TravelGroupRepository;
import com.trip.adaptive.repository.TravelGuideRepository;
import com.trip.adaptive.repository.UserRepository;

@Service
public class ChatService {
  private final ConversationRepository conversations;
  private final ChatMessageRepository messages;
  private final UserRepository users;
  private final GroupMemberRepository groupMembers;
  private final TravelGroupRepository groups;
  private final TravelGuideRepository guides;
  private final SimpMessagingTemplate messaging;

  public ChatService(
      ConversationRepository conversations,
      ChatMessageRepository messages,
      UserRepository users,
      GroupMemberRepository groupMembers,
      TravelGroupRepository groups,
      TravelGuideRepository guides,
      SimpMessagingTemplate messaging) {
    this.conversations = conversations;
    this.messages = messages;
    this.users = users;
    this.groupMembers = groupMembers;
    this.groups = groups;
    this.guides = guides;
    this.messaging = messaging;
  }

  /** 获取或创建两名用户之间的私聊会话。 */
  @Transactional
  public Conversation directConversation(User me, Long otherUserId) {
    if (otherUserId.equals(me.getId())) {
      throw new BusinessException("不能和自己发起私聊");
    }
    User other =
        users.findById(otherUserId).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    Long aId = Math.min(me.getId(), other.getId());
    Long bId = Math.max(me.getId(), other.getId());
    return conversations
        .findByTypeAndUserAIdAndUserBId(Enums.ConversationType.DIRECT, aId, bId)
        .orElseGet(
            () -> {
              Conversation c = new Conversation();
              c.setType(Enums.ConversationType.DIRECT);
              c.setUserA(aId.equals(me.getId()) ? me : other);
              c.setUserB(aId.equals(me.getId()) ? other : me);
              return conversations.save(c);
            });
  }

  /** 获取或创建某个小组的群聊会话，仅小组成员可访问。 */
  @Transactional
  public Conversation groupConversation(Long groupId, User me) {
    TravelGroup group =
        groups.findById(groupId).orElseThrow(() -> new ResourceNotFoundException("群组不存在"));
    requireGroupMember(groupId, me);
    return conversations
        .findByTypeAndGroupId(Enums.ConversationType.GROUP, groupId)
        .orElseGet(
            () -> {
              Conversation c = new Conversation();
              c.setType(Enums.ConversationType.GROUP);
              c.setGroup(group);
              return conversations.save(c);
            });
  }

  /** 当前用户可见的全部会话（私聊 + 所在小组群聊），按最近消息倒序。 */
  @Transactional
  public List<ConversationView> conversationsFor(User me) {
    List<Conversation> direct = conversations.findByUserAIdOrUserBId(me.getId(), me.getId());
    List<Long> groupIds =
        groupMembers.findByUserId(me.getId()).stream()
            .map(m -> m.getGroup().getId())
            .distinct()
            .collect(Collectors.toList());
    List<Conversation> group = new ArrayList<>();
    for (Long gid : groupIds) {
      group.add(groupConversation(gid, me));
    }
    List<Conversation> all = new ArrayList<>(direct);
    all.addAll(group);
    return all.stream()
        .map(c -> conversationView(c, me))
        .sorted(
            (x, y) -> {
              LocalDateTime lx = x.lastMessageAt();
              LocalDateTime ly = y.lastMessageAt();
              return ly.compareTo(lx);
            })
        .collect(Collectors.toList());
  }

  @Transactional
  public ConversationView conversationDetail(Long conversationId, User me) {
    Conversation c = get(conversationId);
    requireAccess(c, me);
    return conversationView(c, me);
  }

  @Transactional
  public List<MessageView> messages(Long conversationId, User me) {
    Conversation c = get(conversationId);
    requireAccess(c, me);
    return messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
        .map(this::toView)
        .collect(Collectors.toList());
  }

  @Transactional
  public MessageView send(Long conversationId, User me, String content) {
    Conversation c = get(conversationId);
    requireAccess(c, me);
    if (content == null || content.trim().isEmpty()) {
      throw new BusinessException("消息内容不能为空");
    }
    ChatMessage m = new ChatMessage();
    m.setConversation(c);
    m.setSender(me);
    m.setKind(Enums.MessageKind.TEXT);
    m.setContent(content.trim());
    return persistAndBroadcast(c, m);
  }

  /** 把一篇攻略分享到某个小组群聊。 */
  @Transactional
  public MessageView shareGuideToGroup(Long groupId, User me, Long guideId, String note) {
    return shareGuide(groupConversation(groupId, me), me, guideId, note);
  }

  /** 把一篇攻略分享给某位好友（私聊）。 */
  @Transactional
  public MessageView shareGuideToFriend(Long friendUserId, User me, Long guideId, String note) {
    return shareGuide(directConversation(me, friendUserId), me, guideId, note);
  }

  private MessageView shareGuide(Conversation c, User me, Long guideId, String note) {
    TravelGuide guide =
        guides.findById(guideId).orElseThrow(() -> new ResourceNotFoundException("攻略不存在"));
    ChatMessage m = new ChatMessage();
    m.setConversation(c);
    m.setSender(me);
    m.setKind(Enums.MessageKind.GUIDE);
    m.setContent(note != null && !note.trim().isEmpty() ? note.trim() : "分享了一篇攻略");
    m.setSharedGuideId(guide.getId());
    m.setSharedGuideTitle(guide.getTitle());
    m.setSharedGuideCity(guide.getCity());
    m.setSharedGuideCover(guide.getCover());
    return persistAndBroadcast(c, m);
  }

  private MessageView persistAndBroadcast(Conversation c, ChatMessage m) {
    ChatMessage saved = messages.save(m);
    c.setLastMessageAt(saved.getCreatedAt());
    conversations.save(c);
    MessageView view = toView(saved);
    messaging.convertAndSend("/topic/conversations/" + c.getId(), view);
    return view;
  }

  private Conversation get(Long id) {
    return conversations.findById(id).orElseThrow(() -> new ResourceNotFoundException("会话不存在"));
  }

  private void requireAccess(Conversation c, User me) {
    if (c.getType() == Enums.ConversationType.DIRECT) {
      boolean member =
          (c.getUserA() != null && c.getUserA().getId().equals(me.getId()))
              || (c.getUserB() != null && c.getUserB().getId().equals(me.getId()));
      if (!member) {
        throw new BusinessException("你不是该会话的成员");
      }
    } else {
      requireGroupMember(c.getGroup().getId(), me);
    }
  }

  private void requireGroupMember(Long groupId, User me) {
    if (groupMembers.findByGroupIdAndUserId(groupId, me.getId()).isEmpty()) {
      throw new BusinessException("你不是该小组成员");
    }
  }

  private ConversationView conversationView(Conversation c, User me) {
    String title;
    Long peerId = null;
    Long groupId = null;
    if (c.getType() == Enums.ConversationType.DIRECT) {
      User peer =
          c.getUserA() != null && c.getUserA().getId().equals(me.getId())
              ? c.getUserB()
              : c.getUserA();
      title = peer != null ? peer.getName() : "私聊";
      peerId = peer != null ? peer.getId() : null;
    } else {
      title = c.getGroup() != null ? c.getGroup().getName() : "群聊";
      groupId = c.getGroup() != null ? c.getGroup().getId() : null;
    }
    Optional<ChatMessage> last = messages.findFirstByConversationIdOrderByCreatedAtDesc(c.getId());
    String preview =
        last.map(
                m ->
                    m.getKind() == Enums.MessageKind.GUIDE
                        ? "[攻略] " + (m.getSharedGuideTitle() != null ? m.getSharedGuideTitle() : "")
                        : m.getContent())
            .orElse(null);
    return new ConversationView(
        c.getId(), c.getType().name(), title, peerId, groupId, preview, c.getLastMessageAt());
  }

  private MessageView toView(ChatMessage m) {
    return new MessageView(
        m.getId(),
        m.getConversation().getId(),
        m.getSender().getId(),
        m.getSender().getName(),
        m.getKind().name(),
        m.getContent(),
        m.getSharedGuideId(),
        m.getSharedGuideTitle(),
        m.getSharedGuideCity(),
        m.getSharedGuideCover(),
        m.getCreatedAt());
  }

  public record ConversationView(
      Long id,
      String type,
      String title,
      Long peerId,
      Long groupId,
      String lastMessage,
      LocalDateTime lastMessageAt) {}

  public record MessageView(
      Long id,
      Long conversationId,
      Long senderId,
      String senderName,
      String kind,
      String content,
      Long sharedGuideId,
      String sharedGuideTitle,
      String sharedGuideCity,
      String sharedGuideCover,
      LocalDateTime createdAt) {}
}
