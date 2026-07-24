import { useEffect, useState } from "react";
import { ArrowRight, Bus, Car, Check, ChevronDown, ChevronRight, CircleAlert, Copy, Footprints, Heart, Plus, Search, Send, Shield, Sparkles, ThumbsUp, Trash2, UserMinus, UserPlus, Users } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type AiPlace, type MapPlace, type VoteChoice, type WeatherPreview } from "../api/client";
import { getCitySuggestions, getPlaceDetail, getPlaceImage, suggestPlaces, type SuggestedPlace } from "../mocks/places";
import { Badge, BoardingPassCard, Button, Card, EventIcon, Input, PageHeader, RiskGauge, RouteTrail } from "../components/ui";
import { ImageFallback, Modal, Toast } from "../components/pass2";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import { BaiduMap } from "../components/BaiduMap";
import { MapNodeDetailSheet } from "../components/MapNodeDetailSheet";
import { TripSwitcher } from "../components/TripSwitcher";
import { getCurrentUser, signOut, updateCurrentUser } from "../auth";
import { useTripScope } from "../hooks/useTripScope";
import type { EventType, ExternalEvent, ImpactAssessment, ItineraryNode, MemberConstraint, NodeType, Severity, TravelGuide, Trip } from "../types";

function useToast() {
  const [message, setMessage] = useState("");
  return { toast: message ? <Toast message={message} onClose={() => setMessage("")} /> : null, show: setMessage };
}

function buildEventsByNode(impacts: ImpactAssessment[] | undefined): Record<number, ExternalEvent[]> {
  return (impacts ?? []).reduce<Record<number, ExternalEvent[]>>((map, impact) => {
    if (impact.affectedNode?.id && impact.event) {
      map[impact.affectedNode.id] = [...(map[impact.affectedNode.id] ?? []), impact.event];
    }
    return map;
  }, {});
}

export function GroupsPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [joinOpen, setJoinOpen] = useState(false);
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const groupsQuery = useQuery({ queryKey: ["groups"], queryFn: api.groups });
  const groups = groupsQuery.data ?? [];
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ["groups"] });
  return (
    <>
      <PageHeader
        eyebrow="YOUR CREW"
        title="我的小组"
        description="和旅伴共享约束、一起确认每一次路线变化。"
        action={<div className="flex gap-2"><Button variant="ghost" onClick={() => setJoinOpen(true)}><UserPlus size={16} className="mr-2 inline" />加入小组</Button><Button onClick={() => setCreateOpen(true)}><Plus size={16} className="mr-2 inline" />创建小组</Button></div>}
      />
      {groupsQuery.isLoading && <LoadingState label="正在读取小组…" />}
      {groupsQuery.isError && <ErrorState message={groupsQuery.error instanceof Error ? groupsQuery.error.message : "无法读取小组"} onRetry={() => void groupsQuery.refetch()} />}
      {!groupsQuery.isLoading && !groupsQuery.isError && groups.length === 0 && <EmptyState title="还没有加入小组" message="创建一个小组，或输入旅伴分享的房间码加入。" />}
      <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
        {groups.map((group, index) => (
          <Card key={group.id} className={`relative overflow-hidden p-6 ${index === 0 ? "bg-ink text-white" : ""}`}>
            {index === 0 && <div className="absolute -right-12 -top-12 h-40 w-40 rounded-full border-[20px] border-white/5" />}
            <p className={`eyebrow ${index === 0 ? "text-coral" : ""}`}>TRAVEL ROOM</p>
            <h2 className="relative mt-3 font-display text-2xl font-bold">{group.name}</h2>
            <p className={`relative mt-2 text-sm leading-6 ${index === 0 ? "text-white/55" : "text-ink-soft"}`}>{group.description || "和旅伴一起规划下一段路。"}</p>
            <div className={`relative mt-7 flex items-center justify-between border-t pt-4 ${index === 0 ? "border-white/10" : "border-slate-100"}`}>
              <span className={`flex items-center gap-2 text-sm ${index === 0 ? "text-white/65" : "text-ink-soft"}`}><Users size={16} />{group.members?.length ?? 0} 位成员</span>
              <span className={`font-mono text-xs ${index === 0 ? "text-coral" : "text-sky"}`}>{group.roomCode}</span>
            </div>
            <Link to={`/groups/${group.id}`} className={`relative mt-5 flex items-center gap-1 text-sm font-semibold ${index === 0 ? "text-white hover:text-coral" : "text-sky"}`}>查看小组详情<ChevronRight size={15} /></Link>
          </Card>
        ))}
      </div>
      <Modal open={createOpen} title="创建一个新小组" onClose={() => setCreateOpen(false)}><CreateGroupForm onDone={(group) => { setCreateOpen(false); refresh(); show(`小组已创建，房间码 ${group.roomCode}`); }} /></Modal>
      <Modal open={joinOpen} title="加入旅伴的小组" onClose={() => setJoinOpen(false)}><JoinForm onDone={(group) => { setJoinOpen(false); refresh(); show(`已加入 ${group.name}`); }} /></Modal>
      {toast}
    </>
  );
}

function CreateGroupForm({ onDone }: { onDone: (group: Awaited<ReturnType<typeof api.createGroup>>) => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");
  const mutation = useMutation({ mutationFn: () => api.createGroup(name.trim(), description.trim()), onSuccess: onDone, onError: (e) => setError(e instanceof Error ? e.message : "创建小组失败") });
  return <form onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }} className="space-y-4"><Input placeholder="小组名称，例如：西湖边慢慢走" value={name} onChange={(event) => setName(event.target.value)} required /><Input placeholder="一句话描述（可选）" value={description} onChange={(event) => setDescription(event.target.value)} />{error && <p className="text-sm text-coral" role="alert">{error}</p>}<Button className="w-full" disabled={!name.trim() || mutation.isPending}>{mutation.isPending ? "创建中…" : "生成房间码"}</Button></form>;
}

function JoinForm({ onDone }: { onDone: (group: Awaited<ReturnType<typeof api.joinGroup>>) => void }) {
  const [roomCode, setRoomCode] = useState("");
  const [error, setError] = useState("");
  const mutation = useMutation({ mutationFn: () => api.joinGroup(roomCode.trim()), onSuccess: onDone, onError: (e) => setError(e instanceof Error ? e.message : "加入小组失败") });
  return <form onSubmit={(event) => { event.preventDefault(); mutation.mutate(); }} className="space-y-4"><p className="text-sm leading-6 text-ink-soft">输入旅伴分享的房间码即可加入小组。</p><Input className="font-mono uppercase tracking-[0.2em]" placeholder="例如 SH24-7K" value={roomCode} onChange={(event) => setRoomCode(event.target.value.toUpperCase())} maxLength={7} required />{error && <p className="text-sm text-coral" role="alert">{error}</p>}<Button className="w-full" disabled={!roomCode.trim() || mutation.isPending}>{mutation.isPending ? "加入中…" : "加入小组"}</Button></form>;
}

export function GroupDetailPage() {
  const { id } = useParams();
  const groupId = Number(id);
  const { toast, show } = useToast();
  const [transferOpen, setTransferOpen] = useState(false);
  const [newOwnerId, setNewOwnerId] = useState<number | null>(null);
  const queryClient = useQueryClient();
  const groupQuery = useQuery({ queryKey: ["group", groupId], queryFn: () => api.group(groupId), enabled: Number.isFinite(groupId) });
  const membersQuery = useQuery({ queryKey: ["group", groupId, "members"], queryFn: () => api.members(groupId), enabled: Number.isFinite(groupId) });
  const members = membersQuery.data ?? [];
  const mutation = useMutation({
    mutationFn: async (request: { type: "remove" | "transfer"; memberId: number }) => {
      if (request.type === "remove") await api.removeMember(groupId, request.memberId);
      else await api.transferOwner(groupId, request.memberId);
    },
    onSuccess: (_, request) => {
      void queryClient.invalidateQueries({ queryKey: ["group", groupId] });
      void queryClient.invalidateQueries({ queryKey: ["group", groupId, "members"] });
      setTransferOpen(false);
      setNewOwnerId(null);
      show(request.type === "remove" ? "成员已移除" : "群主已转移");
    },
    onError: (error) => show(error instanceof Error ? error.message : "操作失败"),
  });
  if (!Number.isFinite(groupId)) return <ErrorState message="无效的小组地址" onRetry={() => undefined} />;
  if (groupQuery.isLoading || membersQuery.isLoading) return <LoadingState label="正在加载小组…" />;
  if (groupQuery.isError || membersQuery.isError || !groupQuery.data) return <ErrorState message="无法读取小组信息" onRetry={() => { void groupQuery.refetch(); void membersQuery.refetch(); }} />;
  const group = groupQuery.data;
  const transferableMembers = members.filter((member) => member.role !== "OWNER");
  return (
    <>
      <PageHeader
        eyebrow={`GROUP / ${group.roomCode}`}
        title={group.name}
        description={`${members.length} 位成员 · 房间码 ${group.roomCode}`}
        action={<div className="flex gap-2"><Button variant="ghost" onClick={() => setTransferOpen(true)}><Users size={16} className="mr-2 inline" />转移群主</Button><Button variant="ghost" onClick={() => { void navigator.clipboard?.writeText(group.roomCode); show("房间码已复制"); }}><Copy size={16} className="mr-2 inline" />复制房间码</Button></div>}
      />
      <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <Card className="p-6">
          <div className="flex items-center justify-between"><div><p className="eyebrow">TRAVEL CREW</p><h2 className="mt-2 font-display text-xl font-bold">成员列表</h2></div><Badge tone="mint">{members.length} 位成员</Badge></div>
          <div className="mt-6 divide-y divide-slate-100">{members.map((member) => <div key={member.id} className="flex items-center justify-between gap-4 py-4 first:pt-0"><div className="flex min-w-0 items-center gap-3"><div className="grid h-11 w-11 shrink-0 place-items-center rounded-full bg-coral/10 font-display text-lg font-bold text-coral">{member.user.name.slice(0, 1)}</div><div><p className="font-semibold text-ink">{member.user.name} {member.role === "OWNER" && <Badge tone="coral">群主</Badge>}</p><p className="mt-1 truncate text-xs text-ink-soft">{member.user.email}</p></div></div><div className="flex shrink-0 gap-2"><Link to={`/groups/${groupId}/constraints/${member.id}`} className="rounded-lg px-3 py-2 text-xs font-semibold text-sky hover:bg-sky/5">成员约束</Link>{member.role === "MEMBER" && <button onClick={() => mutation.mutate({ type: "remove", memberId: member.id })} disabled={mutation.isPending} className="rounded-lg p-2 text-ink-soft hover:bg-coral/5 hover:text-coral" aria-label={`移除${member.user.name}`}><UserMinus size={16} /></button>}</div></div>)}</div>
        </Card>
        <Card className="p-6"><p className="eyebrow">GROUP SETTINGS</p><h2 className="mt-2 font-display text-xl font-bold">小组协作规则</h2><p className="mt-3 text-sm leading-6 text-ink-soft">{group.description || "和旅伴一起规划下一段路。"}</p><div className="mt-6 flex items-start gap-3 rounded-xl bg-paper p-4"><Shield size={18} className="mt-0.5 text-mint" /><div><p className="text-sm font-semibold text-ink">房间码</p><p className="mt-1 font-mono text-xs text-ink-soft">{group.roomCode} · 仅分享给你的旅伴</p></div></div></Card>
      </div>
      {toast}
      <Modal open={transferOpen} title="转移群主" onClose={() => { setTransferOpen(false); setNewOwnerId(null); }}><div className="space-y-4"><p className="text-sm text-ink-soft">选择一位成员成为新的群主，当前群主将转为普通成员。</p><select value={newOwnerId ?? ""} onChange={(event) => setNewOwnerId(event.target.value ? Number(event.target.value) : null)} className="w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"><option value="">请选择新群主</option>{transferableMembers.map((member) => <option key={member.id} value={member.id}>{member.user.name}</option>)}</select><Button className="w-full" disabled={!newOwnerId || mutation.isPending} onClick={() => newOwnerId && mutation.mutate({ type: "transfer", memberId: newOwnerId })}>确认转移</Button></div></Modal>
    </>
  );
}

export function ConstraintPage() {
  const { id = "", memberId = "" } = useParams();
  const groupId = Number(id);
  const memberIdNum = Number(memberId);
  const queryClient = useQueryClient();
  const { toast, show } = useToast();
  const membersQuery = useQuery({ queryKey: ["members", groupId], queryFn: () => api.members(groupId), enabled: !Number.isNaN(groupId) });
  const member = (membersQuery.data ?? []).find((item) => item.id === memberIdNum);
  const [form, setForm] = useState<MemberConstraint | null>(null);
  const [newPlace, setNewPlace] = useState("");
  const [newDiet, setNewDiet] = useState("");
  const [newAccess, setNewAccess] = useState("");
  const current: MemberConstraint | null = form ?? member?.constraint ?? (member ? {
    availableFrom: "",
    availableTo: "",
    maxBudget: 0,
    mustVisitPlaces: [],
    fitnessLevel: "MEDIUM",
    dietaryNeeds: [],
    accessibilityNeeds: [],
  } : null);
  const patch = (partial: Partial<MemberConstraint>) => setForm({ ...(current as MemberConstraint), ...partial });
  const save = useMutation({
    mutationFn: () => api.saveConstraint(groupId, memberIdNum, current as MemberConstraint),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["members", groupId] }); setForm(null); show("成员约束已保存"); },
    onError: (error) => show(error instanceof Error ? error.message : "保存失败"),
  });
  if (membersQuery.isLoading) return <LoadingState label="正在读取成员约束…" />;
  if (membersQuery.isError) return <ErrorState message="无法读取成员约束" onRetry={() => void membersQuery.refetch()} />;
  if (!member || !current) return <EmptyState title="未找到成员" message="该成员可能已被移除，请返回小组重试。" />;
  const chip = (text: string, onRemove: () => void, key: string) => <button key={key} onClick={onRemove} className="rounded-full bg-coral/10 px-3 py-2 text-xs font-semibold text-coral-deep">#{text} ×</button>;
  return <><PageHeader eyebrow={`CONSTRAINTS / ${member.user.name}`} title="成员约束" description="约束不是限制，而是让每个人都能放心上车的底线。" action={<Button disabled={save.isPending} onClick={() => save.mutate()}>{save.isPending ? "保存中…" : "保存修改"}</Button>} /><div className="grid gap-5 lg:grid-cols-2"><Card className="p-6"><h2 className="font-display text-xl font-bold">可用时间与预算</h2><div className="mt-5 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold text-ink">最早可出发<input type="date" value={current.availableFrom ?? ""} onChange={(event) => patch({ availableFrom: event.target.value })} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label><label className="text-sm font-semibold text-ink">最晚可返回<input type="date" value={current.availableTo ?? ""} onChange={(event) => patch({ availableTo: event.target.value })} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label></div><label className="mt-5 block text-sm font-semibold text-ink">人均预算上限<input type="number" value={current.maxBudget ?? 0} onChange={(event) => patch({ maxBudget: Number(event.target.value) })} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label><label className="mt-5 block text-sm font-semibold text-ink">体力水平<select value={current.fitnessLevel} onChange={(event) => patch({ fitnessLevel: event.target.value as MemberConstraint["fitnessLevel"] })} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"><option value="LOW">LOW</option><option value="MEDIUM">MEDIUM</option><option value="HIGH">HIGH</option></select></label></Card><Card className="p-6"><h2 className="font-display text-xl font-bold">想去与需要避开的事</h2><p className="mt-2 text-sm text-ink-soft">必访地点优先进入计划；饮食与无障碍需求会作为重规划筛选依据。</p><p className="mt-5 text-xs font-semibold text-ink-soft">必访地点</p><div className="mt-2 flex flex-wrap gap-2">{current.mustVisitPlaces.map((place) => chip(place, () => patch({ mustVisitPlaces: current.mustVisitPlaces.filter((item) => item !== place) }), `p-${place}`))}</div><div className="mt-3 flex gap-2"><Input value={newPlace} onChange={(event) => setNewPlace(event.target.value)} placeholder="添加必访地点" /><Button variant="ghost" onClick={() => { if (newPlace.trim() && !current.mustVisitPlaces.includes(newPlace.trim())) { patch({ mustVisitPlaces: [...current.mustVisitPlaces, newPlace.trim()] }); setNewPlace(""); } }}>添加</Button></div><p className="mt-5 text-xs font-semibold text-ink-soft">饮食需求（如 素食 / 清真）</p><div className="mt-2 flex flex-wrap gap-2">{current.dietaryNeeds.map((diet) => chip(diet, () => patch({ dietaryNeeds: current.dietaryNeeds.filter((item) => item !== diet) }), `d-${diet}`))}</div><div className="mt-3 flex gap-2"><Input value={newDiet} onChange={(event) => setNewDiet(event.target.value)} placeholder="添加饮食需求" /><Button variant="ghost" onClick={() => { if (newDiet.trim() && !current.dietaryNeeds.includes(newDiet.trim())) { patch({ dietaryNeeds: [...current.dietaryNeeds, newDiet.trim()] }); setNewDiet(""); } }}>添加</Button></div><p className="mt-5 text-xs font-semibold text-ink-soft">无障碍需求（如 轮椅 / 电梯）</p><div className="mt-2 flex flex-wrap gap-2">{current.accessibilityNeeds.map((access) => chip(access, () => patch({ accessibilityNeeds: current.accessibilityNeeds.filter((item) => item !== access) }), `a-${access}`))}</div><div className="mt-3 flex gap-2"><Input value={newAccess} onChange={(event) => setNewAccess(event.target.value)} placeholder="添加无障碍需求" /><Button variant="ghost" onClick={() => { if (newAccess.trim() && !current.accessibilityNeeds.includes(newAccess.trim())) { patch({ accessibilityNeeds: [...current.accessibilityNeeds, newAccess.trim()] }); setNewAccess(""); } }}>添加</Button></div></Card></div>{toast}</>;
}

