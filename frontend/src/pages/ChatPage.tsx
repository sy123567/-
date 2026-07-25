import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MessageSquare, Send, Users, MapPin } from "lucide-react";
import { api } from "../api/client";
import { useConversationRealtime } from "../api/realtime";
import { getCurrentUser } from "../auth";
import { Card, PageHeader } from "../components/ui";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";

function formatTime(value?: string | null) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  return sameDay
    ? date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

export function ChatPage() {
  const { id } = useParams();
  const conversationId = id ? Number(id) : undefined;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const me = getCurrentUser();

  const conversationsQuery = useQuery({ queryKey: ["conversations"], queryFn: api.conversations });
  const conversations = conversationsQuery.data ?? [];

  const messagesQuery = useQuery({
    queryKey: ["chat-messages", conversationId],
    queryFn: () => api.chatMessages(conversationId as number),
    enabled: conversationId !== undefined,
  });

  const activeConversation = conversations.find((item) => item.id === conversationId);

  const onRealtime = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["chat-messages", conversationId] });
    void queryClient.invalidateQueries({ queryKey: ["conversations"] });
  }, [queryClient, conversationId]);
  useConversationRealtime(conversationId, onRealtime);

  const [draft, setDraft] = useState("");
  const send = useMutation({
    mutationFn: () => api.sendChatMessage(conversationId as number, draft.trim()),
    onSuccess: () => {
      setDraft("");
      void queryClient.invalidateQueries({ queryKey: ["chat-messages", conversationId] });
      void queryClient.invalidateQueries({ queryKey: ["conversations"] });
    },
  });

  const scrollRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight });
  }, [messagesQuery.data]);

  return (
    <>
      <PageHeader eyebrow="MESSAGES" title="聊天" description="和好友私聊、在小组里群聊，随时把攻略分享给旅伴。" />
      <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
        <Card className="p-0">
          <div className="border-b border-slate-100 px-5 py-4">
            <p className="font-display text-lg font-bold text-ink">会话</p>
          </div>
          {conversationsQuery.isLoading ? (
            <div className="p-5"><LoadingState label="正在读取会话…" /></div>
          ) : conversationsQuery.isError ? (
            <div className="p-5"><ErrorState message="无法读取会话" onRetry={() => void conversationsQuery.refetch()} /></div>
          ) : conversations.length === 0 ? (
            <div className="p-5"><EmptyState title="还没有会话" message="到好友页发起私聊，或到小组里开启群聊。" /></div>
          ) : (
            <div className="max-h-[70vh] divide-y divide-slate-100 overflow-y-auto">
              {conversations.map((conversation) => (
                <button
                  key={conversation.id}
                  onClick={() => navigate(`/chat/${conversation.id}`)}
                  className={`flex w-full items-center gap-3 px-5 py-4 text-left transition hover:bg-paper ${conversation.id === conversationId ? "bg-paper" : ""}`}
                >
                  <div className={`grid h-11 w-11 shrink-0 place-items-center rounded-full font-semibold ${conversation.type === "GROUP" ? "bg-sky/15 text-blue-700" : "bg-coral/10 text-coral-deep"}`}>
                    {conversation.type === "GROUP" ? <Users size={18} /> : conversation.title.slice(0, 1)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="truncate font-semibold text-ink">{conversation.title}</p>
                      <span className="shrink-0 text-[11px] text-ink-soft">{formatTime(conversation.lastMessageAt)}</span>
                    </div>
                    <p className="mt-1 truncate text-xs text-ink-soft">{conversation.lastMessage ?? "开始聊天吧"}</p>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>

        <Card className="flex h-[70vh] flex-col p-0">
          {conversationId === undefined ? (
            <div className="grid flex-1 place-items-center text-center text-ink-soft">
              <div><MessageSquare size={32} className="mx-auto mb-3 text-slate-300" /><p className="text-sm">选择左侧的会话开始聊天</p></div>
            </div>
          ) : (
            <>
              <div className="flex items-center gap-3 border-b border-slate-100 px-5 py-4">
                <div className={`grid h-9 w-9 place-items-center rounded-full font-semibold ${activeConversation?.type === "GROUP" ? "bg-sky/15 text-blue-700" : "bg-coral/10 text-coral-deep"}`}>
                  {activeConversation?.type === "GROUP" ? <Users size={16} /> : (activeConversation?.title ?? "").slice(0, 1)}
                </div>
                <div>
                  <p className="font-semibold text-ink">{activeConversation?.title ?? "会话"}</p>
                  <p className="text-[11px] text-ink-soft">{activeConversation?.type === "GROUP" ? "群聊" : "私聊"}</p>
                </div>
              </div>
              <div ref={scrollRef} className="flex-1 space-y-4 overflow-y-auto px-5 py-5">
                {messagesQuery.isLoading ? (
                  <LoadingState label="正在读取消息…" />
                ) : messagesQuery.isError ? (
                  <ErrorState message="无法读取消息" onRetry={() => void messagesQuery.refetch()} />
                ) : (messagesQuery.data ?? []).length === 0 ? (
                  <p className="py-10 text-center text-sm text-ink-soft">还没有消息，发送第一条吧。</p>
                ) : (
                  (messagesQuery.data ?? []).map((message) => {
                    const mine = message.senderId === me?.id;
                    return (
                      <div key={message.id} className={`flex ${mine ? "justify-end" : "justify-start"}`}>
                        <div className={`max-w-[78%] ${mine ? "items-end" : "items-start"}`}>
                          {activeConversation?.type === "GROUP" && !mine && (
                            <p className="mb-1 px-1 text-[11px] text-ink-soft">{message.senderName}</p>
                          )}
                          {message.kind === "GUIDE" ? (
                            <Link
                              to={`/guides/${message.sharedGuideId}`}
                              className="block w-64 overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:-translate-y-0.5"
                            >
                              {message.sharedGuideCover && (
                                <img src={message.sharedGuideCover} alt="" className="h-28 w-full object-cover" />
                              )}
                              <div className="p-3">
                                <p className="text-[10px] font-semibold uppercase tracking-wider text-coral">攻略分享</p>
                                <p className="mt-1 line-clamp-2 font-semibold text-ink">{message.sharedGuideTitle}</p>
                                {message.sharedGuideCity && (
                                  <p className="mt-1 flex items-center gap-1 text-xs text-ink-soft"><MapPin size={12} />{message.sharedGuideCity}</p>
                                )}
                                {message.content && message.content !== "分享了一篇攻略" && (
                                  <p className="mt-2 border-t border-slate-100 pt-2 text-xs text-ink-soft">{message.content}</p>
                                )}
                              </div>
                            </Link>
                          ) : (
                            <div className={`rounded-2xl px-4 py-2.5 text-sm leading-6 ${mine ? "bg-coral text-white" : "bg-paper text-ink"}`}>
                              {message.content}
                            </div>
                          )}
                          <p className={`mt-1 px-1 text-[10px] text-ink-soft ${mine ? "text-right" : "text-left"}`}>{formatTime(message.createdAt)}</p>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
              <form
                className="flex items-center gap-2 border-t border-slate-100 px-4 py-3"
                onSubmit={(event) => {
                  event.preventDefault();
                  if (draft.trim()) send.mutate();
                }}
              >
                <input
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="输入消息…"
                  className="flex-1 rounded-xl border border-slate-200 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-sky/30"
                />
                <button
                  type="submit"
                  disabled={!draft.trim() || send.isPending}
                  className="grid h-10 w-10 place-items-center rounded-xl bg-coral text-white transition hover:bg-coral-deep disabled:opacity-50"
                  aria-label="发送"
                >
                  <Send size={17} />
                </button>
              </form>
            </>
          )}
        </Card>
      </div>
    </>
  );
}