export function FriendsPage() {
  const [tab, setTab] = useState("全部");
  const [keyword, setKeyword] = useState("");
  const [searchTerm, setSearchTerm] = useState("");
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const friendsQuery = useQuery({ queryKey: ["friends"], queryFn: api.friends });
  const incomingQuery = useQuery({ queryKey: ["friend-requests", "incoming"], queryFn: api.incomingFriendRequests });
  const outgoingQuery = useQuery({ queryKey: ["friend-requests", "outgoing"], queryFn: api.outgoingFriendRequests });
  const searchQuery = useQuery({
    queryKey: ["friend-search", searchTerm],
    queryFn: () => api.searchFriends(searchTerm),
    enabled: searchTerm.trim().length > 0,
  });
  const refreshFriends = () => {
    void queryClient.invalidateQueries({ queryKey: ["friends"] });
    void queryClient.invalidateQueries({ queryKey: ["friend-requests"] });
  };
  const action = useMutation({
    mutationFn: async (request: { type: "send" | "accept" | "reject" | "delete"; id: number }) => {
      if (request.type === "send") return api.sendFriendRequest(request.id);
      if (request.type === "accept") return api.acceptFriendRequest(request.id);
      if (request.type === "reject") return api.rejectFriendRequest(request.id);
      return api.deleteFriend(request.id);
    },
    onSuccess: () => {
      refreshFriends();
      show("操作已完成");
    },
    onError: (error) => show(error instanceof Error ? error.message : "操作失败"),
  });
  const friends = friendsQuery.data ?? [];
  const incoming = incomingQuery.data ?? [];
  const outgoing = outgoingQuery.data ?? [];
  const currentUser = getCurrentUser();
  const searchResults = (searchQuery.data ?? []).filter((user) => user.id !== currentUser?.id);
  const loading = friendsQuery.isLoading || incomingQuery.isLoading || outgoingQuery.isLoading;
  const error = friendsQuery.error ?? incomingQuery.error ?? outgoingQuery.error;
  const avatar = (name: string) => name.slice(0, 1);
  return (
    <>
      <PageHeader
        eyebrow="PEOPLE YOU TRAVEL WITH"
        title="好友与邀请"
        description="把一起走过的路，变成下一次出发的默契。"
        action={
          <form
            className="flex gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setSearchTerm(keyword);
            }}
          >
            <Input placeholder="搜索姓名或邮箱" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
            <Button type="submit">
              <UserPlus size={16} className="mr-2 inline" />搜索
            </Button>
          </form>
        }
      />
      {searchResults.length > 0 && (
        <Card className="mb-5 divide-y divide-slate-100 p-5">
          <p className="pb-3 text-sm font-semibold text-ink">搜索结果</p>
          {searchResults.map((user) => {
            const isFriend = friends.some((friend) => friend.id === user.id);
            const hasOutgoingRequest = outgoing.some((request) => request.addressee.id === user.id);
            const hasIncomingRequest = incoming.some((request) => request.requester.id === user.id);
            const relationshipLabel = isFriend
              ? "已是好友"
              : hasOutgoingRequest
                ? "等待对方同意"
                : hasIncomingRequest
                  ? "待你确认"
                  : "添加好友";
            return (
              <div key={user.id} className="flex items-center justify-between gap-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="grid h-10 w-10 place-items-center rounded-full bg-mint/15 font-semibold text-ink">{avatar(user.name)}</div>
                  <div>
                    <p className="font-semibold text-ink">{user.name}</p>
                    <p className="text-xs text-ink-soft">{user.email}</p>
                  </div>
                </div>
                <Button
                  onClick={() => action.mutate({ type: "send", id: user.id })}
                  disabled={action.isPending || isFriend || hasOutgoingRequest || hasIncomingRequest}
                  variant={isFriend || hasOutgoingRequest || hasIncomingRequest ? "ghost" : "primary"}
                >
                  {relationshipLabel}
                </Button>
              </div>
            );
          })}
        </Card>
      )}
      <div className="mb-5 flex gap-2">
        {["全部", "好友", "收到的申请", "发出的申请"].map((item) => (
          <button key={item} onClick={() => setTab(item)} className={`rounded-full px-4 py-2 text-sm font-semibold ${tab === item ? "bg-ink text-white" : "bg-white text-ink-soft"}`}>
            {item}
          </button>
        ))}
      </div>
      {loading ? (
        <LoadingState label="正在读取好友关系…" />
      ) : error ? (
        <ErrorState message={error instanceof Error ? error.message : undefined} onRetry={() => refreshFriends()} />
      ) : (
        <Card className="divide-y divide-slate-100 p-5">
          {tab === "收到的申请" &&
            incoming.map((request) => (
              <FriendRow
                key={request.id}
                name={request.requester.name}
                detail={request.requester.email}
                initial={avatar(request.requester.name)}
                actions={
                  <div className="flex gap-2">
                    <Button onClick={() => action.mutate({ type: "accept", id: request.id })}>接受</Button>
                    <Button variant="ghost" onClick={() => action.mutate({ type: "reject", id: request.id })}>忽略</Button>
                  </div>
                }
              />
            ))}
          {tab === "发出的申请" &&
            outgoing.map((request) => (
              <FriendRow key={request.id} name={request.addressee.name} detail="等待对方同意" initial={avatar(request.addressee.name)} />
            ))}
          {(tab === "全部" || tab === "好友") &&
            friends.map((friend) => (
              <FriendRow
                key={friend.id}
                name={friend.name}
                detail={friend.email}
                initial={avatar(friend.name)}
                actions={<Button variant="ghost" onClick={() => action.mutate({ type: "delete", id: friend.id })}>删除好友</Button>}
              />
            ))}
          {((tab === "收到的申请" && incoming.length === 0) ||
            (tab === "发出的申请" && outgoing.length === 0) ||
            ((tab === "全部" || tab === "好友") && friends.length === 0)) && (
            <p className="py-8 text-center text-sm text-ink-soft">这里还没有关系记录。</p>
          )}
        </Card>
      )}
      {toast}
    </>
  );
}

function FriendRow({
  name,
  detail,
  initial,
  actions,
}: {
  name: string;
  detail: string;
  initial: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-4 first:pt-0 last:pb-0">
      <div className="flex items-center gap-3">
        <div className="grid h-11 w-11 place-items-center rounded-full bg-coral/10 font-semibold text-coral">{initial}</div>
        <div>
          <p className="font-semibold text-ink">{name}</p>
          <p className="mt-1 text-xs text-ink-soft">{detail}</p>
        </div>
      </div>
      {actions}
    </div>
  );
}

export function TripsPage() {
  const [status, setStatus] = useState("全部");
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard });
  if (isLoading) return <LoadingState label="正在装载你的行程…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  const trips = data?.trips ?? [];
  const filteredTrips = trips.filter((trip) => {
    if (status === "全部") return true;
    if (status === "进行中") return trip.status === "ONGOING";
    if (status === "已规划") return trip.status === "PLANNED";
    if (status === "已完成") return trip.status === "COMPLETED";
    return true;
  });
  const orderedTrips = [...filteredTrips].sort((left, right) => Number(right.status === "ONGOING") - Number(left.status === "ONGOING"));
  const featuredTrip = orderedTrips[0];
  const otherTrips = orderedTrips.slice(1);
  return <><div className="relative"><div className="gallery-orbit pointer-events-none absolute -right-8 -top-16 h-40 w-40 rounded-full border-[18px] border-coral/10 motion-reduce:animate-none" /><PageHeader eyebrow="ALL ROUTES" title="行程总览" description="每一张登机牌，都是一次共同决定过的出发。" action={<Link to="/trips/new"><Button><Plus size={16} className="mr-2 inline" />新建行程</Button></Link>} /></div><div className="mb-6 flex flex-wrap gap-2">{["全部", "进行中", "已规划", "已完成"].map((item) => <button key={item} onClick={() => setStatus(item)} className={`rounded-full px-4 py-2 text-sm font-semibold transition ${status === item ? "bg-ink text-white" : "bg-white text-ink-soft hover:bg-sky/10 hover:text-sky"}`}>{item}</button>)}</div>{featuredTrip ? <div className="grid gap-5 md:grid-cols-2 lg:grid-rows-2"><div className="md:row-span-2"><BoardingPassCard trip={featuredTrip} featured onClick={() => window.location.assign(`/trips/${featuredTrip.id}`)} /></div>{otherTrips.map((trip) => <BoardingPassCard key={trip.id} trip={trip} onClick={() => window.location.assign(`/trips/${trip.id}`)} />)}</div> : <EmptyState title="还没有符合条件的行程" message="换一个状态筛选，或创建一段新的出发。" />}</>;
}

type NodeDraft = {
  name: string;
  placeName: string;
  latitude: string;
  longitude: string;
  nodeType: NodeType;
  plannedStart: string;
  plannedEnd: string;
  cost: string;
};

type PlannerPlace = {
  placeName: string;
  category: "吃" | "喝" | "玩" | "乐" | "住";
  nodeType: NodeType;
  description: string;
  latitude: number;
  longitude: number;
  durationMinutes: number;
};

const cityOptions = ["上海", "北京", "成都", "杭州", "西安", "广州", "重庆", "南京"];

function dateAfter(date: string, days: number): string {
  const value = new Date(`${date}T12:00:00`);
  value.setDate(value.getDate() + days);
  return value.toISOString().slice(0, 10);
}

function localDateTime(date: string, hour: number, minute = 0): string {
  const safeHour = Math.max(0, Math.min(23, Math.round(hour)));
  const safeMinute = Math.max(0, Math.min(59, Math.round(minute)));
  return `${date}T${String(safeHour).padStart(2, "0")}:${String(safeMinute).padStart(2, "0")}`;
}

function emptyNodeDraft(startDate = ""): NodeDraft {
  return {
    name: "",
    placeName: "",
    latitude: "",
    longitude: "",
    nodeType: "ATTRACTION",
    plannedStart: startDate ? localDateTime(startDate, 9) : "",
    plannedEnd: startDate ? localDateTime(startDate, 11) : "",
    cost: "0",
  };
}

function toNodePayload(draft: NodeDraft, sequenceOrder: number): Omit<ItineraryNode, "id"> {
  return {
    name: draft.name.trim() || draft.placeName.trim(),
    placeName: draft.placeName.trim(),
    latitude: Number(draft.latitude),
    longitude: Number(draft.longitude),
    nodeType: draft.nodeType,
    plannedStart: draft.plannedStart,
    plannedEnd: draft.plannedEnd,
    cost: Number(draft.cost) || 0,
    sequenceOrder,
    status: "PLANNED",
  };
}

type PlannerTimeBucket = "MORNING" | "MEAL" | "AFTERNOON" | "EVENING" | "LODGING";

function plannerTimeBucket(place: PlannerPlace): PlannerTimeBucket {
  const name = place.placeName.toLowerCase();
  if (place.nodeType === "LODGING" || place.category === "住") return "LODGING";
  if (/夜市|夜景|酒吧|bar|演出|livehouse|夜/.test(name) || place.category === "乐") return "EVENING";
  if (place.category === "吃" || place.category === "喝") return "MEAL";
  if (place.nodeType === "ATTRACTION" || place.category === "玩" || /景点|公园/.test(name)) return "MORNING";
  return "AFTERNOON";
}

function plannerTargetMinutes(place: PlannerPlace): number {
  const name = place.placeName.toLowerCase();
  const bucket = plannerTimeBucket(place);
  if (bucket === "LODGING") return 21 * 60;
  if (bucket === "EVENING") return 18 * 60 + 30;
  if (bucket === "MORNING") return 9 * 60;
  if (bucket === "AFTERNOON") return 14 * 60;
  if (/早餐|早茶/.test(name)) return 8 * 60;
  if (/晚餐|晚饭|宵夜/.test(name)) return 18 * 60;
  return 12 * 60;
}

function schedulePlannerPlaces(places: PlannerPlace[], date: string, days: number): ItineraryNode[] {
  const dayCount = Math.max(1, Math.floor(days));
  const dailyPlaces = Array.from({ length: dayCount }, () => [] as PlannerPlace[]);
  places.forEach((place, index) => dailyPlaces[index % dayCount].push(place));
  const bucketOrder: Record<PlannerTimeBucket, number> = {
    MORNING: 0,
    MEAL: 1,
    AFTERNOON: 2,
    EVENING: 3,
    LODGING: 4,
  };
  let sequenceOrder = 1;
  return dailyPlaces.flatMap((dayPlaces, dayIndex) => {
    const orderedPlaces = [...dayPlaces].sort((left, right) => {
      const targetDifference = plannerTargetMinutes(left) - plannerTargetMinutes(right);
      return targetDifference || bucketOrder[plannerTimeBucket(left)] - bucketOrder[plannerTimeBucket(right)];
    });
    let cursorMinutes = 8 * 60;
    const startDate = dateAfter(date, dayIndex);
    return orderedPlaces.map((place) => {
      const startMinutes = Math.max(cursorMinutes, plannerTargetMinutes(place));
      const boundedStart = Math.min(startMinutes, 22 * 60 + 30);
      const durationMinutes = Math.min(180, Math.max(30, Math.round(place.durationMinutes)));
      const endMinutes = Math.min(23 * 60 + 30, boundedStart + durationMinutes);
      const node = {
        id: -sequenceOrder,
        name: place.placeName,
        placeName: place.placeName,
        latitude: place.latitude,
        longitude: place.longitude,
        nodeType: place.nodeType,
        plannedStart: localDateTime(startDate, Math.floor(boundedStart / 60), boundedStart % 60),
        plannedEnd: localDateTime(startDate, Math.floor(endMinutes / 60), endMinutes % 60),
        cost: 0,
        sequenceOrder,
        status: "PLANNED" as const,
      };
      sequenceOrder += 1;
      cursorMinutes = endMinutes + 30;
      return node;
    });
  });
}

function categoryForNodeType(nodeType: NodeType): PlannerPlace["category"] {
  if (nodeType === "MEAL") return "吃";
  if (nodeType === "LODGING") return "住";
  if (nodeType === "TRANSPORT") return "喝";
  return "玩";
}

function offlinePlannerPlace(place: SuggestedPlace): PlannerPlace {
  return {
    placeName: place.placeName,
    category: categoryForNodeType(place.nodeType),
    nodeType: place.nodeType,
    description: `${place.city}本地规则推荐地点，适合纳入行程。`,
    latitude: place.latitude,
    longitude: place.longitude,
    durationMinutes: place.durationMinutes,
  };
}

function fallbackPlannerPlaces(
  places: SuggestedPlace[],
  limit: number,
  group?: Trip["group"],
): PlannerPlace[] {
  const mustVisitNames = new Set(
    (group?.members ?? []).flatMap((member) => member.constraint?.mustVisitPlaces ?? []).map((name) => name.trim()).filter(Boolean),
  );
  const selected: SuggestedPlace[] = [];
  for (const mustVisit of mustVisitNames) {
    const match = places.find((place) => place.placeName === mustVisit || place.placeName.includes(mustVisit) || mustVisit.includes(place.placeName));
    if (match && !selected.some((place) => place.placeName === match.placeName)) selected.push(match);
  }
  for (const place of places) {
    if (selected.length >= limit) break;
    if (!selected.some((item) => item.placeName === place.placeName)) selected.push(place);
  }
  return selected.map(offlinePlannerPlace);
}

function aiPlannerPlace(place: AiPlace): PlannerPlace {
  return {
    placeName: place.placeName,
    category: place.category,
    nodeType: place.nodeType,
    description: place.description,
    latitude: place.latitude,
    longitude: place.longitude,
    durationMinutes: place.suggestedDurationMinutes,
  };
}

export function NewTripPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const groupsQuery = useQuery({ queryKey: ["groups"], queryFn: api.groups });
  const [mode, setMode] = useState<"constraints" | "manual">("constraints");
  const [groupId, setGroupId] = useState<number | "">("");
  const [title, setTitle] = useState("");
  const [city, setCity] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [days, setDays] = useState("2");
  const [interests, setInterests] = useState("");
  const [plannerPlaces, setPlannerPlaces] = useState<PlannerPlace[]>([]);
  const [selectedPlaces, setSelectedPlaces] = useState<Set<string>>(new Set());
  const [plannerSource, setPlannerSource] = useState<"ai" | "offline" | null>(null);
  const [planning, setPlanning] = useState(false);
  const [activeTrip, setActiveTrip] = useState<Trip | null>(null);
  const [nodes, setNodes] = useState<ItineraryNode[]>([]);
  const [nodeDraft, setNodeDraft] = useState<NodeDraft>(emptyNodeDraft());
  const [weatherPreview, setWeatherPreview] = useState<WeatherPreview | null>(null);
  const [weatherLoading, setWeatherLoading] = useState(false);
  const [mapSearchTerm, setMapSearchTerm] = useState("");
  const [mapPlaces, setMapPlaces] = useState<MapPlace[]>([]);
  const [mapSearchLoading, setMapSearchLoading] = useState(false);
  const [mapSearchMessage, setMapSearchMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  if (groupsQuery.isLoading) return <LoadingState label="正在读取你的旅行小组…" />;
  if (groupsQuery.isError) return <ErrorState onRetry={() => void groupsQuery.refetch()} message={groupsQuery.error instanceof Error ? groupsQuery.error.message : undefined} />;
  const groups = groupsQuery.data ?? [];
  if (groups.length === 0) return <EmptyState title="暂时没有可用的小组" message="先创建或加入一个小组，再生成初始方案。" />;

  const selectedGroupId = groupId === "" ? groups[0].id : groupId;
  const selectedGroup = groups.find((group) => group.id === selectedGroupId);
  const suggestions = suggestPlaces(nodeDraft.placeName);
  const cityPlaces = getCitySuggestions(city);

  const updateDraft = <K extends keyof NodeDraft>(key: K, value: NodeDraft[K]) => {
    setNodeDraft((current) => ({ ...current, [key]: value }));
  };

  const previewLocationWeather = (latitudeText: string, longitudeText: string) => {
    const latitude = Number(latitudeText);
    const longitude = Number(longitudeText);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || !latitudeText || !longitudeText) return;
    setWeatherLoading(true);
    void api.previewWeather(latitude, longitude)
      .then(setWeatherPreview)
      .catch(() => setWeatherPreview({ available: false, hasAlert: false, hasPrecipitation: false, message: "天气服务暂不可用，可稍后再查" }))
      .finally(() => setWeatherLoading(false));
  };
  const searchMapPlaces = async (event: React.FormEvent) => {
    event.preventDefault();
    const query = mapSearchTerm.trim() || nodeDraft.placeName.trim();
    if (!query) return;
    setMapSearchLoading(true);
    setMapSearchMessage("");
    try {
      const result = await api.mapSearch(query, city.trim() || nodeDraft.placeName.trim());
      setMapPlaces(result.places);
      if (!result.available) setMapSearchMessage(result.message ?? "地图暂不可用，可继续使用本地地点补全。");
      else if (result.places.length === 0) setMapSearchMessage("没有找到匹配地点，可以换个关键词试试。");
    } catch {
      setMapPlaces([]);
      setMapSearchMessage("地图暂不可用，可继续使用本地地点补全。");
    } finally {
      setMapSearchLoading(false);
    }
  };
  const chooseMapPlace = (place: MapPlace) => {
    const name = place.name ?? "";
    setNodeDraft((current) => ({
      ...current,
      name,
      placeName: name,
      latitude: place.lat === undefined ? current.latitude : String(place.lat),
      longitude: place.lng === undefined ? current.longitude : String(place.lng),
    }));
    setMapSearchTerm(name);
    if (place.lat !== undefined && place.lng !== undefined) previewLocationWeather(String(place.lat), String(place.lng));
  };

  const planItinerary = async () => {
    setErrorMessage("");
    const count = Number(days);
    if (!city.trim() || !startDate || !Number.isFinite(count) || count < 1) {
      setErrorMessage("请先选择目的地、出发日期和有效天数。");
      return;
    }
    setPlanning(true);
    setPlannerPlaces([]);
    setSelectedPlaces(new Set());
    try {
      const result = await api.aiPlan(city, count, interests, groupId === "" ? undefined : selectedGroupId);
      if (result.available && result.places.length > 0) {
        setPlannerSource("ai");
        setPlannerPlaces(result.places.map(aiPlannerPlace));
      } else {
        const fallback = fallbackPlannerPlaces(cityPlaces, count * 4, selectedGroup);
        setPlannerSource("offline");
        setPlannerPlaces(fallback);
        if (fallback.length === 0) setErrorMessage("本地地点库暂时没有这个城市的建议，请换一个热门城市试试。");
      }
    } catch {
      const fallback = fallbackPlannerPlaces(cityPlaces, count * 4, selectedGroup);
      setPlannerSource("offline");
      setPlannerPlaces(fallback);
      if (fallback.length === 0) setErrorMessage("AI 和本地地点库暂时都没有这个城市的建议。");
    } finally {
      setPlanning(false);
    }
  };

  const createTrip = async (withNodes: ItineraryNode[] = [], overrides?: { title?: string; endDate?: string }) => {
    const tripTitle = overrides?.title ?? title;
    const tripEndDate = overrides?.endDate ?? endDate;
    if (!tripTitle.trim() || !startDate || !tripEndDate) {
      setErrorMessage("请补齐小组、行程名称和起止日期。");
      return null;
    }
    setBusy(true);
    setErrorMessage("");
    try {
      const trip = await api.createTrip(selectedGroupId, {
        title: tripTitle.trim(),
        status: "DRAFT",
        startDate,
        endDate: tripEndDate,
        totalBudget: withNodes.reduce((sum, node) => sum + node.cost, 0),
      });
      const savedNodes: ItineraryNode[] = [];
      for (const node of withNodes) {
        const payload = {
          name: node.name,
          placeName: node.placeName,
          latitude: node.latitude,
          longitude: node.longitude,
          nodeType: node.nodeType,
          plannedStart: node.plannedStart,
          plannedEnd: node.plannedEnd,
          cost: node.cost,
          sequenceOrder: node.sequenceOrder,
          status: node.status,
        };
        savedNodes.push(await api.addNode(trip.id, payload));
      }
      await queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      await queryClient.invalidateQueries({ queryKey: ["groups"] });
      return { trip, savedNodes };
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "创建行程失败，请稍后再试。");
      return null;
    } finally {
      setBusy(false);
    }
  };

  const handleManualCreate = async (event: React.FormEvent) => {
    event.preventDefault();
    const result = await createTrip();
    if (result) {
      setActiveTrip(result.trip);
      setNodes([]);
      setNodeDraft(emptyNodeDraft(startDate));
    }
  };

  const handleGeneratedCreate = async () => {
    const generatedTitle = `${city} ${days}日 行程`;
    const generatedEndDate = dateAfter(startDate, Number(days) - 1);
    const selected = plannerPlaces.filter((place) => selectedPlaces.has(place.placeName));
    const generatedNodes = schedulePlannerPlaces(selected, startDate, Number(days));
    if (generatedNodes.length === 0) {
      setErrorMessage("请至少勾选一个想去的地点。");
      return;
    }
    const result = await createTrip(generatedNodes, { title: generatedTitle, endDate: generatedEndDate });
    if (result) navigate(`/trips/${result.trip.id}`);
  };

  const handleAddNode = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!activeTrip || !nodeDraft.placeName.trim() || !nodeDraft.plannedStart || !nodeDraft.plannedEnd || !Number.isFinite(Number(nodeDraft.latitude)) || !Number.isFinite(Number(nodeDraft.longitude))) {
      setErrorMessage("请选择一个地点，并补齐坐标与时间。");
      return;
    }
    setBusy(true);
    setErrorMessage("");
    try {
      const saved = await api.addNode(activeTrip.id, toNodePayload(nodeDraft, nodes.length + 1));
      setNodes((current) => [...current, saved]);
      setNodeDraft(emptyNodeDraft(activeTrip.startDate));
      await queryClient.invalidateQueries({ queryKey: ["trip", activeTrip.id] });
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "节点保存失败，请稍后再试。");
    } finally {
      setBusy(false);
    }
  };

  const hasCoordinates = Number.isFinite(Number(nodeDraft.latitude)) && Number.isFinite(Number(nodeDraft.longitude)) && Boolean(nodeDraft.latitude) && Boolean(nodeDraft.longitude);
  const shownWeather = hasCoordinates ? weatherPreview : null;
  const weatherNotice = weatherLoading
    ? "正在查询该地点的天气…"
    : shownWeather?.available && shownWeather.tempMin !== undefined && shownWeather.tempMax !== undefined
      ? `白天 ${Math.round(shownWeather.tempMin)}~${Math.round(shownWeather.tempMax)}°C${shownWeather.phrase ? `，${shownWeather.phrase}` : ""}`
      : shownWeather?.message ?? "";
  const weatherAlert = shownWeather?.hasAlert || shownWeather?.hasPrecipitation;

  return <>
    <PageHeader eyebrow="NEW ROUTE" title="创建一段新行程" description="先把目的地和成员放在同一张桌上，再让每个节点都带着天气上下文出发。" />
    {activeTrip && <MapSearchPicker term={mapSearchTerm} onTermChange={setMapSearchTerm} onSearch={searchMapPlaces} places={mapPlaces} loading={mapSearchLoading} message={mapSearchMessage} onChoose={chooseMapPlace} />}
    {errorMessage && <div className="mb-5 rounded-card border border-coral/20 bg-coral/5 px-4 py-3 text-sm text-coral-deep">{errorMessage}</div>}
    {!activeTrip && <div className="grid gap-6 lg:grid-cols-[0.82fr_1.18fr]">
      <Card className="p-6">
        <div className="flex gap-2 rounded-xl bg-paper p-1">
          <button type="button" onClick={() => setMode("constraints")} className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold ${mode === "constraints" ? "bg-white text-ink shadow-sm" : "text-ink-soft"}`}>AI 自动规划</button>
          <button type="button" onClick={() => setMode("manual")} className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold ${mode === "manual" ? "bg-white text-ink shadow-sm" : "text-ink-soft"}`}>手动创建</button>
        </div>
        <div className="mt-6 space-y-4">
          <label className="block text-sm font-semibold text-ink">选择小组<select value={selectedGroupId} onChange={(event) => setGroupId(event.target.value ? Number(event.target.value) : "")} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm"><option value="">请选择小组</option>{groups.map((group) => <option key={group.id} value={group.id}>{group.name} · {group.roomCode}</option>)}</select></label>
          {mode === "manual" ? <form onSubmit={handleManualCreate} className="space-y-4">
            <label className="block text-sm font-semibold text-ink">行程名称<Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="例如：杭州春茶之旅" /></label>
            <label className="block text-sm font-semibold text-ink">目的地城市<Input value={city} onChange={(event) => setCity(event.target.value)} placeholder="例如：杭州" /></label>
            <div className="grid gap-3 sm:grid-cols-2"><label className="block text-sm font-semibold text-ink">开始日期<input required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label><label className="block text-sm font-semibold text-ink">结束日期<input required type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label></div>
            <Button type="submit" disabled={busy} className="w-full">{busy ? "正在创建…" : "创建行程并添加节点"}<ArrowRight size={16} className="ml-2 inline" /></Button>
          </form> : <>
            <label className="block text-sm font-semibold text-ink">目的地城市<input list="trip-cities" value={city} onChange={(event) => setCity(event.target.value)} placeholder="输入上海、北京、成都…" className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /><datalist id="trip-cities">{cityOptions.map((option) => <option key={option} value={option} />)}</datalist></label>
            <div className="grid gap-3 sm:grid-cols-[1fr_0.6fr]"><label className="block text-sm font-semibold text-ink">出发日期<input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label><label className="block text-sm font-semibold text-ink">旅行天数<input type="number" min="1" max="7" value={days} onChange={(event) => setDays(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label></div>
            <label className="block text-sm font-semibold text-ink">偏好 / 兴趣<span className="ml-2 text-xs font-normal text-ink-soft">可选</span><Input value={interests} onChange={(event) => setInterests(event.target.value)} placeholder="例如：川菜、博物馆、适合拍照" /></label>
            <Button type="button" disabled={planning || busy} className="w-full" onClick={() => void planItinerary()}>{planning ? "AI 正在规划路线…" : "AI 规划吃喝玩乐"}<Sparkles size={16} className="ml-2 inline" /></Button>
          </>}
        </div>
      </Card>
      <Card className="min-h-[420px] p-6">
        {planning ? <div className="flex h-full min-h-[360px] items-center justify-center text-center"><div><div className="mx-auto grid h-14 w-14 animate-pulse place-items-center rounded-2xl bg-coral/10 text-coral"><Sparkles /></div><h2 className="mt-4 font-display text-xl font-bold text-ink">AI 正在规划路线…</h2><p className="mt-2 text-sm text-ink-soft">正在整理 {city || "目的地"} 的吃喝玩乐清单。</p></div></div> : plannerPlaces.length > 0 ? <><div className="flex flex-wrap items-center gap-2"><Badge tone={plannerSource === "ai" ? "coral" : "mint"}>{plannerSource === "ai" ? "AI 推荐" : "本地推荐"}</Badge><span className="font-mono text-xs text-ink-soft">{city} · {days} 天</span><span className="ml-auto text-sm font-semibold text-ink">{selectedPlaces.size} / {plannerPlaces.length} 已选</span></div><h2 className="mt-3 font-display text-2xl font-bold text-ink">勾选想去的地点</h2><p className="mt-2 text-sm leading-6 text-ink-soft">先挑选你真正想去的地方，确认后才会创建行程和节点。</p><div className="mt-5 grid gap-3 sm:grid-cols-2">{plannerPlaces.map((place) => { const selected = selectedPlaces.has(place.placeName); return <button type="button" key={place.placeName} onClick={() => setSelectedPlaces((current) => { const next = new Set(current); if (next.has(place.placeName)) next.delete(place.placeName); else next.add(place.placeName); return next; })} className={`rounded-card border p-4 text-left transition hover:-translate-y-0.5 ${selected ? "border-coral bg-coral/5 shadow-soft" : "border-slate-100 bg-white hover:border-coral/30"}`}><div className="flex items-start gap-3"><span className={`mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-md border text-xs ${selected ? "border-coral bg-coral text-white" : "border-slate-300 text-transparent"}`}>✓</span><span className="min-w-0 flex-1"><span className="flex items-center gap-2"><span className="font-semibold text-ink">{place.placeName}</span><Badge tone={place.category === "吃" ? "sun" : place.category === "住" ? "sky" : "mint"}>{place.category}</Badge></span><span className="mt-2 block text-xs text-ink-soft">{place.nodeType} · {place.durationMinutes} 分钟</span><span className="mt-2 block text-sm leading-5 text-ink-soft">{place.description}</span></span></div></button>; })}</div><Button type="button" disabled={busy || selectedPlaces.size === 0} className="mt-6 w-full" onClick={() => void handleGeneratedCreate()}>{busy ? "正在创建行程…" : `确认创建行程（${selectedPlaces.size} 个地点）`}<ArrowRight size={16} className="ml-2 inline" /></Button></> : <div className="flex h-full min-h-[360px] items-center justify-center text-center"><div><div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-sky/10 text-sky"><Sparkles /></div><h2 className="mt-4 font-display text-xl font-bold text-ink">输入城市，开始一份智能清单</h2><p className="mt-2 max-w-sm text-sm leading-6 text-ink-soft">AI 不可用时会自动切换到已有的本地地点推荐。</p></div></div>}
      </Card>
    </div>}
    {activeTrip && <div className="grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
      <Card className="p-6">
        <div className="flex items-start justify-between gap-4"><div><p className="eyebrow">NODE EDITOR</p><h2 className="mt-2 font-display text-2xl font-bold text-ink">{activeTrip.title}</h2><p className="mt-1 text-sm text-ink-soft">{activeTrip.startDate} → {activeTrip.endDate} · {nodes.length} 个节点</p></div><Badge tone="mint">已创建</Badge></div>
        <form onSubmit={handleAddNode} className="mt-6 space-y-4">
          <label className="relative block text-sm font-semibold text-ink">地点名称<input value={nodeDraft.placeName} onChange={(event) => { updateDraft("placeName", event.target.value); updateDraft("name", event.target.value); }} placeholder="搜索外滩、故宫、宽窄巷子…" className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm" />{nodeDraft.placeName && suggestions.length > 0 && <div className="absolute left-0 right-0 top-full z-20 mt-2 overflow-hidden rounded-xl border border-slate-100 bg-white shadow-xl">{suggestions.map((suggestion) => <button type="button" key={suggestion.placeName} onClick={() => { setNodeDraft((current) => ({ ...current, name: suggestion.placeName, placeName: suggestion.placeName, latitude: String(suggestion.latitude), longitude: String(suggestion.longitude), nodeType: suggestion.nodeType })); previewLocationWeather(String(suggestion.latitude), String(suggestion.longitude)); }} className="flex w-full items-center justify-between px-4 py-3 text-left text-sm hover:bg-paper"><span className="font-semibold text-ink">{suggestion.placeName}</span><span className="text-xs text-ink-soft">{suggestion.city} · {suggestion.durationMinutes} 分钟</span></button>)}</div>}</label>
          <div className="grid gap-3 sm:grid-cols-2"><label className="block text-sm font-semibold text-ink">节点类型<select value={nodeDraft.nodeType} onChange={(event) => updateDraft("nodeType", event.target.value as NodeType)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm"><option value="ATTRACTION">景点</option><option value="MEAL">餐饮</option><option value="LODGING">住宿</option><option value="TRANSPORT">交通</option><option value="OTHER">其他</option></select></label><label className="block text-sm font-semibold text-ink">费用（元）<input type="number" min="0" value={nodeDraft.cost} onChange={(event) => updateDraft("cost", event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label></div>
          <div className="grid gap-3 sm:grid-cols-2"><label className="block text-sm font-semibold text-ink">开始时间<input required type="datetime-local" value={nodeDraft.plannedStart} onChange={(event) => updateDraft("plannedStart", event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label><label className="block text-sm font-semibold text-ink">结束时间<input required type="datetime-local" value={nodeDraft.plannedEnd} onChange={(event) => updateDraft("plannedEnd", event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label></div>
          <div className="grid gap-3 sm:grid-cols-2"><label className="block text-sm font-semibold text-ink">纬度<input value={nodeDraft.latitude} onChange={(event) => { updateDraft("latitude", event.target.value); previewLocationWeather(event.target.value, nodeDraft.longitude); }} placeholder="自动填充" className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label><label className="block text-sm font-semibold text-ink">经度<input value={nodeDraft.longitude} onChange={(event) => { updateDraft("longitude", event.target.value); previewLocationWeather(nodeDraft.latitude, event.target.value); }} placeholder="自动填充" className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm" /></label></div>
          {weatherNotice && <div className={`rounded-xl px-4 py-3 text-sm ${weatherAlert ? "bg-sun/15 text-amber-800" : shownWeather?.available ? "bg-mint/10 text-emerald-800" : "bg-slate-100 text-ink-soft"}`}><span className="font-semibold">{weatherAlert ? "天气提醒 · " : "天气预览 · "}</span>{weatherNotice}{weatherAlert && "，注意安排"}</div>}
          <Button type="submit" disabled={busy} className="w-full">{busy ? "正在保存节点…" : "保存节点"}<Plus size={16} className="ml-2 inline" /></Button>
        </form>
      </Card>
      <Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">LIVE ITINERARY</p><h2 className="mt-2 font-display text-xl font-bold text-ink">已经加入的节点</h2></div><Badge tone="coral">{nodes.length} 个节点</Badge></div>{nodes.length > 0 ? <div className="mt-6"><RouteTrail nodes={nodes} compact /></div> : <div className="mt-8 rounded-card bg-paper p-6 text-center text-sm leading-6 text-ink-soft">还没有节点。选一个地点，天气提示会先帮你看一眼再保存。</div>}<Button type="button" variant="secondary" className="mt-6 w-full" onClick={() => navigate(`/trips/${activeTrip.id}`)}>完成并查看行程<ArrowRight size={16} className="ml-2 inline" /></Button></Card>
    </div>}
  </>;
}

function MapSearchPicker({
  term,
  onTermChange,
  onSearch,
  places,
  loading,
  message,
  onChoose,
}: {
  term: string;
  onTermChange: (value: string) => void;
  onSearch: (event: React.FormEvent) => void;
  places: MapPlace[];
  loading: boolean;
  message: string;
  onChoose: (place: MapPlace) => void;
}) {
  return (
    <Card className="mb-5 border-sky/20 bg-sky/5 p-5">
      <div className="flex items-start gap-3">
        <div className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-white text-sky shadow-sm">
          <Search size={18} />
        </div>
        <div className="min-w-0 flex-1">
          <p className="eyebrow text-sky">MAP PICKER</p>
          <h2 className="mt-1 font-display text-lg font-bold text-ink">用地图搜索并选一个节点</h2>
          <p className="mt-1 text-xs leading-5 text-ink-soft">选中后会自动填入地点和经纬度，并立即刷新天气预览。</p>
        </div>
      </div>
      <form onSubmit={onSearch} className="mt-4 flex gap-2">
        <Input value={term} onChange={(event) => onTermChange(event.target.value)} placeholder="搜索西湖、故宫、附近餐馆…" />
        <Button type="submit" variant="secondary" disabled={loading} className="shrink-0">
          {loading ? "搜索中…" : "搜索"}
        </Button>
      </form>
      {message && <p className="mt-3 rounded-xl bg-white/70 px-3 py-2 text-xs text-ink-soft">{message}</p>}
      {places.length > 0 && (
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {places.slice(0, 6).map((place, index) => (
            <button
              key={`${place.uid ?? place.name}-${index}`}
              type="button"
              onClick={() => onChoose(place)}
              className="rounded-xl border border-white bg-white px-3 py-3 text-left transition hover:-translate-y-0.5 hover:border-sky/40 motion-reduce:transition-none"
            >
              <p className="truncate text-sm font-semibold text-ink">{place.name ?? "未命名地点"}</p>
              <p className="mt-1 line-clamp-2 text-xs text-ink-soft">{place.address ?? place.area ?? "百度地图地点"}</p>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}

const severityRank: Record<Severity, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
const severityLabel: Record<Severity, string> = { LOW: "低", MEDIUM: "中", HIGH: "高", CRITICAL: "紧急" };

function eventGroupTitle(event: ExternalEvent, tripTitleById: Map<number, string>): string {
  const scopedTitle = event.tripId !== undefined ? tripTitleById.get(event.tripId) : undefined;
  return scopedTitle?.trim() || event.tripTitle?.trim() || (event.tripId ? `行程 #${event.tripId}` : "未关联行程");
}

function eventGroupSeverity(events: ExternalEvent[]): Severity | undefined {
  return events.reduce<Severity | undefined>((worst, event) => {
    if (!event.severity || (worst && severityRank[worst] >= severityRank[event.severity])) return worst;
    return event.severity;
  }, undefined);
}

export function EventsPage() {
  const scope = useTripScope();
  const currentUser = getCurrentUser();
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["events", "mine", currentUser?.id], queryFn: api.myEvents });
  const tripId = scope.tripId;
  const impactQueries = useQueries({ queries: scope.trips.map((trip) => ({ queryKey: ["impacts", trip.id], queryFn: () => api.impacts(trip.id), enabled: scope.trips.length > 0 })) });
  const [typeFilter, setTypeFilter] = useState<"ALL" | "TRAFFIC" | EventType>("ALL");
  const [severityFilter, setSeverityFilter] = useState<"ALL" | Severity>("ALL");
  const [keyword, setKeyword] = useState("");
  const [collapsedGroups, setCollapsedGroups] = useState<string[]>([]);
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const refreshMonitor = () => {
    void queryClient.invalidateQueries({ queryKey: ["events", "mine", currentUser?.id] });
    void queryClient.invalidateQueries({ queryKey: ["impacts", tripId] });
    void queryClient.invalidateQueries({ queryKey: ["risk", tripId] });
  };
  const fetchEvents = useMutation({
    mutationFn: () => api.scanEvents(tripId as number),
    onSuccess: () => { refreshMonitor(); show("已拉取当前行程的实时事件"); },
    onError: (err) => show(err instanceof Error ? err.message : "拉取事件失败"),
  });
  const assess = useMutation({
    mutationFn: () => api.assess(tripId as number),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["impacts", tripId] }); void queryClient.invalidateQueries({ queryKey: ["risk", tripId] }); show("已重新评估影响与风险"); },
    onError: (err) => show(err instanceof Error ? err.message : "评估失败"),
  });
  if (scope.isLoading || isLoading) return <LoadingState label="正在同步事件信号…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data) return <EmptyState title="暂时没有事件数据" />;
  const visibleTripIds = new Set(scope.trips.map((trip) => trip.id));
  const tripTitleById = new Map(scope.trips.map((trip) => [trip.id, trip.title] as const));
  // 事件—节点匹配只保留归属当前可见行程的影响，避免其它行程（如长春）的节点串到本事件卡片下。
  const eventNodeMatches = impactQueries.flatMap((query) => query.data ?? []).reduce<Record<number, ItineraryNode[]>>((map, impact) => {
    const impactEvent = impact.event;
    if (impactEvent?.id && impactEvent.tripId !== undefined && visibleTripIds.has(impactEvent.tripId) && impact.affectedNode) {
      map[impactEvent.id] = [...(map[impactEvent.id] ?? []), impact.affectedNode];
    }
    return map;
  }, {});
  const typeTabs: { value: "ALL" | "TRAFFIC" | EventType; label: string }[] = [
    { value: "ALL", label: "全部" },
    { value: "WEATHER", label: "天气" },
    { value: "TRAFFIC", label: "交通类" },
    { value: "ATTRACTION_CLOSURE", label: "景点闭馆" },
    { value: "LARGE_EVENT", label: "大型活动" },
  ];
  const filteredEvents = data.filter((event) => {
    if (event.tripId === undefined || !visibleTripIds.has(event.tripId)) return false;
    const typeMatches = typeFilter === "ALL" || (typeFilter === "TRAFFIC" ? event.eventType === "ROAD_WORK" || event.eventType === "TRAFFIC_CONTROL" : event.eventType === typeFilter);
    const severityMatches = severityFilter === "ALL" || event.severity === severityFilter;
    const searchText = `${event.title ?? ""} ${event.description ?? ""} ${event.placeName ?? ""}`.toLowerCase();
    return typeMatches && severityMatches && searchText.includes(keyword.trim().toLowerCase());
  });
  // 分组以 tripId 作为稳定键：同名行程也不会被合并，事件也不会因为标题相同而串到别的行程分组里。
  const groupedEvents = filteredEvents.reduce<Record<string, { title: string; events: ExternalEvent[]; worstSeverity?: Severity }>>((groups, event) => {
    const key = event.tripId !== undefined ? String(event.tripId) : "none";
    const title = eventGroupTitle(event, tripTitleById);
    const group = groups[key] ?? { title, events: [] };
    group.events.push(event);
    group.worstSeverity = eventGroupSeverity(group.events);
    groups[key] = group;
    return groups;
  }, {});
  const activeTripKey = scope.tripId !== undefined ? String(scope.tripId) : undefined;
  const eventGroups = Object.entries(groupedEvents).sort(([left, leftGroup], [right, rightGroup]) => {
    if (left === activeTripKey) return -1;
    if (right === activeTripKey) return 1;
    return leftGroup.title.localeCompare(rightGroup.title, "zh-CN");
  });
  return (
    <>
      <PageHeader eyebrow="LIVE SIGNALS" title="事件监测" description="天气、交通和城市公告会在这里汇合，帮助你提前看见路线中的变化。" action={<div className="flex flex-wrap gap-2"><Button variant="ghost" disabled={tripId === undefined || fetchEvents.isPending} onClick={() => fetchEvents.mutate()}>{fetchEvents.isPending ? "拉取中…" : "拉取实时事件"}</Button><Button variant="ghost" disabled={tripId === undefined || assess.isPending} onClick={() => assess.mutate()}>{assess.isPending ? "评估中…" : "评估影响"}</Button><Button variant="ghost" onClick={() => void refetch()}>刷新事件</Button></div>} />
      <TripSwitcher scope={scope} />
      <Card className="mb-6 p-4">
        <div className="flex flex-wrap items-center gap-2">
          {typeTabs.map((tab) => <button key={tab.value} type="button" onClick={() => setTypeFilter(tab.value)} className={`rounded-full px-4 py-2 text-xs font-semibold transition ${typeFilter === tab.value ? "bg-ink text-white shadow-sm" : "bg-paper text-ink-soft hover:bg-sky/10 hover:text-sky"}`}>{tab.label}</button>)}
          <label className="ml-auto flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-2 text-xs text-ink-soft"><span>严重度</span><select value={severityFilter} onChange={(event) => setSeverityFilter(event.target.value as "ALL" | Severity)} className="bg-transparent font-semibold text-ink outline-none"><option value="ALL">全部</option><option value="CRITICAL">紧急</option><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select></label>
        </div>
        <label className="relative mt-3 block"><Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" /><input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索事件标题、描述或地点" className="w-full rounded-xl border border-slate-200 bg-paper py-2.5 pl-9 pr-4 text-sm text-ink outline-none transition focus:border-sky" /></label>
      </Card>
      <div className="space-y-4">
        {eventGroups.length === 0 ? <Card className="py-10 text-center text-sm text-ink-soft">{data.length === 0 ? "当前没有活跃事件。" : "没有符合筛选条件的事件。"}</Card> : eventGroups.map(([key, group]) => {
          const expanded = !collapsedGroups.includes(key);
          const groupTone = group.worstSeverity === "CRITICAL" || group.worstSeverity === "HIGH" ? "risk" : group.worstSeverity === "MEDIUM" ? "sun" : group.worstSeverity === "LOW" ? "mint" : "neutral";
          return <Card key={key} className="overflow-hidden p-0"><button type="button" aria-expanded={expanded} onClick={() => setCollapsedGroups((current) => current.includes(key) ? current.filter((item) => item !== key) : [...current, key])} className="flex w-full items-center justify-between gap-4 p-5 text-left transition hover:bg-paper motion-reduce:transition-none"><div className="flex min-w-0 items-center gap-3"><span className={`h-2.5 w-2.5 shrink-0 rounded-full ${group.worstSeverity === "CRITICAL" || group.worstSeverity === "HIGH" ? "bg-coral" : group.worstSeverity === "MEDIUM" ? "bg-sun" : "bg-mint"}`} /><div className="min-w-0"><p className="truncate font-display text-lg font-bold text-ink">{group.title}</p><p className="mt-1 text-xs text-ink-soft">{group.events.length} 条事件</p></div></div><div className="flex items-center gap-3"><Badge tone={groupTone}>{group.worstSeverity ? severityLabel[group.worstSeverity] : "未评级"}</Badge><ChevronRight size={18} className={`text-ink-soft transition-transform duration-300 ${expanded ? "rotate-90" : ""} motion-reduce:transition-none`} /></div></button><div className={`grid transition-[grid-template-rows] duration-300 motion-reduce:transition-none ${expanded ? "grid-rows-[1fr]" : "grid-rows-[0fr]"}`}><div className="min-h-0 overflow-hidden"><div className="space-y-3 border-t border-slate-100 p-3">{group.events.map((event) => { const matches = eventNodeMatches[event.id] ?? []; const accent = event.severity === "CRITICAL" ? "border-risk-critical" : event.severity === "HIGH" ? "border-coral" : event.severity === "MEDIUM" ? "border-sun" : "border-mint"; return <div key={event.id} className={`group flex flex-wrap items-start gap-4 rounded-card border-l-4 bg-white p-4 transition hover:-translate-y-0.5 hover:shadow-soft motion-reduce:transition-none md:p-5 ${accent}`}><div className="h-16 w-16 shrink-0 overflow-hidden rounded-2xl"><ImageFallback src={getPlaceImage(event.placeName ?? "", "OTHER")} alt={event.placeName ?? "事件地点"} city={event.placeName ?? "事件"} /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><span className={`grid h-9 w-9 place-items-center rounded-xl ${event.severity === "HIGH" || event.severity === "CRITICAL" ? "bg-coral/10 text-coral" : "bg-sun/20 text-amber-700"}`}><EventIcon type={event.eventType ?? "OTHER"} /></span><h2 className="font-semibold text-ink">{event.title ?? "未命名事件"}</h2>{event.severity && <Badge tone={event.severity === "HIGH" || event.severity === "CRITICAL" ? "risk" : event.severity === "LOW" ? "mint" : "sun"}>{severityLabel[event.severity]}</Badge>}{event.tempMin !== undefined && event.tempMax !== undefined && <span className="rounded-full bg-sky/10 px-2.5 py-1 font-mono text-[11px] font-semibold text-blue-700">{Math.round(event.tempMin)}~{Math.round(event.tempMax)}°C</span>}</div><p className="mt-2 text-sm leading-6 text-ink-soft">{event.description ?? "暂无详细描述"}</p><div className="mt-3 flex flex-wrap items-center gap-2">{event.placeName && <span className="font-mono text-[11px] text-ink-soft">{event.placeName}{event.startTime ? ` · ${event.startTime.replace("T", " ")}` : ""}{event.endTime ? ` — ${event.endTime.slice(11)}` : ""}</span>}<span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${matches.length > 0 ? "bg-coral/10 text-coral-deep" : "bg-slate-100 text-ink-soft"}`}>{matches.length > 0 ? `影响：${matches[0].name} · ${group.title}` : "未匹配到对应行程节点"}</span></div></div><Button variant="ghost" onClick={(event) => { event.stopPropagation(); show("事件已加入影响分析队列"); }}>分析影响</Button></div>; })}</div></div></div></Card>;
        })}
      </div>
      {toast}
    </>
  );
}

export function ImpactsPage() {
  const scope = useTripScope();
  const tripId = scope.tripId;
  const { data: trip, isLoading: tripLoading, isError: tripError, error: tripQueryError, refetch: refetchTrip } = useQuery({ queryKey: ["trip", tripId], queryFn: () => api.trip(tripId as number), enabled: tripId !== undefined });
  const { data: risk, isLoading: riskLoading, isError: riskError, error: riskQueryError, refetch: refetchRisk } = useQuery({ queryKey: ["risk", tripId], queryFn: () => api.risk(tripId as number), enabled: tripId !== undefined });
  const { data: impacts, isLoading: impactsLoading, isError: impactsError, refetch: refetchImpacts } = useQuery({ queryKey: ["impacts", tripId], queryFn: () => api.impacts(tripId as number), enabled: tripId !== undefined });
  const { toast, show } = useToast();
  if (scope.isLoading || tripLoading || riskLoading || impactsLoading) return <LoadingState label="正在计算影响与风险…" />;
  if (tripError || riskError || impactsError) {
    const queryError = tripQueryError ?? riskQueryError;
    return <ErrorState onRetry={() => { void refetchTrip(); void refetchRisk(); void refetchImpacts(); }} message={queryError instanceof Error ? queryError.message : undefined} />;
  }
  if (!trip || !risk) return <EmptyState title="暂无风险报告" message="当行程和事件数据准备好后，这里会显示风险拆解。" />;
  const statusAffectedNodes = trip.itineraryNodes.filter((node) => node.status === "AFFECTED");
  const affectedNodes = statusAffectedNodes.length > 0
    ? statusAffectedNodes
    : impacts?.map((item) => item.affectedNode).filter((node): node is NonNullable<typeof node> => node !== undefined) ?? [];
  const eventsByNode = buildEventsByNode(impacts);
  return <><PageHeader eyebrow="IMPACT & RISK" title="影响与风险" description="把一条外部消息，翻译成你们路线中具体的下一步。" action={<Button onClick={() => { void refetchRisk(); void refetchImpacts(); show("影响分析已刷新"); }}>刷新影响分析</Button>} /><TripSwitcher scope={scope} /><div className="grid gap-5 xl:grid-cols-[0.7fr_1.3fr]"><Card className="flex flex-col items-center bg-coral/5 p-7 text-center"><p className="eyebrow">TRIP RISK REPORT</p><div className="mt-5"><RiskGauge score={risk.overallScore ?? 0} label={risk.riskLevel ?? "暂无评级"} /></div><h2 className="mt-4 font-display text-xl font-bold text-ink">{risk.affectedNodes > 0 ? "路线需要一个决定" : "当前路线暂无影响"}</h2><p className="mt-2 max-w-xs text-sm leading-6 text-ink-soft">{risk.affectedNodes} / {risk.totalNodes} 个节点被事件命中。</p><Link to={`/plans?trip=${tripId}`} className="mt-5 text-sm font-semibold text-sky">查看替代方案 →</Link></Card><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">SCORING BREAKDOWN</p><h2 className="mt-2 font-display text-xl font-bold">风险是怎么来的</h2></div><CircleAlert className="text-coral" /></div><div className="mt-6 grid gap-4 sm:grid-cols-2"><div className="rounded-card bg-sky/5 p-4"><div className="flex justify-between text-sm font-semibold"><span>总体风险</span><span className="font-mono text-coral">{risk.overallScore}/100</span></div><div className="mt-3 h-2 rounded-full bg-white"><div className="h-full rounded-full bg-coral" style={{ width: `${Math.min(100, Math.max(0, risk.overallScore))}%` }} /></div><p className="mt-2 text-xs text-ink-soft">{risk.assessments?.length ?? 0} 条影响评估</p></div></div></Card></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">AFFECTED NODES</p><h2 className="mt-2 font-display text-xl font-bold">受影响节点</h2></div><Badge tone="coral">{affectedNodes.length} 个节点</Badge></div><div className="mt-6"><RouteTrail nodes={affectedNodes} eventsByNode={eventsByNode} /></div></Card>{toast}</>;
}

const PLAN_STRATEGY_LABEL: Record<string, string> = { MIN_EXTRA_COST: "成本最省", MIN_DELAY: "时间最快", MIN_CHANGE: "改动最小" };
const PLAN_STATUS_LABEL: Record<string, string> = { PROPOSED: "待决策", VOTING: "投票中", ACCEPTED: "已采纳", REJECTED: "已否决" };
const CHANGE_TYPE_LABEL: Record<string, string> = { RESCHEDULE: "调整时间", REPLACE: "替换节点", REMOVE: "移除节点", ADD: "新增节点" };

function fmtDateTime(iso?: string): string {
  if (!iso) return "待定";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "待定";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function fmtRange(start?: string, end?: string): string {
  if (!start && !end) return "时间待定";
  return `${fmtDateTime(start)} — ${fmtDateTime(end)}`;
}

export function PlansPage() {
  const scope = useTripScope();
  const tripId = scope.tripId;
  const plansQuery = useQuery({ queryKey: ["plans", tripId], queryFn: () => api.plans(tripId as number), enabled: tripId !== undefined });
  const plans = (plansQuery.data ?? []).map((plan) => ({
    ...plan,
    title: plan.title ?? "未命名方案",
    strategy: plan.strategy ?? "MIN_CHANGE",
    extraCost: plan.extraCost ?? 0,
    extraDelayMinutes: plan.extraDelayMinutes ?? 0,
    changedNodeCount: plan.changedNodeCount ?? 0,
    summary: plan.summary ?? "暂无方案说明",
    status: plan.status ?? "PROPOSED",
    changes: (plan.proposedNodeChanges ?? []).map((change, index) => ({
      key: change.id ?? index,
      type: change.changeType ?? "REMOVE",
      fromPlace: change.originalNode?.placeName ?? change.originalNode?.name ?? "原节点",
      fromStart: change.originalNode?.plannedStart,
      fromEnd: change.originalNode?.plannedEnd,
      fromCost: change.originalNode?.cost,
      toPlace: change.newPlaceName ?? undefined,
      toStart: change.newStart,
      toEnd: change.newEnd,
      toCost: change.newCost ?? undefined,
      note: change.note ?? "暂无变更说明",
    })),
  }));
  const [selectedId, setSelectedId] = useState<number>();
  const selected = plans.find((plan) => plan.id === selectedId) ?? plans[0];
  const { toast, show } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const regenerate = useMutation({
    mutationFn: () => api.replan(tripId as number),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["plans", tripId] }); show("已根据最新影响重新生成方案"); },
    onError: (error) => show(error instanceof Error ? error.message : "重新生成失败"),
  });
  const startVoting = useMutation({
    mutationFn: (planId: number) => api.startVoting(planId),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["plans", tripId] }); show("已发起投票，前往投票中心"); navigate(`/votes?trip=${tripId}`); },
    onError: (error) => show(error instanceof Error ? error.message : "发起投票失败"),
  });
  if (scope.isLoading || plansQuery.isLoading) return <LoadingState label="正在读取替代方案…" />;
  if (plansQuery.isError) return <ErrorState onRetry={() => void plansQuery.refetch()} message={plansQuery.error instanceof Error ? plansQuery.error.message : undefined} />;
  if (!selected) return <><PageHeader eyebrow="应变方案" title="替代方案" description="不同的取舍，没有绝对正确的答案。" action={<Button disabled={tripId === undefined || regenerate.isPending} onClick={() => regenerate.mutate()}>{regenerate.isPending ? "生成中…" : "生成替代方案"}</Button>} /><TripSwitcher scope={scope} /><EmptyState title="暂无替代方案" message="当行程受到事件影响后，点右上角“生成替代方案”即可生成可比较的选项。" />{toast}</>;
  return <><PageHeader eyebrow="应变方案" title="替代方案" description="不同的取舍，没有绝对正确的答案。把成本、时间和改变程度放在一起比较。" action={<div className="flex gap-2"><Button variant="ghost" disabled={tripId === undefined || regenerate.isPending} onClick={() => regenerate.mutate()}>{regenerate.isPending ? "生成中…" : "重新生成"}</Button><Button disabled={!selected || startVoting.isPending} onClick={() => selected && startVoting.mutate(selected.id)}>{startVoting.isPending ? "发起中…" : selected?.status === "VOTING" ? "投票进行中" : "发起投票"}</Button></div>} /><TripSwitcher scope={scope} /><div className="grid gap-5 xl:grid-cols-3">{plans.map((plan) => <button key={plan.id} onClick={() => setSelectedId(plan.id)} className={`card p-5 text-left transition hover:-translate-y-1 ${selected.id === plan.id ? "border-coral ring-2 ring-coral/20" : ""}`}><div className="flex items-center justify-between"><Badge tone={plan.status === "VOTING" ? "coral" : plan.status === "ACCEPTED" ? "mint" : plan.status === "REJECTED" ? "coral" : "neutral"}>{PLAN_STATUS_LABEL[plan.status] ?? plan.status}</Badge><span className="rounded-full bg-sky/10 px-2 py-0.5 text-xs font-semibold text-blue-700">{PLAN_STRATEGY_LABEL[plan.strategy] ?? plan.strategy}</span></div><h2 className="mt-4 font-display text-lg font-bold text-ink">{plan.title}</h2><div className="mt-5 grid grid-cols-3 gap-2 border-y border-slate-100 py-4 text-center"><div><p className="font-mono text-lg font-bold text-ink">¥{plan.extraCost}</p><p className="text-[10px] text-ink-soft">额外成本</p></div><div><p className="font-mono text-lg font-bold text-ink">{plan.extraDelayMinutes}m</p><p className="text-[10px] text-ink-soft">额外延误</p></div><div><p className="font-mono text-lg font-bold text-ink">{plan.changedNodeCount}</p><p className="text-[10px] text-ink-soft">节点变更</p></div></div><p className="mt-4 text-sm leading-6 text-ink-soft">{plan.summary}</p><p className="mt-5 text-sm font-semibold text-sky">查看变更清单 →</p></button>)}</div><Card className="mt-6 p-6"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">当前所选方案</p><h2 className="mt-2 font-display text-xl font-bold">{selected.title}</h2></div><Badge tone={selected.status === "ACCEPTED" ? "mint" : selected.status === "REJECTED" ? "coral" : "sky"}>{PLAN_STRATEGY_LABEL[selected.strategy] ?? selected.strategy}</Badge></div><div className="mt-5 grid grid-cols-3 gap-3"><div className="rounded-card bg-coral/5 p-3 text-center"><p className="font-mono text-lg font-bold text-coral-deep">¥{selected.extraCost}</p><p className="mt-1 text-[11px] text-ink-soft">额外成本</p></div><div className="rounded-card bg-sun/10 p-3 text-center"><p className="font-mono text-lg font-bold text-amber-700">{selected.extraDelayMinutes} 分钟</p><p className="mt-1 text-[11px] text-ink-soft">额外延误</p></div><div className="rounded-card bg-sky/5 p-3 text-center"><p className="font-mono text-lg font-bold text-blue-700">{selected.changedNodeCount} 处</p><p className="mt-1 text-[11px] text-ink-soft">节点变更</p></div></div><p className="mt-4 text-sm leading-6 text-ink-soft">{selected.summary}</p><div className="mt-5 space-y-4">{selected.changes.length === 0 ? <p className="rounded-xl bg-paper p-4 text-sm text-ink-soft">该方案无需改动任何节点，可直接沿用原计划。</p> : selected.changes.map((change) => <div key={change.key} className="rounded-card border border-slate-100 bg-white p-4"><div className="flex flex-wrap items-center gap-2"><Badge tone={change.type === "REMOVE" ? "coral" : change.type === "REPLACE" ? "sky" : "sun"}>{CHANGE_TYPE_LABEL[change.type] ?? change.type}</Badge><span className="text-xs text-ink-soft">{change.note}</span></div><div className="mt-3 grid grid-cols-[1fr_auto_1fr] items-stretch gap-2"><div className="rounded-xl bg-paper p-3"><p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">原安排</p><p className="mt-1 text-sm font-semibold text-ink line-through decoration-coral/50">{change.fromPlace}</p><p className="mt-1 text-xs text-ink-soft">{fmtRange(change.fromStart, change.fromEnd)}</p>{change.fromCost !== undefined && <p className="text-xs text-ink-soft">费用 ¥{change.fromCost}</p>}</div><div className="grid place-items-center px-1 text-coral"><ArrowRight size={20} /></div>{change.type === "REMOVE" ? <div className="grid place-items-center rounded-xl bg-coral/5 p-3 text-center ring-1 ring-coral/20"><div><p className="text-sm font-bold text-coral-deep">移除该节点</p><p className="mt-1 text-xs text-ink-soft">该事件无法安全避让</p></div></div> : <div className="rounded-xl bg-mint/10 p-3 ring-1 ring-mint/30"><p className="text-[11px] font-semibold uppercase tracking-wide text-emerald-700">调整为</p><p className="mt-1 text-sm font-bold text-emerald-800">{change.toPlace ?? change.fromPlace}</p><p className="mt-1 text-xs text-emerald-700">{fmtRange(change.toStart, change.toEnd)}</p>{change.toCost !== undefined && <p className="text-xs text-emerald-700">费用 ¥{change.toCost}{change.fromCost !== undefined && <span className="ml-1 font-semibold">（{Number(change.toCost) - Number(change.fromCost) >= 0 ? "+" : ""}{Number(change.toCost) - Number(change.fromCost)}）</span>}</p>}</div>}</div></div>)}</div><div className="mt-5 flex flex-wrap gap-3"><Button disabled={!selected || startVoting.isPending} onClick={() => selected && startVoting.mutate(selected.id)}>{selected?.status === "VOTING" ? "投票进行中" : "选择并发起投票"}</Button><Link to={`/votes?trip=${tripId}`}><Button variant="ghost">去投票中心</Button></Link></div></Card>{toast}</>;
}

export function VotesPage() {
  const [choice, setChoice] = useState<VoteChoice | "">("");
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const scope = useTripScope();
  const tripId = scope.tripId;
  const tripQuery = useQuery({ queryKey: ["trip", tripId], queryFn: () => api.trip(tripId as number), enabled: tripId !== undefined });
  const groupId = tripQuery.data?.group?.id;
  const plansQuery = useQuery({ queryKey: ["plans", tripId], queryFn: () => api.plans(tripId as number), enabled: tripId !== undefined });
  const membersQuery = useQuery({ queryKey: ["members", groupId], queryFn: () => api.members(groupId as number), enabled: groupId !== undefined });
  const votingPlan = (plansQuery.data ?? []).find((plan) => plan.status === "VOTING");
  const votesQuery = useQuery({ queryKey: ["plan-votes", votingPlan?.id], queryFn: () => api.planVotes(votingPlan?.id as number), enabled: votingPlan?.id !== undefined });
  const members = membersQuery.data ?? [];
  const votes = votesQuery.data ?? [];
  const currentUser = getCurrentUser();
  const myMember = members.find((member) => member.user.id === currentUser?.id);
  const voteByMember = new Map(votes.map((vote) => [vote.memberName, vote.choice] as const));
  const approveCount = votes.filter((vote) => vote.choice === "APPROVE").length;
  const total = members.length || 1;
  const needed = Math.floor(total / 2) + 1;
  const submitVote = useMutation({
    mutationFn: () => api.submitVote(votingPlan?.id as number, myMember?.id as number, choice as VoteChoice),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["plan-votes", votingPlan?.id] }); show("你的投票已记录"); },
    onError: (error) => show(error instanceof Error ? error.message : "投票失败"),
  });
  const tally = useMutation({
    mutationFn: () => api.tally(votingPlan?.id as number),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["plans", tripId] });
      void queryClient.invalidateQueries({ queryKey: ["plan-votes", votingPlan?.id] });
      void queryClient.invalidateQueries({ queryKey: ["changelogs", tripId] });
      void queryClient.invalidateQueries({ queryKey: ["trip", tripId] });
      show("计票完成");
    },
    onError: (error) => show(error instanceof Error ? error.message : "计票失败"),
  });
  if (scope.isLoading || tripQuery.isLoading || plansQuery.isLoading || (groupId !== undefined && membersQuery.isLoading)) return <LoadingState label="正在读取投票…" />;
  if (tripQuery.isError || plansQuery.isError || membersQuery.isError) return <ErrorState onRetry={() => { void tripQuery.refetch(); void plansQuery.refetch(); void membersQuery.refetch(); }} />;
  if (!votingPlan) return <><PageHeader eyebrow="GROUP DECISION" title="投票中心" description="每个人的一票都在路线里留下位置。过半同意后，方案会自动应用到行程。" /><TripSwitcher scope={scope} /><EmptyState title="还没有进行中的投票" message="去“替代方案”页选择一个方案并发起投票，这里就会显示投票进度。" />{toast}</>;
  const label = (item: VoteChoice) => (item === "APPROVE" ? "赞成" : item === "REJECT" ? "反对" : "弃权");
  return <><PageHeader eyebrow="GROUP DECISION" title="投票中心" description="每个人的一票都在路线里留下位置。过半同意后，方案会自动应用到行程。" /><TripSwitcher scope={scope} /><Card className="p-6"><div className="flex flex-wrap items-center justify-between gap-4"><div><Badge tone="coral">投票中</Badge><h2 className="mt-3 font-display text-2xl font-bold">{votingPlan.title ?? "替代方案"}</h2><p className="mt-2 text-sm text-ink-soft">{(votingPlan.strategy ?? "").replace("MIN_", "")} · 额外成本 ¥{votingPlan.extraCost ?? 0} · 变更 {votingPlan.changedNodeCount ?? 0} 个节点</p></div><div className="text-right"><p className="font-mono text-3xl font-bold text-ink">{approveCount} / {total}</p><p className="text-xs text-ink-soft">赞成票</p></div></div><div className="mt-6 h-3 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-mint transition-all" style={{ width: `${Math.min(100, (approveCount / total) * 100)}%` }} /></div><p className="mt-2 text-xs text-ink-soft">{approveCount >= needed ? "已达到通过所需票数，群主可计票应用" : `还需要 ${needed - approveCount} 票赞成即可通过`}</p><div className="mt-7 grid gap-3 sm:grid-cols-2">{members.map((member) => { const c = voteByMember.get(member.user.name); return <div key={member.id} className="flex items-center justify-between rounded-xl bg-paper p-3"><div className="flex items-center gap-3"><div className="grid h-9 w-9 place-items-center rounded-full bg-coral/10 font-semibold text-coral">{member.user.name.slice(0, 1)}</div><span className="text-sm font-semibold">{member.user.name}</span></div><Badge tone={c === "APPROVE" ? "mint" : c === "REJECT" ? "coral" : "neutral"}>{c ? label(c) : "待投票"}</Badge></div>; })}</div><div className="mt-7 border-t border-slate-100 pt-6"><p className="text-sm font-semibold text-ink">你的选择{myMember ? "" : "（你不是该行程成员，无法投票）"}</p><div className="mt-3 flex flex-wrap gap-2">{(["APPROVE", "REJECT", "ABSTAIN"] as VoteChoice[]).map((item) => <button key={item} disabled={!myMember} onClick={() => setChoice(item)} className={`rounded-xl border px-5 py-3 text-sm font-semibold disabled:opacity-50 ${choice === item ? "border-coral bg-coral/10 text-coral-deep" : "border-slate-200 text-ink-soft"}`}>{label(item)}</button>)}</div><Button className="mt-4" disabled={!choice || !myMember || submitVote.isPending} onClick={() => submitVote.mutate()}>{submitVote.isPending ? "提交中…" : "提交投票"}</Button></div></Card><Card className="mt-5 flex flex-wrap items-center justify-between gap-4 p-6"><div><p className="eyebrow">TALLY RESULT</p><h2 className="mt-2 font-display text-xl font-bold">计票并应用</h2><p className="mt-1 text-sm text-ink-soft">达到法定人数（过半参与）后计票：赞成过半自动应用到行程并重算路线。</p></div><Button disabled={tally.isPending} onClick={() => tally.mutate()}>{tally.isPending ? "计票中…" : "计票并应用"}</Button></Card>{toast}</>;
}

export function ChangelogPage() {
  const dashboardQuery = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard });
  const tripId = dashboardQuery.data?.activeTrip?.id;
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["changelogs", tripId], queryFn: () => api.changelogs(tripId as number), enabled: tripId !== undefined });
  if (dashboardQuery.isLoading || isLoading) return <LoadingState label="正在读取变更记录…" />;
  if (dashboardQuery.isError || isError) return <ErrorState onRetry={() => { void dashboardQuery.refetch(); void refetch(); }} message={(dashboardQuery.error ?? error) instanceof Error ? (dashboardQuery.error ?? error)?.message : undefined} />;
  if (!data || data.length === 0) return <EmptyState title="还没有变更记录" message="当替代方案被采纳后，相关变更会显示在这里。" />;
  return <><PageHeader eyebrow="TRIP HISTORY" title="变更记录" description="每一次应变都有迹可循，费用、截止时间和关联方案都在这里。" /><Card className="divide-y divide-slate-100 p-6">{data.map((log) => <div key={log.id} className="flex flex-wrap items-start justify-between gap-5 py-5 first:pt-0 last:pb-0"><div><div className="flex items-center gap-2"><Badge tone="mint">已应用</Badge>{log.createdAt && <span className="font-mono text-xs text-ink-soft">{log.createdAt.replace("T", " ")}</span>}</div><h2 className="mt-3 font-semibold text-ink">{log.description ?? "未命名变更"}</h2><p className="mt-2 text-sm text-ink-soft">关联方案：{log.relatedPlan?.title ?? "暂无关联方案"}</p></div><div className="text-right"><p className="font-mono text-lg font-bold text-coral">{log.extraCost !== undefined ? `+¥${log.extraCost}` : "费用待定"}</p>{log.refundDeadline && <p className="mt-1 text-xs text-ink-soft">退款截止 {log.refundDeadline.replace("T", " ")}</p>}</div></div>)}</Card></>;
}

function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const diff = Math.max(0, Date.now() - then);
  const min = Math.floor(diff / 60000);
  if (min < 1) return "刚刚";
  if (min < 60) return `${min}分钟前`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours}小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}天前`;
  return new Date(iso).toLocaleDateString();
}

export function DiscussionsPage() {
  const [text, setText] = useState("");
  const [notificationsOpen, setNotificationsOpen] = useState(true);
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const scope = useTripScope({ includeCompleted: true });
  const tripId = scope.tripId;
  const tripTitle = scope.selectedTrip?.title ?? "当前行程";
  const postsQuery = useQuery({ queryKey: ["discussions", tripId], queryFn: () => api.discussions(tripId as number), enabled: tripId !== undefined });
  const notificationsQuery = useQuery({ queryKey: ["notifications"], queryFn: api.notifications });
  const notifications = (notificationsQuery.data ?? []).filter((item) => item.tripId === undefined || item.tripId === tripId);
  const posts = postsQuery.data ?? [];
  const publish = useMutation({
    mutationFn: () => api.postDiscussion(tripId as number, text.trim()),
    onSuccess: () => { setText(""); void queryClient.invalidateQueries({ queryKey: ["discussions", tripId] }); show("评论已发布"); },
    onError: (error) => show(error instanceof Error ? error.message : "发布失败"),
  });
  const like = useMutation({
    mutationFn: (postId: number) => api.likeDiscussion(postId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["discussions", tripId] }),
  });
  if (scope.isLoading) return <LoadingState label="正在读取行程…" />;
  if (tripId === undefined) return <><PageHeader eyebrow="TRIP DISCUSSION" title="讨论区" description="路线之外的想法，也值得被听见。" /><TripSwitcher scope={scope} /><EmptyState title="还没有行程" message="创建或加入一个行程后即可在这里讨论。" /></>;
  return <><PageHeader eyebrow="TRIP DISCUSSION" title="讨论区" description="路线之外的想法，也值得被听见。针对当前行程或方案，直接和旅伴讨论。" /><TripSwitcher scope={scope} /><Card className="mb-6 overflow-hidden p-0"><button type="button" aria-expanded={notificationsOpen} onClick={() => setNotificationsOpen((open) => !open)} className="flex w-full items-center justify-between gap-3 p-5 text-left transition hover:bg-paper"><div><p className="eyebrow">TRIP SIGNALS</p><h2 className="mt-2 font-display text-xl font-bold">当前行程通知 <span className="ml-2 text-sm font-normal text-ink-soft">{notifications.length} 条</span></h2></div><ChevronDown size={18} className={`text-ink-soft transition-transform ${notificationsOpen ? "rotate-180" : ""}`} /></button>{notificationsOpen && <div className="space-y-3 border-t border-slate-100 p-5">{notifications.length === 0 ? <p className="text-sm text-ink-soft">当前行程还没有新的通知。</p> : notifications.slice(0, 5).map((item) => <div key={item.id} className="rounded-xl bg-paper p-3"><p className="text-sm font-semibold text-ink">{item.title}</p><p className="mt-1 text-xs leading-5 text-ink-soft">{item.detail}</p><p className="mt-2 font-mono text-[10px] text-ink-soft">{relativeTime(item.createdAt)}</p></div>)}</div>}</Card><div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]"><div className="space-y-4">{postsQuery.isLoading && <LoadingState label="正在读取讨论…" />}{!postsQuery.isLoading && posts.length === 0 && <EmptyState title="还没有讨论" message="成为第一个发言的人。" />}{posts.map((post) => <Card key={post.id} className="p-5"><div className="flex gap-3"><div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-coral/10 font-display text-sm font-bold text-coral">{post.authorName.slice(0, 1)}</div><div className="min-w-0 flex-1"><div className="flex justify-between gap-3"><div><p className="text-sm font-semibold">{post.authorName}</p><p className="mt-1 text-[11px] text-ink-soft">{relativeTime(post.createdAt)} · {tripTitle}</p></div></div><p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-ink">{post.body}</p><div className="mt-4 flex gap-4 text-xs text-ink-soft"><button onClick={() => like.mutate(post.id)} className={`flex items-center gap-1.5 transition hover:text-coral ${post.likedByMe ? "text-coral" : ""}`}><ThumbsUp size={14} />{post.likes}</button></div></div></div></Card>)}</div><Card className="h-fit p-5"><p className="eyebrow">WRITE TO THE CREW</p><h2 className="mt-3 font-display text-xl font-bold">说点什么</h2><textarea value={text} onChange={(event) => setText(event.target.value)} maxLength={500} className="mt-5 min-h-32 w-full rounded-xl border border-slate-200 p-3 text-sm leading-6 focus:border-sky focus:outline-none" placeholder="分享一个你在意的细节…" /><div className="mt-3 flex items-center justify-between"><span className="text-xs text-ink-soft">{text.length}/500</span><Button disabled={!text.trim() || publish.isPending} onClick={() => publish.mutate()}><Send size={15} className="mr-2 inline" />发布</Button></div></Card></div>{toast}</>;
}

const NOTIFICATION_META: Record<string, { label: string; tone: "coral" | "sky" | "mint" }> = {
  "new-plans": { label: "新方案", tone: "sky" },
  "plan-accepted": { label: "采纳", tone: "mint" },
  "plan-rejected": { label: "否决", tone: "coral" },
};

export function NotificationsPage() {
  const { toast, show } = useToast();
  const queryClient = useQueryClient();
  const notificationsQuery = useQuery({ queryKey: ["notifications"], queryFn: api.notifications });
  const notifications = notificationsQuery.data ?? [];
  const markRead = useMutation({
    mutationFn: (id: number) => api.markNotificationRead(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
  const markAll = useMutation({
    mutationFn: () => api.markAllNotificationsRead(),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["notifications"] }); show("已全部标为已读"); },
  });
  const removeOne = useMutation({
    mutationFn: (id: number) => api.deleteNotification(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    onError: (error) => show(error instanceof Error ? error.message : "删除失败"),
  });
  const clearRead = useMutation({
    mutationFn: () => api.clearNotifications(true),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ["notifications"] }); show("已删除全部已读通知"); },
    onError: (error) => show(error instanceof Error ? error.message : "清理失败"),
  });
  const hasRead = notifications.some((item) => item.read);
  if (notificationsQuery.isLoading) return <LoadingState label="正在读取通知…" />;
  return <><PageHeader eyebrow="INBOX" title="通知" description="方案生成、采纳与否决的每一个关键节点，都会同步到这里。" action={<div className="flex gap-2"><Button variant="ghost" disabled={clearRead.isPending || !hasRead} onClick={() => clearRead.mutate()}>清空已读</Button><Button variant="ghost" disabled={markAll.isPending || notifications.length === 0} onClick={() => markAll.mutate()}>全部标为已读</Button></div>} />{notifications.length === 0 ? <EmptyState title="暂时没有通知" message="行程出现新方案或投票结果时，这里会第一时间提醒你。" /> : <Card className="divide-y divide-slate-100 p-5">{notifications.map((item) => { const meta = NOTIFICATION_META[item.type] ?? { label: item.type, tone: "sky" as const }; return <div key={item.id} className={`flex gap-4 py-5 first:pt-0 last:pb-0 ${item.read ? "opacity-60" : ""}`}><div className={`mt-1 grid h-10 w-10 shrink-0 place-items-center rounded-xl ${meta.tone === "coral" ? "bg-coral/10 text-coral" : meta.tone === "sky" ? "bg-sky/10 text-sky" : "bg-mint/10 text-mint"}`}><BellIcon type={item.type} /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><Badge tone={meta.tone}>{meta.label}</Badge>{!item.read && <span className="h-2 w-2 rounded-full bg-coral" />}</div><h2 className="mt-2 font-semibold text-ink">{item.title}</h2><p className="mt-1 text-sm text-ink-soft">{item.detail}</p><p className="mt-2 font-mono text-[10px] text-ink-soft">{relativeTime(item.createdAt)}</p></div><div className="flex shrink-0 flex-col items-end justify-center gap-2">{!item.read && <button onClick={() => markRead.mutate(item.id)} className="text-xs font-semibold text-sky">标为已读</button>}<button onClick={() => removeOne.mutate(item.id)} disabled={removeOne.isPending} aria-label="删除通知" className="text-xs font-semibold text-ink-soft transition hover:text-coral">删除</button></div></div>; })}</Card>}{toast}</>;
}

function BellIcon({ type }: { type: string }) { return type === "plan-accepted" ? <Check size={18} /> : type === "plan-rejected" ? <CircleAlert size={18} /> : <Sparkles size={18} />; }

export function SettingsPage() {
  const navigate = useNavigate();
  const userQuery = useQuery({ queryKey: ["auth", "me"], queryFn: api.me });
  if (userQuery.isLoading) return <LoadingState label="正在读取个人资料…" />;
  if (userQuery.isError || !userQuery.data) {
    return <ErrorState message={userQuery.error instanceof Error ? userQuery.error.message : "无法读取个人资料"} onRetry={() => void userQuery.refetch()} />;
  }
  return <SettingsContent user={userQuery.data} navigate={navigate} />;
}

function SettingsContent({ user, navigate }: { user: import("../auth").AuthUser; navigate: ReturnType<typeof useNavigate> }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [phone, setPhone] = useState(user.phone ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [profileMessage, setProfileMessage] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteError, setDeleteError] = useState("");
  const profileMutation = useMutation({
    mutationFn: () => api.updateProfile({ name: name.trim(), email: email.trim(), phone: phone.trim() || undefined }),
    onSuccess: (updatedUser) => {
      updateCurrentUser(updatedUser);
      void queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
      setProfileError("");
      setProfileMessage("资料已保存");
    },
    onError: (error) => {
      setProfileMessage("");
      setProfileError(error instanceof Error ? error.message : "保存资料失败");
    },
  });
  const passwordMutation = useMutation({
    mutationFn: () => api.changePassword(currentPassword, newPassword),
    onSuccess: () => {
      setCurrentPassword("");
      setNewPassword("");
      setPasswordError("");
      setPasswordMessage("密码已更新");
    },
    onError: (error) => {
      setPasswordMessage("");
      setPasswordError(error instanceof Error ? error.message : "修改密码失败");
    },
  });
  const deleteMutation = useMutation({
    mutationFn: api.deleteAccount,
    onSuccess: () => {
      signOut();
      navigate("/login");
    },
    onError: (error) => {
      setDeleteError(error instanceof Error ? error.message : "注销账号失败");
    },
  });
  const initial = user.name.slice(0, 1);
  return (
    <>
      <PageHeader eyebrow="YOUR IDENTITY" title="个人设置" description="你的资料来自账户数据库，更新后会立即同步到所有协作页面。" />
      <div className="grid gap-6 lg:grid-cols-[0.7fr_1.3fr]">
        <Card className="flex flex-col items-center p-7 text-center">
          <div className="grid h-24 w-24 place-items-center rounded-full bg-coral/10 font-display text-4xl font-bold text-coral ring-4 ring-coral/10">{initial}</div>
          <h2 className="mt-4 font-display text-xl font-bold">{user.name}</h2>
          <p className="mt-1 text-sm text-ink-soft">{user.email}</p>
          <p className="mt-2 text-xs text-ink-soft">{user.phone || "尚未填写手机号"}</p>
          <button className="mt-6 text-sm font-semibold text-coral" onClick={() => { signOut(); navigate("/login"); }}>退出登录</button>
        </Card>
        <div className="space-y-6">
          <Card className="p-6">
            <h2 className="font-display text-xl font-bold">个人资料</h2>
            <form className="mt-6 grid gap-4 sm:grid-cols-2" onSubmit={(event) => { event.preventDefault(); setProfileMessage(""); setProfileError(""); profileMutation.mutate(); }}>
              <label className="text-sm font-semibold">昵称<Input className="mt-2" value={name} onChange={(event) => setName(event.target.value)} required /></label>
              <label className="text-sm font-semibold">邮箱<Input className="mt-2" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
              <label className="text-sm font-semibold sm:col-span-2">手机号<Input className="mt-2" value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
              <div className="sm:col-span-2 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
                {profileError ? <p className="text-sm text-coral-deep" role="alert">{profileError}</p> : <p className="text-sm text-mint">{profileMessage}</p>}
                <Button type="submit" disabled={profileMutation.isPending}>{profileMutation.isPending ? "保存中…" : "保存资料"}</Button>
              </div>
            </form>
          </Card>
          <Card className="p-6">
            <h2 className="font-display text-xl font-bold">修改密码</h2>
            <form className="mt-6 grid gap-4 sm:grid-cols-2" onSubmit={(event) => { event.preventDefault(); setPasswordMessage(""); setPasswordError(""); passwordMutation.mutate(); }}>
              <label className="text-sm font-semibold sm:col-span-2">当前密码<Input className="mt-2" type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} minLength={6} required /></label>
              <label className="text-sm font-semibold sm:col-span-2">新密码<Input className="mt-2" type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={6} required /></label>
              <div className="sm:col-span-2 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-5">
                {passwordError ? <p className="text-sm text-coral-deep" role="alert">{passwordError}</p> : <p className="text-sm text-mint">{passwordMessage}</p>}
                <Button type="submit" disabled={passwordMutation.isPending}>{passwordMutation.isPending ? "更新中…" : "更新密码"}</Button>
              </div>
            </form>
          </Card>
          <Card className="border border-coral/25 bg-coral/5 p-6">
            <div className="flex items-start gap-3">
              <Trash2 className="mt-0.5 shrink-0 text-coral" size={18} />
              <div className="min-w-0">
                <h2 className="font-display text-xl font-bold text-ink">注销账号</h2>
                <p className="mt-2 text-sm leading-6 text-ink-soft">注销后将删除你的好友关系和小组成员资料，操作不可撤销。</p>
                {deleteError && <p className="mt-3 text-sm text-coral-deep" role="alert">{deleteError}</p>}
                <Button type="button" variant="ghost" className="mt-4 border border-coral/30 text-coral-deep hover:bg-coral/10" onClick={() => { setDeleteError(""); setDeleteOpen(true); }}>注销账号</Button>
              </div>
            </div>
          </Card>
        </div>
      </div>
      <Modal open={deleteOpen} title="确认注销账号" onClose={() => { if (!deleteMutation.isPending) setDeleteOpen(false); }}>
        <div className="space-y-5">
          <p className="text-sm leading-6 text-ink-soft">此操作会永久删除账号及其好友关系、群组成员资料。若你是任何群组的群主，请先转移群主或解散群组。</p>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="ghost" onClick={() => setDeleteOpen(false)} disabled={deleteMutation.isPending}>取消</Button>
            <Button type="button" className="bg-coral-deep hover:bg-coral" onClick={() => deleteMutation.mutate()} disabled={deleteMutation.isPending}>{deleteMutation.isPending ? "注销中…" : "确认注销"}</Button>
          </div>
        </div>
      </Modal>
    </>
  );
}

export function GuideDetailPage() {
  const { id = "1" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [commentBody, setCommentBody] = useState("");
  const { toast, show } = useToast();
  const guideId = Number(id);
  const guideQuery = useQuery({ queryKey: ["guide", id], queryFn: () => api.guide(guideId), enabled: Number.isFinite(guideId) });
  const guide = guideQuery.data;
  const authorQuery = useQuery({ queryKey: ["guides-by-author", guide?.author.id], queryFn: () => api.guidesByAuthor(guide?.author.id as number), enabled: guide?.author.id !== undefined });
  const commentsQuery = useQuery({ queryKey: ["guide-comments", guideId], queryFn: () => api.guideComments(guideId), enabled: Number.isFinite(guideId) });
  const addComment = useMutation({
    mutationFn: () => api.addGuideComment(guideId, commentBody.trim()),
    onSuccess: () => { setCommentBody(""); void queryClient.invalidateQueries({ queryKey: ["guide-comments", guideId] }); },
    onError: (error) => show(error instanceof Error ? error.message : "评论发布失败"),
  });
  if (guideQuery.isLoading) return <LoadingState label="正在读取攻略…" />;
  if (guideQuery.isError || !guide) return <ErrorState message={guideQuery.error instanceof Error ? guideQuery.error.message : "攻略不存在"} onRetry={() => void guideQuery.refetch()} />;
  const templateNodes = schedulePlannerPlaces(getCitySuggestions(guide.city).slice(0, 2).map(offlinePlannerPlace), "2025-05-03", guide.days);
  const otherGuides = (authorQuery.data ?? []).filter((item) => item.id !== guide.id);
  const comments = commentsQuery.data ?? [];
  return <><div className="mb-7 flex items-center gap-3 text-sm text-ink-soft"><Link to="/guides" className="hover:text-ink">攻略社区</Link><span>/</span><span className="text-ink">{guide.title}</span></div><div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]"><div><Card className="overflow-hidden"><div className="h-72 md:h-96"><ImageFallback src={guide.cover} alt={guide.title} city={guide.city} /></div><div className="p-6 md:p-8"><div className="flex flex-wrap items-center gap-2"><Badge tone="coral">{guide.theme}</Badge><Badge tone="neutral">{guide.city} · {guide.days} 天</Badge><span className="ml-auto flex items-center gap-1 text-sm"><Heart size={16} className="text-coral" />{guide.saves} 收藏</span></div><h1 className="mt-4 font-display text-3xl font-bold text-ink">{guide.title}</h1><p className="mt-4 text-sm leading-7 text-ink-soft">{guide.description} 这是一份把具体地点、留白时间和真实预算放在一起的可复用路线。</p><div className="mt-5 flex flex-wrap gap-2">{guide.tags.map((tag) => <Badge key={tag} tone="sky">#{tag}</Badge>)}</div></div></Card><Card className="mt-5 p-6 md:p-8"><p className="eyebrow">TEMPLATE ITINERARY</p><h2 className="mt-2 font-display text-2xl font-bold">路线模板</h2><div className="mt-7"><RouteTrail nodes={templateNodes} /></div></Card><Card className="mt-5 p-6 md:p-8"><div className="flex items-end justify-between gap-3"><div><p className="eyebrow">COMMENTS</p><h2 className="mt-2 font-display text-2xl font-bold">评论区</h2></div><Badge tone="neutral">{comments.length} 条</Badge></div><div className="mt-6 space-y-4">{commentsQuery.isLoading ? <LoadingState label="正在读取评论…" /> : comments.length === 0 ? <p className="rounded-card bg-paper p-5 text-sm text-ink-soft">还没有评论，来留下第一句建议吧。</p> : comments.map((comment) => <div key={comment.id} className="flex gap-3 border-b border-slate-100 pb-4 last:border-0 last:pb-0"><div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-mint/15 font-semibold text-ink">{comment.authorName.slice(0, 1)}</div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><p className="text-sm font-semibold text-ink">{comment.authorName}</p><span className="text-[11px] text-ink-soft">{relativeTime(comment.createdAt)}</span></div><p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-ink-soft">{comment.body}</p></div></div>)}</div><div className="mt-6 border-t border-slate-100 pt-5"><textarea value={commentBody} onChange={(event) => setCommentBody(event.target.value)} maxLength={500} className="min-h-24 w-full rounded-xl border border-slate-200 p-3 text-sm leading-6 focus:border-sky focus:outline-none" placeholder="分享你的体验或给作者一点建议…" /><div className="mt-3 flex items-center justify-between"><span className="text-xs text-ink-soft">{commentBody.length}/500</span><Button disabled={!commentBody.trim() || addComment.isPending} onClick={() => addComment.mutate()}><Send size={15} className="mr-2 inline" />{addComment.isPending ? "发布中…" : "发表评论"}</Button></div></div></Card></div><div className="space-y-5"><Card className="sticky top-24 p-6"><p className="eyebrow">READY TO GO?</p><h2 className="mt-3 font-display text-2xl font-bold">把这条路线带回你的小组</h2><p className="mt-3 text-sm leading-6 text-ink-soft">选择小组和实际出发日期，模板中的第 1 天 / 第 2 天会自动映射到你的真实行程。</p><div className="mt-6 flex items-center justify-between border-y border-slate-100 py-4"><span className="text-sm text-ink-soft">预计人均</span><span className="font-mono text-xl font-bold">¥{guide.price.toLocaleString()}</span></div><Button className="mt-5 w-full" onClick={() => setOpen(true)}>攻略纳用</Button></Card><Card className="p-6"><p className="eyebrow">BY {guide.author.name.toUpperCase()}</p><div className="mt-4 flex items-center gap-3"><div className="grid h-10 w-10 place-items-center rounded-full bg-mint/15 font-semibold text-ink">{guide.author.name.slice(0, 1)}</div><div><p className="text-sm font-semibold">{guide.author.name}</p><p className="text-xs text-ink-soft">{guide.rating.toFixed(1)} 分 · {guide.reviews} 条评价</p></div></div><h3 className="mt-6 border-t border-slate-100 pt-5 font-display text-lg font-bold">作者的其他攻略 / 笔记</h3><div className="mt-4 space-y-3">{authorQuery.isLoading ? <p className="text-sm text-ink-soft">正在读取作者笔记…</p> : otherGuides.length === 0 ? <p className="text-sm text-ink-soft">作者暂时还没有其他已发布攻略。</p> : otherGuides.map((other) => <Link key={other.id} to={`/guides/${other.id}`} className="block rounded-xl bg-paper p-3 transition hover:-translate-y-0.5 hover:bg-sky/5"><p className="text-sm font-semibold text-ink">{other.title}</p><p className="mt-1 text-xs text-ink-soft">{other.city} · {other.days} 天 · {other.rating.toFixed(1)} 分</p></Link>)}</div></Card></div></div><ApplyGuideModal guide={guide} open={open} onClose={() => setOpen(false)} onDone={(tripId) => { setOpen(false); show("攻略纳用完成，正在打开新行程"); navigate(`/trips/${tripId}`); }} />{toast}</>;
}

function ApplyGuideModal({ guide, open, onClose, onDone }: { guide: TravelGuide; open: boolean; onClose: () => void; onDone: (tripId: number) => void }) {
  const queryClient = useQueryClient();
  const groupsQuery = useQuery({ queryKey: ["groups"], queryFn: api.groups, enabled: open });
  const [groupId, setGroupId] = useState<number | "">("");
  const [departDate, setDepartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [error, setError] = useState("");
  const groups = groupsQuery.data ?? [];
  const selectedGroupId = groupId === "" ? groups[0]?.id : groupId;
  const templateNodes = schedulePlannerPlaces(getCitySuggestions(guide.city).map(offlinePlannerPlace), departDate, guide.days);
  const applyMutation = useMutation({
    mutationFn: async () => {
      if (selectedGroupId === undefined) throw new Error("请先创建或加入一个小组");
      if (templateNodes.length === 0) throw new Error(`本地地点库暂时没有 ${guide.city} 的模板地点`);
      const trip = await api.createTrip(selectedGroupId, {
        title: guide.title,
        status: "DRAFT",
        startDate: departDate,
        endDate: dateAfter(departDate, guide.days - 1),
        totalBudget: templateNodes.reduce((sum, node) => sum + node.cost, 0),
      });
      for (const node of templateNodes) {
        await api.addNode(trip.id, {
          name: node.name,
          placeName: node.placeName,
          latitude: node.latitude,
          longitude: node.longitude,
          nodeType: node.nodeType,
          plannedStart: node.plannedStart,
          plannedEnd: node.plannedEnd,
          cost: node.cost,
          sequenceOrder: node.sequenceOrder,
          status: node.status,
        });
      }
      return trip.id;
    },
    onSuccess: (tripId) => {
      void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
      void queryClient.invalidateQueries({ queryKey: ["groups"] });
      onDone(tripId);
    },
    onError: (mutationError) => setError(mutationError instanceof Error ? mutationError.message : "纳用失败，请稍后再试"),
  });
  const previewNodes = templateNodes.slice(0, 3);
  return <Modal open={open} title="攻略纳用" onClose={onClose}><div className="space-y-5">
    <label className="block text-sm font-semibold">目标小组
      {groupsQuery.isLoading ? <p className="mt-2 text-sm text-ink-soft">正在读取小组…</p> : groups.length === 0 ? <p className="mt-2 text-sm text-coral-deep">还没有小组，先去创建或加入一个小组。</p> : <select value={selectedGroupId ?? ""} onChange={(event) => setGroupId(Number(event.target.value))} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm">{groups.map((group) => <option key={group.id} value={group.id}>{group.name} · {group.members?.length ?? group.memberCount ?? 0} 位成员</option>)}</select>}
    </label>
    <label className="block text-sm font-semibold">实际出发日期<input type="date" value={departDate} onChange={(event) => setDepartDate(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label>
    <div className="rounded-xl bg-paper p-4"><p className="text-sm font-semibold">相对 → 绝对时间</p><div className="mt-3 space-y-2 text-xs text-ink-soft">{previewNodes.length === 0 ? <p>本地地点库暂时没有 {guide.city} 的模板地点。</p> : previewNodes.map((node) => <p key={node.sequenceOrder}><span className="font-mono">{node.placeName}</span><span className="mx-2">→</span>{node.plannedStart.replace("T", " ").slice(0, 16)}</p>)}{templateNodes.length > previewNodes.length && <p>… 共 {templateNodes.length} 个节点</p>}</div></div>
    {error && <div className="flex items-start gap-3 rounded-xl bg-coral/10 p-4 text-sm leading-6 text-coral-deep"><CircleAlert size={18} className="mt-0.5 shrink-0" />{error}</div>}
    <Button className="w-full" disabled={applyMutation.isPending || groups.length === 0} onClick={() => { setError(""); applyMutation.mutate(); }}>{applyMutation.isPending ? "正在创建行程…" : "确认纳用并创建行程"}</Button>
  </div></Modal>;
}

export function AdminPage() {
  const statsQuery = useQuery({ queryKey: ["admin-stats"], queryFn: api.adminStats });
  if (statsQuery.isLoading) return <LoadingState label="正在统计全局数据…" />;
  if (statsQuery.isError || !statsQuery.data) return <ErrorState onRetry={() => void statsQuery.refetch()} />;
  const stats = statsQuery.data;
  const maxPlace = Math.max(1, ...stats.topPlaces.map((place) => place.count));
  const hitRate = stats.totalEvents > 0 ? ((stats.hitEvents / stats.totalEvents) * 100).toFixed(1) : "0.0";
  const adoptRate = stats.totalPlans > 0 ? ((stats.acceptedPlans / stats.totalPlans) * 100).toFixed(1) : "0.0";
  const bucketTotal = Math.max(1, stats.budgetBuckets.reduce((sum, bucket) => sum + bucket.count, 0));
  const bucketColors = ["#FF5B4C", "#17C3A2", "#2F9BFF", "#FFC53D"];
  const gradientStops = stats.budgetBuckets
    .reduce<{ stops: string[]; cursor: number }>((acc, bucket, index) => {
      const end = acc.cursor + (bucket.count / bucketTotal) * 100;
      acc.stops.push(`${bucketColors[index % bucketColors.length]} ${acc.cursor}% ${end}%`);
      return { stops: acc.stops, cursor: end };
    }, { stops: [], cursor: 0 })
    .stops.join(", ");
  return <><PageHeader eyebrow="OPERATIONS / ADMIN" title="数据统计后台" description="看见大家如何出发、哪里被打断，以及哪些替代方案真正帮上了忙。" action={<Badge tone="mint">管理员视图</Badge>} /><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><Stat label="总行程数" value={String(stats.totalTrips)} detail="全部小组累计" tone="coral" /><Stat label="预算中位数" value={`¥${stats.budgetMedian.toLocaleString()}`} detail={`总预算 ¥${stats.totalBudget.toLocaleString()}`} tone="mint" /><Stat label="事件命中率" value={`${hitRate}%`} detail={`${stats.hitEvents} / ${stats.totalEvents} 条事件影响了行程`} tone="sky" /><Stat label="方案采纳率" value={`${adoptRate}%`} detail={`${stats.acceptedPlans} / ${stats.totalPlans} 个方案`} tone="sun" /></div><div className="mt-6 grid gap-5 lg:grid-cols-[1.2fr_0.8fr]"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">TOP PLACES</p><h2 className="mt-2 font-display text-xl font-bold">热门地点</h2></div><span className="text-xs text-ink-soft">按行程节点统计</span></div><div className="mt-7 space-y-5">{stats.topPlaces.length === 0 ? <p className="text-sm text-ink-soft">还没有行程节点数据。</p> : stats.topPlaces.map((bar) => <div key={bar.label} className="flex items-center gap-4"><span className="w-24 truncate text-sm font-semibold text-ink">{bar.label}</span><div className="h-3 flex-1 rounded-full bg-paper"><div className="h-full rounded-full bg-coral" style={{ width: `${(bar.count / maxPlace) * 100}%` }} /></div><span className="font-mono text-xs text-ink-soft">{bar.count}</span></div>)}</div></Card><Card className="p-6"><p className="eyebrow">BUDGET DISTRIBUTION</p><h2 className="mt-2 font-display text-xl font-bold">预算分布</h2><div className="mx-auto mt-7 grid h-44 w-44 place-items-center rounded-full" style={{ background: `conic-gradient(${gradientStops})` }}><div className="grid h-28 w-28 place-items-center rounded-full bg-white text-center"><p className="font-display text-2xl font-bold">¥{stats.totalBudget.toLocaleString()}</p><p className="text-[10px] text-ink-soft">总计划预算</p></div></div><div className="mt-5 flex flex-wrap justify-center gap-3 text-xs text-ink-soft">{stats.budgetBuckets.map((bucket, index) => <span key={bucket.label}><i className="mr-1 inline-block h-2 w-2 rounded-full" style={{ backgroundColor: bucketColors[index % bucketColors.length] }} />{bucket.label} · {bucket.count}</span>)}</div></Card></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">EVENT SOURCES</p><h2 className="mt-2 font-display text-xl font-bold">事件源</h2></div><Link to="/events"><Button variant="ghost">管理事件源</Button></Link></div><div className="mt-5 grid gap-3 md:grid-cols-2"><div className="rounded-xl bg-mint/10 p-4"><p className="text-sm font-semibold text-emerald-800">天气服务</p><p className="mt-2 text-xs text-emerald-700/70">可在事件监测页手动拉取各行程的天气事件</p></div><div className="rounded-xl bg-sky/10 p-4"><p className="text-sm font-semibold text-blue-800">城市事件</p><p className="mt-2 text-xs text-blue-700/70">施工、交通管制、景区开放调整与大型活动会进入统一监测流程</p></div></div></Card></>;
}

export function RouteMapPage() {
  const scope = useTripScope();
  const tripId = scope.tripId;
  const tripQuery = useQuery({ queryKey: ["trip", tripId], queryFn: () => api.trip(tripId as number), enabled: tripId !== undefined });
  const [modes, setModes] = useState<Record<number, RouteModeType>>({});
  const [selectedNode, setSelectedNode] = useState<ItineraryNode | null>(null);
  const routeNodes = [...(tripQuery.data?.itineraryNodes ?? [])].sort((left, right) => left.sequenceOrder - right.sequenceOrder);
  const routeQueries = useQueries({ queries: routeNodes.slice(0, -1).map((from, index) => {
    const to = routeNodes[index + 1];
    const mode = modes[from.id] ?? "DRIVE";
    return { queryKey: ["map-route", tripId, from.id, to.id, mode], queryFn: () => api.mapRoute(from.latitude, from.longitude, to.latitude, to.longitude, mode === "WALK" ? "walking" : mode === "TRANSIT" ? "riding" : "driving"), enabled: tripId !== undefined };
  }) });
  if (scope.isLoading || tripQuery.isLoading) return <LoadingState label="正在加载路线…" />;
  if (tripQuery.isError) return <ErrorState onRetry={() => void tripQuery.refetch()} />;
  if (!tripQuery.data) return <EmptyState title="暂无可展示路线" message="先创建一段包含至少两个节点的行程。" />;
  const trip = tripQuery.data;
  const nodes = routeNodes;
  if (nodes.length < 2) return <><PageHeader eyebrow="TRIP · ROUTE MAP" title={trip.title} description="至少需要两个行程节点才能展开地图路线。" /><TripSwitcher scope={scope} /><EmptyState title="路线节点不足" message="至少需要两个行程节点才能展示路线。" /></>;
  const segments = nodes.slice(0, -1).map((from, index) => {
    const to = nodes[index + 1];
    const fallback = haversine(from.latitude, from.longitude, to.latitude, to.longitude);
    const route = routeQueries[index]?.data;
    return { from, to, baseDistance: route?.available && route.distanceMeters !== undefined ? route.distanceMeters / 1000 : fallback, routeAvailable: route?.available ?? false };
  });
  const totalDistance = segments.reduce((sum, item) => sum + estimateSegment(item.baseDistance, modes[item.from.id] ?? "DRIVE").distance, 0);
  const totalMinutes = segments.reduce((sum, item) => sum + estimateSegment(item.baseDistance, modes[item.from.id] ?? "DRIVE").minutes, 0);
  return <><PageHeader eyebrow="TRIP · TODAY'S LEGS" title={trip.title} description={`${totalDistance.toFixed(1)} km · 约 ${totalMinutes} 分钟 · ${segments.length} 段`} action={<Link to={`/plans?trip=${trip.id}`}><Button>重新规划路线</Button></Link>} /><TripSwitcher scope={scope} /><div className="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]"><div><BaiduMap nodes={nodes} onMarkerClick={setSelectedNode} className="h-[480px]" /><p className="mt-3 text-xs text-ink-soft">路线距离优先使用百度地图查询，服务不可用时自动回退到坐标估算。</p></div><div className="space-y-4">{segments.map((segment, index) => <RouteLeg key={segment.from.id} index={index} from={segment.from} to={segment.to} distance={segment.baseDistance} mode={modes[segment.from.id] ?? "DRIVE"} onModeChange={(mode) => setModes((current) => ({ ...current, [segment.from.id]: mode }))} onPlaceClick={setSelectedNode} />)}</div></div>{selectedNode && <MapNodeDetailSheet node={selectedNode} trip={trip} onClose={() => setSelectedNode(null)} />}</>;
}

type RouteModeType = "WALK" | "DRIVE" | "TRANSIT";
function RouteLeg({ index, from, to, distance, mode, onModeChange, onPlaceClick }: { index: number; from: ItineraryNode; to: ItineraryNode; distance: number; mode: RouteModeType; onModeChange: (mode: RouteModeType) => void; onPlaceClick: (node: ItineraryNode) => void }) {
  const options: { value: RouteModeType; label: string; icon: typeof Car }[] = [{ value: "WALK", label: "步行", icon: Footprints }, { value: "DRIVE", label: "驾车", icon: Car }, { value: "TRANSIT", label: "公交", icon: Bus }];
  const selected = estimateSegment(distance, mode);
  const ModeIcon = options.find((option) => option.value === mode)?.icon ?? Car;
  const fromDetail = getPlaceDetail(from.placeName, from.nodeType);
  const toDetail = getPlaceDetail(to.placeName, to.nodeType);
  return <article className="relative overflow-hidden rounded-card border border-slate-100 bg-surface shadow-soft"><div className="absolute -left-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="absolute -right-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="grid gap-5 p-5 md:grid-cols-[1fr_auto_1fr] md:items-center md:p-6"><PlaceStop label="出发" node={from} detail={fromDetail} onClick={() => onPlaceClick(from)} /><div className="flex items-center gap-3 text-coral md:flex-col"><span className="font-mono text-[10px] text-ink-soft">LEG {String(index + 1).padStart(2, "0")}</span><div className="flex flex-1 items-center md:w-28"><span className="h-px flex-1 border-t-2 border-dashed border-coral/40" /><span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-coral/10 text-coral"><ModeIcon size={17} /></span><span className="h-px flex-1 border-t-2 border-dashed border-coral/40 animate-route" /></div><span className="font-mono text-[10px] text-ink-soft">{selected.distance.toFixed(1)} KM</span></div><PlaceStop label="到达" node={to} detail={toDetail} onClick={() => onPlaceClick(to)} /></div><div className="flex flex-wrap gap-2 border-t border-dashed border-slate-200 p-4 md:px-6">{options.map(({ value, label, icon: Icon }) => { const estimate = estimateSegment(distance, value); return <button type="button" key={value} onClick={() => onModeChange(value)} className={`flex items-center gap-2 rounded-full px-3 py-2 text-xs font-semibold transition focus-visible:outline-offset-2 ${mode === value ? "bg-ink text-white" : "bg-paper text-ink-soft hover:text-ink"}`}><Icon size={14} />{label}<span className={mode === value ? "text-white/60" : "text-ink-soft"}>{estimate.distance.toFixed(1)} km · {estimate.minutes} 分</span></button>; })}</div></article>;
}

function PlaceStop({ label, node, detail, onClick }: { label: string; node: ItineraryNode; detail: ReturnType<typeof getPlaceDetail>; onClick: () => void }) {
  return <button type="button" onClick={onClick} className="min-w-0 text-left transition hover:-translate-y-0.5 focus-visible:outline-offset-2"><p className="font-mono text-[10px] tracking-[0.18em] text-ink-soft">{label} · {detail.code ?? `WP${node.sequenceOrder}`}</p><p className="mt-2 truncate font-display text-xl font-bold text-ink">{node.placeName}</p><p className="mt-1 font-mono text-xs text-ink-soft">{formatLegTime(node.plannedStart)}—{formatLegTime(node.plannedEnd)}</p><p className="mt-2 text-xs text-sky">查看地点详情 →</p></button>;
}

function haversine(lat1: number, lon1: number, lat2: number, lon2: number) {
  const rad = Math.PI / 180;
  const a = Math.sin((lat2 - lat1) * rad / 2) ** 2 + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin((lon2 - lon1) * rad / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function estimateSegment(distance: number, mode: RouteModeType) {
  const factor = mode === "WALK" ? 1.3 : mode === "TRANSIT" ? 1.65 : 1.45;
  const speed = mode === "WALK" ? 4.5 : mode === "TRANSIT" ? 20 : 28;
  return { distance: distance * factor, minutes: Math.max(8, Math.round((distance * factor / speed) * 60 + (mode === "TRANSIT" ? 8 : mode === "DRIVE" ? 5 : 0))) };
}

function formatLegTime(value: string) {
  return new Date(value).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}

const EXPENSE_CATEGORIES = ["餐饮", "住宿", "交通", "门票", "购物", "其他"];

export function BudgetPage() {
  const queryClient = useQueryClient();
  const { toast, show } = useToast();
  const scope = useTripScope();
  const trip = scope.selectedTrip;
  const tripId = scope.tripId;
  const expensesQuery = useQuery({ queryKey: ["expenses", tripId], queryFn: () => api.expenses(tripId as number), enabled: tripId !== undefined });
  const [open, setOpen] = useState(false);
  const [label, setLabel] = useState("");
  const [category, setCategory] = useState(EXPENSE_CATEGORIES[0]);
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  useEffect(() => {
    const resetTimer = window.setTimeout(() => {
      setLabel("");
      setAmount("");
      setError("");
      setOpen(false);
    }, 0);
    return () => window.clearTimeout(resetTimer);
  }, [tripId]);
  const addMutation = useMutation({
    mutationFn: () => api.addExpense(tripId as number, { label: label.trim(), category, amount: Number(amount) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["expenses", tripId] });
      void queryClient.invalidateQueries({ queryKey: ["settlement", tripId] });
      setLabel(""); setAmount(""); setOpen(false); show("费用已记录");
    },
    onError: (mutationError) => setError(mutationError instanceof Error ? mutationError.message : "记录失败，请稍后再试"),
  });
  if (scope.isLoading) return <LoadingState label="正在加载预算…" />;
  if (!trip) return <EmptyState title="还没有行程" message="先创建一段行程，再记录费用。" />;
  const expenses = expensesQuery.data ?? [];
  const totalBudget = trip.totalBudget ?? 0;
  const spent = expenses.reduce((sum, item) => sum + item.amount, 0);
  const percent = totalBudget > 0 ? Math.round((spent / totalBudget) * 100) : 0;
  const submit = () => {
    setError("");
    if (!label.trim() || !Number.isFinite(Number(amount)) || Number(amount) <= 0) { setError("请填写费用名称和有效金额。"); return; }
    addMutation.mutate();
  };
  return <><PageHeader eyebrow="BUDGET & EXPENSES" title={`预算与费用 · ${trip.title ?? ""}`} description="预算不是一条紧绷的线，而是让团队知道什么时候该留一点余地。" action={<Button onClick={() => setOpen(true)}>记录一笔费用</Button>} /><TripSwitcher scope={scope} /><div className="grid gap-5 md:grid-cols-3"><Stat label="总预算" value={`¥${totalBudget.toLocaleString()}`} detail="行程总预算" tone="coral" /><Stat label="已用金额" value={`¥${spent.toLocaleString()}`} detail={totalBudget > 0 ? `${percent}% 已使用` : "尚未设置预算"} tone="mint" /><Stat label="剩余预算" value={`¥${Math.max(0, totalBudget - spent).toLocaleString()}`} detail={spent > totalBudget && totalBudget > 0 ? "已超支" : "仍在预算内"} tone="sky" /></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><h2 className="font-display text-xl font-bold">费用明细</h2><Badge tone={spent > totalBudget && totalBudget > 0 ? "coral" : "mint"}>{spent > totalBudget && totalBudget > 0 ? "已超支" : "未超支"}</Badge></div><div className="mt-5 divide-y divide-slate-100">{expensesQuery.isLoading ? <p className="py-4 text-sm text-ink-soft">正在读取费用…</p> : expenses.length === 0 ? <p className="py-4 text-sm text-ink-soft">还没有费用记录，点右上角「记录一笔费用」开始。</p> : expenses.map((item) => <div key={item.id} className="flex justify-between py-4 text-sm"><span className="text-ink-soft">{item.category} · {item.label}<span className="ml-2 text-xs">by {item.payerName}</span></span><span className="font-mono font-bold">¥{item.amount.toLocaleString()}</span></div>)}</div></Card>
  <Modal open={open} title="记录一笔费用" onClose={() => setOpen(false)}><div className="space-y-4">
    <label className="block text-sm font-semibold">费用名称<Input className="mt-2" value={label} onChange={(event) => setLabel(event.target.value)} placeholder="例如：外滩茂悦一晚" /></label>
    <label className="block text-sm font-semibold">类别<select value={category} onChange={(event) => setCategory(event.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm">{EXPENSE_CATEGORIES.map((item) => <option key={item} value={item}>{item}</option>)}</select></label>
    <label className="block text-sm font-semibold">金额 (¥)<Input className="mt-2" type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} /></label>
    {error && <p className="text-sm text-coral-deep" role="alert">{error}</p>}
    <Button className="w-full" disabled={addMutation.isPending} onClick={submit}>{addMutation.isPending ? "保存中…" : "保存费用"}</Button>
  </div></Modal>{toast}</>;
}

export function SettlementPage() {
  const navigate = useNavigate();
  const scope = useTripScope();
  const tripId = scope.tripId;
  const settlementQuery = useQuery({ queryKey: ["settlement", tripId], queryFn: () => api.settlement(tripId as number), enabled: tripId !== undefined });
  if (scope.isLoading || settlementQuery.isLoading) return <LoadingState label="正在计算分账…" />;
  if (settlementQuery.isError) return <ErrorState onRetry={() => void settlementQuery.refetch()} />;
  if (!tripId) return <EmptyState title="还没有行程" message="先创建一段行程，再进行分账结算。" />;
  const settlement = settlementQuery.data;
  if (!settlement) return <EmptyState title="暂无结算数据" message="先在预算页记录几笔共同消费。" />;
  return <><PageHeader eyebrow="AA SETTLEMENT" title="分账与结算" description="共同消费按人平分，尽量用最少的转账次数结清。" action={<Button onClick={() => navigate(`/budget?trip=${tripId}`)}>录入共同消费</Button>} /><TripSwitcher scope={scope} /><div className="grid gap-5 md:grid-cols-2"><Stat label="共同消费总额" value={`¥${settlement.total.toLocaleString()}`} detail={`${settlement.members.length} 位成员参与平摊`} tone="coral" /><Stat label="人均应摊" value={`¥${settlement.perPerson.toLocaleString()}`} detail="按成员人数平均" tone="mint" /></div><div className="mt-5 grid gap-5 lg:grid-cols-[1fr_0.8fr]"><Card className="p-6"><p className="eyebrow">WHO PAYS WHO</p><h2 className="mt-2 font-display text-xl font-bold">建议结算</h2><div className="mt-6 space-y-3">{settlement.transfers.length === 0 ? <p className="text-sm text-ink-soft">目前不需要任何转账——大家已经结清或还没有共同消费。</p> : settlement.transfers.map((transfer) => <SettlementRow key={`${transfer.fromId}-${transfer.toId}`} from={transfer.fromName} to={transfer.toName} amount={`¥${transfer.amount.toLocaleString()}`} />)}</div></Card><Card className="p-6"><p className="eyebrow">TRIP SHARES</p><h2 className="mt-2 font-display text-xl font-bold">成员账单</h2><div className="mt-6 space-y-4">{settlement.members.map((member) => <div key={member.userId} className="flex justify-between text-sm"><span>{member.name}</span><span className="font-mono">¥{member.paid.toLocaleString()} 已垫付 · {member.balance >= 0 ? `应收 ¥${member.balance.toLocaleString()}` : `应付 ¥${Math.abs(member.balance).toLocaleString()}`}</span></div>)}</div></Card></div></>;
}

function SettlementRow({ from, to, amount }: { from: string; to: string; amount: string }) {
  return <div className="flex items-center justify-between rounded-xl bg-paper p-4"><div className="flex items-center gap-3 text-sm font-semibold"><span>{from}</span><ArrowRight size={15} className="text-coral" /><span>{to}</span></div><span className="font-mono text-sm font-bold text-coral">{amount}</span></div>;
}

function Stat({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: "coral" | "mint" | "sky" | "sun" }) {
  return <Card className="p-5"><div className={`mb-5 h-2 w-10 rounded-full ${tone === "coral" ? "bg-coral" : tone === "mint" ? "bg-mint" : tone === "sky" ? "bg-sky" : "bg-sun"}`} /><p className="text-xs text-ink-soft">{label}</p><p className="mt-2 font-display text-2xl font-bold">{value}</p><p className="mt-1 text-xs text-ink-soft">{detail}</p></Card>;
}
