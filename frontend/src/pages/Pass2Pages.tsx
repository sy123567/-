import { useState } from "react";
import { ArrowRight, Bus, Car, Check, ChevronRight, CircleAlert, Copy, Footprints, Heart, MessageCircle, MoreHorizontal, Plus, Send, Shield, SlidersHorizontal, Sparkles, ThumbsUp, Trash2, UserMinus, UserPlus, Users } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { activeTrip, groupMembers, guides, plans } from "../mocks/data";
import { getPlaceDetail } from "../mocks/places";
import { Badge, BoardingPassCard, Button, Card, EventIcon, Input, PageHeader, RiskGauge, RouteTrail } from "../components/ui";
import { ImageFallback, Modal, Toast } from "../components/pass2";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import { PlaceDetailSheet } from "../components/PlaceDetailSheet";
import { signOut, updateCurrentUser } from "../auth";

function useToast() {
  const [message, setMessage] = useState("");
  return { toast: message ? <Toast message={message} onClose={() => setMessage("")} /> : null, show: setMessage };
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
  const { id = "1" } = useParams();
  const member = groupMembers.find((item) => item.id === Number(id)) ?? groupMembers[0];
  const [places, setPlaces] = useState(member.constraint.mustVisitPlaces);
  const [newPlace, setNewPlace] = useState("");
  const { toast, show } = useToast();
  return <><PageHeader eyebrow={`CONSTRAINTS / ${member.user.name}`} title="成员约束" description="约束不是限制，而是让每个人都能放心上车的底线。" action={<Button onClick={() => show("成员约束已保存")}>保存修改</Button>} /><div className="grid gap-5 lg:grid-cols-2"><Card className="p-6"><h2 className="font-display text-xl font-bold">可用时间与预算</h2><div className="mt-5 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold text-ink">最早可出发<input type="date" defaultValue={member.constraint.availableFrom} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label><label className="text-sm font-semibold text-ink">最晚可返回<input type="date" defaultValue={member.constraint.availableTo} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label></div><label className="mt-5 block text-sm font-semibold text-ink">人均预算上限<input type="number" defaultValue={member.constraint.maxBudget} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label><label className="mt-5 block text-sm font-semibold text-ink">体力水平<select defaultValue={member.constraint.fitnessLevel} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"><option>LOW</option><option>MEDIUM</option><option>HIGH</option></select></label></Card><Card className="p-6"><h2 className="font-display text-xl font-bold">想去与需要避开的事</h2><p className="mt-2 text-sm text-ink-soft">这些地点会优先进入初始计划，饮食和无障碍需求会影响路线筛选。</p><div className="mt-5 flex flex-wrap gap-2">{places.map((place) => <button key={place} onClick={() => setPlaces(places.filter((item) => item !== place))} className="rounded-full bg-coral/10 px-3 py-2 text-xs font-semibold text-coral-deep">#{place} ×</button>)}</div><div className="mt-4 flex gap-2"><Input value={newPlace} onChange={(event) => setNewPlace(event.target.value)} placeholder="添加必访地点" /><Button variant="secondary" onClick={() => { if (newPlace) { setPlaces([...places, newPlace]); setNewPlace(""); } }}>添加</Button></div><div className="mt-7 grid gap-4 sm:grid-cols-2"><label className="text-sm font-semibold">饮食需求<textarea defaultValue={member.constraint.dietaryNeeds.join("、")} className="mt-2 min-h-24 w-full rounded-xl border border-slate-200 p-3 text-sm" placeholder="例如：不吃辣、素食" /></label><label className="text-sm font-semibold">无障碍需求<textarea defaultValue={member.constraint.accessibilityNeeds.join("、")} className="mt-2 min-h-24 w-full rounded-xl border border-slate-200 p-3 text-sm" placeholder="例如：少走楼梯" /></label></div></Card></div>{toast}</>;
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
      {searchQuery.data && searchQuery.data.length > 0 && (
        <Card className="mb-5 divide-y divide-slate-100 p-5">
          <p className="pb-3 text-sm font-semibold text-ink">搜索结果</p>
          {searchQuery.data.map((user) => (
            <div key={user.id} className="flex items-center justify-between gap-4 py-3">
              <div className="flex items-center gap-3">
                <div className="grid h-10 w-10 place-items-center rounded-full bg-mint/15 font-semibold text-ink">{avatar(user.name)}</div>
                <div>
                  <p className="font-semibold text-ink">{user.name}</p>
                  <p className="text-xs text-ink-soft">{user.email}</p>
                </div>
              </div>
              <Button onClick={() => action.mutate({ type: "send", id: user.id })} disabled={action.isPending}>添加好友</Button>
            </div>
          ))}
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
              <FriendRow key={request.id} name={request.addressee.name} detail="等待对方确认" initial={avatar(request.addressee.name)} />
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
  return <><PageHeader eyebrow="ALL ROUTES" title="行程总览" description="每一张登机牌，都是一次共同决定过的出发。" action={<Link to="/trips/new"><Button><Plus size={16} className="mr-2 inline" />新建行程</Button></Link>} /><div className="mb-5 flex gap-2">{["全部", "进行中", "已规划", "已完成"].map((item) => <button key={item} onClick={() => setStatus(item)} className={`rounded-full px-4 py-2 text-sm font-semibold ${status === item ? "bg-ink text-white" : "bg-white text-ink-soft"}`}>{item}</button>)}</div><div className="grid gap-5 md:grid-cols-2">{trips.filter((trip) => status === "全部" || (status === "进行中" ? trip.status === "ONGOING" : status === "已规划" ? trip.status === "PLANNED" : trip.status === "COMPLETED")).map((trip) => <BoardingPassCard key={trip.id} trip={trip} onClick={() => window.location.assign(`/trips/${trip.id}`)} />)}</div></>;
}

export function NewTripPage() {
  const navigate = useNavigate();
  const [generated, setGenerated] = useState(false);
  const [mode, setMode] = useState("constraints");
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard });
  if (isLoading) return <LoadingState label="正在读取小组约束…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data) return <EmptyState title="暂时没有可用的小组" message="先创建或加入一个小组，再生成初始方案。" />;
  return <><PageHeader eyebrow="NEW ROUTE" title="创建一段新行程" description="可以从零开始，也可以让成员约束先替你找到第一条可行路线。" /><div className="grid gap-6 lg:grid-cols-[0.8fr_1.2fr]"><Card className="p-6"><div className="flex gap-2 rounded-xl bg-paper p-1"><button onClick={() => setMode("constraints")} className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold ${mode === "constraints" ? "bg-white text-ink shadow-sm" : "text-ink-soft"}`}>从小组约束生成</button><button onClick={() => setMode("manual")} className={`flex-1 rounded-lg px-3 py-2 text-xs font-semibold ${mode === "manual" ? "bg-white text-ink shadow-sm" : "text-ink-soft"}`}>手动创建</button></div><div className="mt-6 space-y-5"><label className="block text-sm font-semibold">选择小组<select className="mt-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm"><option>周末慢游组 · SH24-7K</option></select></label><label className="block text-sm font-semibold">出发日期<input type="date" defaultValue="2025-04-19" className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label>{mode === "manual" && <Input placeholder="行程名称，例如：杭州春茶之旅" />}<Button className="w-full" onClick={() => setGenerated(true)}>{mode === "constraints" ? "生成初始方案" : "创建空白行程"}<ArrowRight size={16} className="ml-2 inline" /></Button></div></Card>{generated ? <Card className="p-6"><div className="flex items-center gap-2"><Badge tone="mint">方案已生成</Badge><span className="font-mono text-xs text-ink-soft">PLAN PREVIEW</span></div><h2 className="mt-3 font-display text-2xl font-bold">上海春日漫游 · 2 天 1 夜</h2><p className="mt-3 text-sm leading-6 text-ink-soft">已读取 4 位成员约束，日期交集为 4 月 19 日至 20 日，预算上限按最低值 ¥1,800 计算。</p><div className="mt-6"><RouteTrail nodes={dataNodes} compact /></div><div className="mt-6 rounded-xl bg-sun/15 p-4"><p className="text-sm font-semibold text-amber-800">取舍说明</p><p className="mt-2 text-sm leading-6 text-amber-900/70">保留了外滩、豫园和本帮菜；因为成员可用时间不同，暂不加入朱家角，并将住宿控制在预算内。</p></div><Button className="mt-5 w-full" onClick={() => navigate("/trips/1")}>确认并查看行程</Button></Card> : <Card className="flex min-h-[400px] items-center justify-center p-8 text-center"><div><div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-sky/10 text-sky"><Sparkles /></div><h2 className="mt-4 font-display text-xl font-bold">先选一种开始方式</h2><p className="mt-2 text-sm text-ink-soft">我们会把成员的真实约束，翻译成一条能出发的路线。</p></div></Card>}</div></>;
}

const dataNodes = [{
  id: 1, name: "上海外滩", placeName: "上海外滩", latitude: 31.23, longitude: 121.47, nodeType: "ATTRACTION" as const, plannedStart: "2025-04-19T09:30:00", plannedEnd: "2025-04-19T11:30:00", cost: 0, sequenceOrder: 1, status: "CONFIRMED" as const,
}, {
  id: 2, name: "豫园", placeName: "豫园", latitude: 31.23, longitude: 121.49, nodeType: "ATTRACTION" as const, plannedStart: "2025-04-19T14:30:00", plannedEnd: "2025-04-19T17:00:00", cost: 40, sequenceOrder: 2, status: "PLANNED" as const,
}];

export function EventsPage() {
  const [created, setCreated] = useState(false);
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["events"], queryFn: api.events });
  const { toast, show } = useToast();
  if (isLoading) return <LoadingState label="正在同步事件信号…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data) return <EmptyState title="暂时没有事件数据" />;
  return <><PageHeader eyebrow="LIVE SIGNALS" title="事件监测" description="天气、交通和城市公告会在这里汇合，帮助你提前看见路线中的变化。" action={<div className="flex gap-2"><Button variant="ghost" onClick={() => setCreated(true)}>录入事件</Button><Button onClick={() => show("已生成 2 条模拟事件")}><Sparkles size={16} className="mr-2 inline" />生成模拟事件</Button></div>} /><div className="mb-6 flex flex-wrap gap-2"><select className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm"><option>事件类型：全部</option><option>天气</option><option>交通</option><option>景点闭馆</option></select><select className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm"><option>严重度：全部</option><option>HIGH</option><option>MEDIUM</option></select><input type="date" className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm" /></div><Card className="divide-y divide-slate-100 p-5">{[...data, ...(created ? [{ ...data[0], id: 9, title: "南京东路临时交通管制", eventType: "TRAFFIC_CONTROL" as const, placeName: "南京东路", severity: "MEDIUM" as const }] : [])].map((event) => <div key={event.id} className="flex flex-wrap items-start justify-between gap-4 py-5 first:pt-0 last:pb-0"><div className="flex gap-4"><div className={`rounded-xl p-3 ${event.severity === "HIGH" || event.severity === "CRITICAL" ? "bg-coral/10 text-coral" : "bg-sun/20 text-amber-700"}`}><EventIcon type={event.eventType} /></div><div><div className="flex flex-wrap items-center gap-2"><h2 className="font-semibold text-ink">{event.title}</h2><Badge tone={event.severity === "HIGH" ? "risk" : "sun"}>{event.severity}</Badge></div><p className="mt-2 text-sm leading-6 text-ink-soft">{event.description}</p><p className="mt-2 font-mono text-[11px] text-ink-soft">{event.placeName} · {event.startTime.replace("T", " ")} — {event.endTime.slice(11)}</p></div></div><Button variant="ghost" onClick={() => show("已加入影响分析队列")}>分析影响</Button></div>)}</Card>{toast}</>;
}

export function ImpactsPage() {
  const { data: trip, isLoading: tripLoading, isError: tripError, error: tripQueryError, refetch: refetchTrip } = useQuery({ queryKey: ["trip", 1], queryFn: () => api.trip(1) });
  const { data: risk, isLoading: riskLoading, isError: riskError, error: riskQueryError, refetch: refetchRisk } = useQuery({ queryKey: ["risk"], queryFn: api.risk });
  const { toast, show } = useToast();
  if (tripLoading || riskLoading) return <LoadingState label="正在计算影响与风险…" />;
  if (tripError || riskError) {
    const queryError = tripQueryError ?? riskQueryError;
    return <ErrorState onRetry={() => { void refetchTrip(); void refetchRisk(); }} message={queryError instanceof Error ? queryError.message : undefined} />;
  }
  if (!trip || !risk) return <EmptyState title="暂无风险报告" message="当行程和事件数据准备好后，这里会显示风险拆解。" />;
  return <><PageHeader eyebrow="IMPACT & RISK" title="影响与风险" description="把一条外部消息，翻译成你们路线中具体的下一步。" action={<Button onClick={() => show("影响分析已刷新，发现 1 个受影响节点")}>刷新影响分析</Button>} /><div className="grid gap-5 xl:grid-cols-[0.7fr_1.3fr]"><Card className="flex flex-col items-center p-7 text-center"><p className="eyebrow">TRIP RISK REPORT</p><div className="mt-5"><RiskGauge score={risk?.score ?? 68} label="需要留意" /></div><h2 className="mt-4 font-display text-xl font-bold text-ink">下午路线需要一个决定</h2><p className="mt-2 max-w-xs text-sm leading-6 text-ink-soft">1 / 4 个节点被事件命中，建议在 12:00 前完成替代方案投票。</p><Link to="/plans" className="mt-5 text-sm font-semibold text-sky">查看 3 个替代方案 →</Link></Card><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">SCORING BREAKDOWN</p><h2 className="mt-2 font-display text-xl font-bold">风险是怎么来的</h2></div><CircleAlert className="text-coral" /></div><div className="mt-6 grid gap-4 sm:grid-cols-2">{risk?.factors.map((factor) => <div key={factor.label} className="rounded-xl bg-paper p-4"><div className="flex justify-between text-sm font-semibold"><span>{factor.label}</span><span className="font-mono text-coral">{factor.value}/40</span></div><div className="mt-3 h-2 rounded-full bg-white"><div className="h-full rounded-full bg-coral" style={{ width: `${factor.value * 2.5}%` }} /></div><p className="mt-2 text-xs text-ink-soft">{factor.detail}</p></div>)}</div></Card></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">AFFECTED NODES</p><h2 className="mt-2 font-display text-xl font-bold">受影响节点</h2></div><Badge tone="coral">1 个节点</Badge></div><div className="mt-6"><RouteTrail nodes={trip?.itineraryNodes.filter((node) => node.status === "AFFECTED") ?? []} /></div></Card>{toast}</>;
}

export function PlansPage() {
  const [selected, setSelected] = useState(plans[1]);
  const { toast, show } = useToast();
  return <><PageHeader eyebrow="RECOVERY OPTIONS" title="替代方案" description="不同的取舍，没有绝对正确的答案。把成本、时间和改变程度放在一起比较。" action={<Button onClick={() => show("已发起全员投票")}>发起投票</Button>} /><div className="grid gap-5 xl:grid-cols-3">{plans.map((plan) => <button key={plan.id} onClick={() => setSelected(plan)} className={`card p-5 text-left transition hover:-translate-y-1 ${selected.id === plan.id ? "border-coral ring-2 ring-coral/20" : ""}`}><div className="flex items-center justify-between"><Badge tone={plan.status === "VOTING" ? "coral" : "neutral"}>{plan.status === "VOTING" ? "投票中" : "待选择"}</Badge><span className="font-mono text-xs text-ink-soft">{plan.strategy.replace("MIN_", "")}</span></div><h2 className="mt-4 font-display text-lg font-bold text-ink">{plan.title}</h2><div className="mt-5 grid grid-cols-3 gap-2 border-y border-slate-100 py-4 text-center"><div><p className="font-mono text-lg font-bold text-ink">¥{plan.extraCost}</p><p className="text-[10px] text-ink-soft">额外成本</p></div><div><p className="font-mono text-lg font-bold text-ink">{plan.extraDelayMinutes}m</p><p className="text-[10px] text-ink-soft">额外延误</p></div><div><p className="font-mono text-lg font-bold text-ink">{plan.changedNodeCount}</p><p className="text-[10px] text-ink-soft">节点变更</p></div></div><p className="mt-4 text-sm leading-6 text-ink-soft">{plan.summary}</p><p className="mt-5 text-sm font-semibold text-sky">查看变更清单 →</p></button>)}</div><Card className="mt-6 p-6"><div className="flex flex-wrap items-center justify-between gap-3"><div><p className="eyebrow">SELECTED PLAN</p><h2 className="mt-2 font-display text-xl font-bold">{selected.title}</h2></div><Badge tone="mint">可行</Badge></div><div className="mt-5 space-y-3">{selected.changes.map((change) => <div key={change.place} className="flex items-center gap-3 rounded-xl bg-paper p-4"><span className="rounded-lg bg-white p-2 text-coral"><SlidersHorizontal size={16} /></span><div><p className="text-sm font-semibold text-ink">{change.type === "RESCHEDULE" ? "调整时间" : change.type === "REPLACE" ? "替换节点" : "移除节点"} · {change.place}</p><p className="mt-1 text-xs text-ink-soft">{change.note}</p></div></div>)}</div><div className="mt-5 flex flex-wrap gap-3"><Button onClick={() => show("投票已开启，成员会收到通知")}>选择并发起投票</Button><Link to="/votes"><Button variant="ghost">去投票中心</Button></Link></div></Card>{toast}</>;
}

export function VotesPage() {
  const [choice, setChoice] = useState("");
  const [tallied, setTallied] = useState(false);
  const { toast, show } = useToast();
  return <><PageHeader eyebrow="GROUP DECISION" title="投票中心" description="每个人的一票都在路线里留下位置。过半同意后，方案会自动应用到行程。" /><Card className="p-6"><div className="flex flex-wrap items-center justify-between gap-4"><div><Badge tone="coral">投票中</Badge><h2 className="mt-3 font-display text-2xl font-bold">少等一会儿 · 先去室内</h2><p className="mt-2 text-sm text-ink-soft">最低延误策略 · 额外成本 ¥160 · 变更 2 个节点</p></div><div className="text-right"><p className="font-mono text-3xl font-bold text-ink">2 / 4</p><p className="text-xs text-ink-soft">赞成票</p></div></div><div className="mt-6 h-3 overflow-hidden rounded-full bg-slate-100"><div className="h-full w-1/2 rounded-full bg-mint" /></div><p className="mt-2 text-xs text-ink-soft">还需要 1 票赞成即可通过</p><div className="mt-7 grid gap-3 sm:grid-cols-2">{groupMembers.map((member, index) => <div key={member.id} className="flex items-center justify-between rounded-xl bg-paper p-3"><div className="flex items-center gap-3"><img src={member.user.avatar} alt="" className="h-9 w-9 rounded-full" /><span className="text-sm font-semibold">{member.user.name}</span></div><Badge tone={index < 2 ? "mint" : "neutral"}>{index < 2 ? "已赞成" : "待投票"}</Badge></div>)}</div><div className="mt-7 border-t border-slate-100 pt-6"><p className="text-sm font-semibold text-ink">你的选择</p><div className="mt-3 flex flex-wrap gap-2">{["APPROVE", "REJECT", "ABSTAIN"].map((item) => <button key={item} onClick={() => setChoice(item)} className={`rounded-xl border px-5 py-3 text-sm font-semibold ${choice === item ? "border-coral bg-coral/10 text-coral-deep" : "border-slate-200 text-ink-soft"}`}>{item === "APPROVE" ? "赞成" : item === "REJECT" ? "反对" : "弃权"}</button>)}</div><Button className="mt-4" disabled={!choice} onClick={() => show("你的投票已记录")}>提交投票</Button></div></Card><Card className="mt-5 flex flex-wrap items-center justify-between gap-4 p-6"><div><p className="eyebrow">TALLY RESULT</p><h2 className="mt-2 font-display text-xl font-bold">{tallied ? "方案已通过并应用" : "等待计票"}</h2><p className="mt-1 text-sm text-ink-soft">{tallied ? "豫园已替换为上海博物馆，行程风险降至 24。" : "群主可以在成员完成投票后计票。"}</p></div><Button variant={tallied ? "secondary" : "primary"} onClick={() => { setTallied(true); show("计票完成，方案已应用"); }}>{tallied ? "已完成" : "计票并应用"}</Button></Card>{toast}</>;
}

export function ChangelogPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["changelogs"], queryFn: api.changelogs });
  if (isLoading) return <LoadingState label="正在读取变更记录…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data) return <EmptyState title="还没有变更记录" />;
  return <><PageHeader eyebrow="TRIP HISTORY" title="变更记录" description="每一次应变都有迹可循，费用、截止时间和关联方案都在这里。" /><Card className="divide-y divide-slate-100 p-6">{data.map((log) => <div key={log.id} className="flex flex-wrap items-start justify-between gap-5 py-5 first:pt-0 last:pb-0"><div><div className="flex items-center gap-2"><Badge tone="mint">已应用</Badge><span className="font-mono text-xs text-ink-soft">{log.createdAt.replace("T", " ")}</span></div><h2 className="mt-3 font-semibold text-ink">{log.description}</h2><p className="mt-2 text-sm text-ink-soft">关联方案：{log.plan}</p></div><div className="text-right"><p className="font-mono text-lg font-bold text-coral">+¥{log.extraCost}</p><p className="mt-1 text-xs text-ink-soft">退款截止 {log.refundDeadline.replace("T", " ")}</p></div></div>)}</Card></>;
}

export function DiscussionsPage() {
  const [text, setText] = useState("");
  const { toast, show } = useToast();
  const threads = [{ author: "周知远", avatar: "https://i.pravatar.cc/100?img=13", time: "12分钟前", body: "@林小满 豫园如果改到明早，我可以把早餐也一起安排上。", likes: 4 }, { author: "许桃", avatar: "https://i.pravatar.cc/100?img=44", time: "36分钟前", body: "我更倾向方案 2，上海博物馆下雨天也很舒服，大家觉得呢？", likes: 7 }, { author: "林小满", avatar: "https://i.pravatar.cc/100?img=47", time: "1小时前", body: "先把每个人的约束都看一遍，再决定不要让任何人赶路。", likes: 12 }];
  return <><PageHeader eyebrow="TRIP DISCUSSION" title="讨论区" description="路线之外的想法，也值得被听见。可以 @旅伴，直接针对行程或方案讨论。" action={<Button onClick={() => show("讨论主题已创建")}>新建主题</Button>} /><div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]"><div className="space-y-4">{threads.map((thread) => <Card key={thread.time} className="p-5"><div className="flex gap-3"><img src={thread.avatar} alt="" className="h-10 w-10 rounded-full" /><div className="min-w-0 flex-1"><div className="flex justify-between gap-3"><div><p className="text-sm font-semibold">{thread.author}</p><p className="mt-1 text-[11px] text-ink-soft">{thread.time} · 上海春日漫游</p></div><button aria-label="更多讨论操作" className="text-ink-soft"><MoreHorizontal size={18} /></button></div><p className="mt-4 text-sm leading-7 text-ink">{thread.body}</p><div className="mt-4 flex gap-4 text-xs text-ink-soft"><button className="flex items-center gap-1.5 hover:text-coral"><ThumbsUp size={14} />{thread.likes}</button><button className="flex items-center gap-1.5 hover:text-sky"><MessageCircle size={14} />回复</button></div></div></div></Card>)}</div><Card className="h-fit p-5"><p className="eyebrow">WRITE TO THE CREW</p><h2 className="mt-3 font-display text-xl font-bold">说点什么</h2><textarea value={text} onChange={(event) => setText(event.target.value)} className="mt-5 min-h-32 w-full rounded-xl border border-slate-200 p-3 text-sm leading-6 focus:border-sky focus:outline-none" placeholder="@旅伴，分享一个你在意的细节…" /><div className="mt-3 flex items-center justify-between"><span className="text-xs text-ink-soft">{text.length}/500</span><Button disabled={!text} onClick={() => { setText(""); show("评论已发布"); }}><Send size={15} className="mr-2 inline" />发布</Button></div></Card></div>{toast}</>;
}

export function NotificationsPage() {
  const [read, setRead] = useState<number[]>([2]);
  const notifications = [{ id: 1, type: "事件", title: "黄浦江沿线短时阵雨", detail: "上海春日漫游有一个节点需要重新确认", time: "8分钟前", color: "coral" }, { id: 2, type: "投票", title: "你已投票：少等一会儿 · 先去室内", detail: "方案当前获得 2 / 4 赞成票", time: "1小时前", color: "sky" }, { id: 3, type: "新方案", title: "有 3 个替代方案等待比较", detail: "来自上海春日漫游 · 周末慢游组", time: "昨天", color: "mint" }, { id: 4, type: "采纳", title: "行程变更已应用", detail: "退款截止时间为 4 月 20 日 18:00", time: "昨天", color: "mint" }];
  return <><PageHeader eyebrow="INBOX" title="通知" description="事件、影响、方案和投票的每一个关键节点，都不会错过。" action={<Button variant="ghost" onClick={() => setRead(notifications.map((item) => item.id))}>全部标为已读</Button>} /><Card className="divide-y divide-slate-100 p-5">{notifications.map((item) => <div key={item.id} className={`flex gap-4 py-5 first:pt-0 last:pb-0 ${!read.includes(item.id) ? "" : "opacity-60"}`}><div className={`mt-1 grid h-10 w-10 shrink-0 place-items-center rounded-xl ${item.color === "coral" ? "bg-coral/10 text-coral" : item.color === "sky" ? "bg-sky/10 text-sky" : "bg-mint/10 text-mint"}`}><BellIcon type={item.type} /></div><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><Badge tone={item.color === "coral" ? "coral" : item.color === "sky" ? "sky" : "mint"}>{item.type}</Badge>{!read.includes(item.id) && <span className="h-2 w-2 rounded-full bg-coral" />}</div><h2 className="mt-2 font-semibold text-ink">{item.title}</h2><p className="mt-1 text-sm text-ink-soft">{item.detail}</p><p className="mt-2 font-mono text-[10px] text-ink-soft">{item.time}</p></div>{!read.includes(item.id) && <button onClick={() => setRead([...read, item.id])} className="self-center text-xs font-semibold text-sky">标为已读</button>}</div>)}</Card></>;
}

function BellIcon({ type }: { type: string }) { return type === "事件" ? <CircleAlert size={18} /> : type === "投票" ? <Check size={18} /> : <Sparkles size={18} />; }

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
  const guide = guides.find((item) => item.id === Number(id)) ?? guides[0];
  const [open, setOpen] = useState(false);
  const { toast, show } = useToast();
  const templateNodes = dataNodes.map((node, index) => ({ ...node, id: index + 1, name: index === 0 ? guide.city + "老城漫步" : guide.tags[0], placeName: guide.city, status: "PLANNED" as const }));
  return <><div className="mb-7 flex items-center gap-3 text-sm text-ink-soft"><Link to="/guides" className="hover:text-ink">攻略社区</Link><span>/</span><span className="text-ink">{guide.title}</span></div><div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]"><div><Card className="overflow-hidden"><div className="h-72 md:h-96"><ImageFallback src={guide.cover} alt={guide.title} city={guide.city} /></div><div className="p-6 md:p-8"><div className="flex flex-wrap items-center gap-2"><Badge tone="coral">{guide.theme}</Badge><Badge tone="neutral">{guide.city} · {guide.days} 天</Badge><span className="ml-auto flex items-center gap-1 text-sm"><Heart size={16} className="text-coral" />{guide.saves} 收藏</span></div><h1 className="mt-4 font-display text-3xl font-bold text-ink">{guide.title}</h1><p className="mt-4 text-sm leading-7 text-ink-soft">{guide.description} 这是一份把具体地点、留白时间和真实预算放在一起的可复用路线。</p><div className="mt-5 flex flex-wrap gap-2">{guide.tags.map((tag) => <Badge key={tag} tone="sky">#{tag}</Badge>)}</div></div></Card><Card className="mt-5 p-6 md:p-8"><p className="eyebrow">TEMPLATE ITINERARY</p><h2 className="mt-2 font-display text-2xl font-bold">路线模板</h2><div className="mt-7"><RouteTrail nodes={templateNodes} /></div></Card></div><div className="space-y-5"><Card className="sticky top-24 p-6"><p className="eyebrow">READY TO GO?</p><h2 className="mt-3 font-display text-2xl font-bold">把这条路线带回你的小组</h2><p className="mt-3 text-sm leading-6 text-ink-soft">选择小组和实际出发日期，模板中的第 1 天 / 第 2 天会自动映射到你的真实行程。</p><div className="mt-6 flex items-center justify-between border-y border-slate-100 py-4"><span className="text-sm text-ink-soft">预计人均</span><span className="font-mono text-xl font-bold">¥{guide.price.toLocaleString()}</span></div><Button className="mt-5 w-full" onClick={() => setOpen(true)}>攻略纳用</Button></Card><Card className="p-6"><p className="eyebrow">BY {guide.author.name.toUpperCase()}</p><div className="mt-4 flex items-center gap-3"><img src={guide.author.avatar} alt="" className="h-10 w-10 rounded-full" /><div><p className="text-sm font-semibold">{guide.author.name}</p><p className="text-xs text-ink-soft">4.9 分 · {guide.reviews} 条评价</p></div></div></Card></div></div><ApplyGuideModal open={open} onClose={() => setOpen(false)} onDone={() => { setOpen(false); show("攻略纳用完成，正在打开新行程"); navigate("/trips/1"); }} />{toast}</>;
}

function ApplyGuideModal({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  return <Modal open={open} title="攻略纳用" onClose={onClose}><div className="space-y-5"><label className="block text-sm font-semibold">目标小组<select className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm"><option>周末慢游组 · 4 位成员</option></select></label><label className="block text-sm font-semibold">实际出发日期<input type="date" defaultValue="2025-05-03" className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3 text-sm" /></label><div className="rounded-xl bg-paper p-4"><p className="text-sm font-semibold">相对 → 绝对时间</p><div className="mt-3 space-y-2 text-xs text-ink-soft"><p><span className="font-mono">第 1 天 09:30</span><span className="mx-2">→</span>2025-05-03 周六 09:30</p><p><span className="font-mono">第 2 天 10:00</span><span className="mx-2">→</span>2025-05-04 周日 10:00</p></div></div><div className="flex items-start gap-3 rounded-xl bg-sun/15 p-4 text-sm leading-6 text-amber-900/75"><CircleAlert size={18} className="mt-0.5 shrink-0 text-amber-600" />按当前小组约束，预算约超出 ¥180；纳用后可以自动裁剪非必访节点。</div><Button className="w-full" onClick={onDone}>确认纳用并创建行程</Button></div></Modal>;
}

export function AdminPage() {
  const bars = [{ label: "上海", value: 82 }, { label: "成都", value: 61 }, { label: "杭州", value: 48 }, { label: "大理", value: 36 }, { label: "厦门", value: 24 }];
  return <><PageHeader eyebrow="OPERATIONS / ADMIN" title="数据统计后台" description="看见大家如何出发、哪里被打断，以及哪些替代方案真正帮上了忙。" action={<Badge tone="mint">管理员视图</Badge>} /><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><Stat label="总行程数" value="128" detail="+18% 较上月" tone="coral" /><Stat label="预算中位数" value="¥2,480" detail="人均 ¥820" tone="mint" /><Stat label="事件命中率" value="23.6%" detail="30 / 127 条事件" tone="sky" /><Stat label="方案采纳率" value="71.4%" detail="15 / 21 个方案" tone="sun" /></div><div className="mt-6 grid gap-5 lg:grid-cols-[1.2fr_0.8fr]"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">TRIPS BY DESTINATION</p><h2 className="mt-2 font-display text-xl font-bold">热门目的地</h2></div><span className="text-xs text-ink-soft">近 30 天</span></div><div className="mt-7 space-y-5">{bars.map((bar) => <div key={bar.label} className="flex items-center gap-4"><span className="w-12 text-sm font-semibold text-ink">{bar.label}</span><div className="h-3 flex-1 rounded-full bg-paper"><div className="h-full rounded-full bg-coral" style={{ width: `${bar.value}%` }} /></div><span className="font-mono text-xs text-ink-soft">{bar.value}</span></div>)}</div></Card><Card className="p-6"><p className="eyebrow">BUDGET DISTRIBUTION</p><h2 className="mt-2 font-display text-xl font-bold">预算分布</h2><div className="mx-auto mt-7 grid h-44 w-44 place-items-center rounded-full" style={{ background: "conic-gradient(#FF5B4C 0 38%, #17C3A2 38% 65%, #2F9BFF 65% 84%, #FFC53D 84%)" }}><div className="grid h-28 w-28 place-items-center rounded-full bg-white text-center"><p className="font-display text-2xl font-bold">¥318k</p><p className="text-[10px] text-ink-soft">总计划预算</p></div></div><div className="mt-5 flex flex-wrap justify-center gap-3 text-xs text-ink-soft"><span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-coral" />1k 以下</span><span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-mint" />1—3k</span><span><i className="mr-1 inline-block h-2 w-2 rounded-full bg-sky" />3—5k</span></div></Card></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">EVENT SOURCES</p><h2 className="mt-2 font-display text-xl font-bold">事件源健康度</h2></div><Button variant="ghost">管理事件源</Button></div><div className="mt-5 grid gap-3 md:grid-cols-3"><div className="rounded-xl bg-mint/10 p-4"><p className="text-sm font-semibold text-emerald-800">天气服务</p><p className="mt-2 text-xs text-emerald-700/70">正常 · 最近同步 2 分钟前</p></div><div className="rounded-xl bg-mint/10 p-4"><p className="text-sm font-semibold text-emerald-800">交通公告</p><p className="mt-2 text-xs text-emerald-700/70">正常 · 最近同步 8 分钟前</p></div><div className="rounded-xl bg-sun/15 p-4"><p className="text-sm font-semibold text-amber-800">城市活动</p><p className="mt-2 text-xs text-amber-700/70">延迟 · 最近同步 24 分钟前</p></div></div></Card></>;
}

export function RouteMapPage() {
  const nodes = activeTrip.itineraryNodes;
  const [modes, setModes] = useState<Record<number, RouteModeType>>(() => Object.fromEntries(nodes.slice(0, -1).map((node) => [node.id, "DRIVE"])) as Record<number, RouteModeType>);
  const [selectedNode, setSelectedNode] = useState<{ node: typeof nodes[number] } | null>(null);
  const segments = nodes.slice(0, -1).map((from, index) => {
    const to = nodes[index + 1];
    const baseDistance = haversine(from.latitude, from.longitude, to.latitude, to.longitude);
    return { from, to, baseDistance };
  });
  const totalDistance = segments.reduce((sum, item) => sum + estimateSegment(item.baseDistance, modes[item.from.id]).distance, 0);
  const totalMinutes = segments.reduce((sum, item) => sum + estimateSegment(item.baseDistance, modes[item.from.id]).minutes, 0);
  return <><PageHeader eyebrow="SH24-7K · TODAY'S LEGS" title={activeTrip.title} description={`${totalDistance.toFixed(1)} km · 约 ${totalMinutes} 分钟 · ${segments.length} 段 · 估算距离（降级）`} action={<Button>重新规划路线</Button>} /><section className="relative mb-6 overflow-hidden rounded-card bg-gradient-to-br from-ink via-[#1d4e83] to-sky p-6 text-white shadow-soft md:p-8"><div className="absolute -right-20 -top-28 h-72 w-72 rounded-full border-[38px] border-white/10" /><div className="absolute inset-0 opacity-30"><svg className="h-full w-full" viewBox="0 0 900 260" preserveAspectRatio="none"><path d="M-20 220C140 120 190 250 340 150S630 65 920 118" fill="none" stroke="#FF5B4C" strokeWidth="3" strokeDasharray="9 12" className="animate-route" /><path d="M-20 220C140 120 190 250 340 150S630 65 920 118" fill="none" stroke="#fff" strokeWidth="1" strokeDasharray="2 16" /></svg></div><div className="relative max-w-xl"><p className="font-mono text-[10px] tracking-[0.24em] text-coral">BOARDING PASS / ROUTE MAP</p><h2 className="mt-4 font-display text-3xl font-bold md:text-4xl">一段一段，飞向下一站。</h2><div className="mt-7 flex flex-wrap gap-3 text-xs text-white/70"><span className="rounded-full bg-white/10 px-3 py-2">出发 SH</span><span className="rounded-full bg-white/10 px-3 py-2">抵达 {getPlaceDetail(nodes[nodes.length - 1].placeName, nodes[nodes.length - 1].nodeType).code ?? "WP" + nodes[nodes.length - 1].sequenceOrder}</span><span className="rounded-full bg-white/10 px-3 py-2">共 {segments.length} 个航段</span></div></div></section><div className="space-y-4">{segments.map((segment, index) => <RouteLeg key={segment.from.id} index={index} from={segment.from} to={segment.to} distance={segment.baseDistance} mode={modes[segment.from.id]} onModeChange={(mode) => setModes({ ...modes, [segment.from.id]: mode })} onPlaceClick={(node) => setSelectedNode({ node })} />)}</div><div className="relative mt-6 overflow-hidden rounded-card border border-slate-100 bg-[#eaf5fb] p-5"><svg className="h-40 w-full" viewBox="0 0 800 180" preserveAspectRatio="none" aria-hidden="true"><circle cx="104" cy="112" r="4" fill="#2F9BFF" /><circle cx="390" cy="54" r="5" fill="#17C3A2" /><circle cx="686" cy="115" r="4" fill="#FF5B4C" /><path d="M104 112 C 220 10, 300 150, 390 54 S 580 12, 686 115" fill="none" stroke="#FF5B4C" strokeWidth="3" strokeDasharray="8 10" className="animate-route" /></svg><p className="text-center text-xs text-ink-soft">路线图使用节点坐标进行估算，未接入外部地图服务时仍可继续规划。</p></div>{selectedNode && <PlaceDetailSheet detail={getPlaceDetail(selectedNode.node.placeName, selectedNode.node.nodeType)} node={selectedNode.node} onClose={() => setSelectedNode(null)} />}</>;
}

type RouteModeType = "WALK" | "DRIVE" | "TRANSIT";
function RouteLeg({ index, from, to, distance, mode, onModeChange, onPlaceClick }: { index: number; from: typeof activeTrip.itineraryNodes[number]; to: typeof activeTrip.itineraryNodes[number]; distance: number; mode: RouteModeType; onModeChange: (mode: RouteModeType) => void; onPlaceClick: (node: typeof activeTrip.itineraryNodes[number]) => void }) {
  const options: { value: RouteModeType; label: string; icon: typeof Car }[] = [{ value: "WALK", label: "步行", icon: Footprints }, { value: "DRIVE", label: "驾车", icon: Car }, { value: "TRANSIT", label: "公交", icon: Bus }];
  const selected = estimateSegment(distance, mode);
  const ModeIcon = options.find((option) => option.value === mode)?.icon ?? Car;
  const fromDetail = getPlaceDetail(from.placeName, from.nodeType);
  const toDetail = getPlaceDetail(to.placeName, to.nodeType);
  return <article className="relative overflow-hidden rounded-card border border-slate-100 bg-surface shadow-soft"><div className="absolute -left-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="absolute -right-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="grid gap-5 p-5 md:grid-cols-[1fr_auto_1fr] md:items-center md:p-6"><PlaceStop label="出发" node={from} detail={fromDetail} onClick={() => onPlaceClick(from)} /><div className="flex items-center gap-3 text-coral md:flex-col"><span className="font-mono text-[10px] text-ink-soft">LEG {String(index + 1).padStart(2, "0")}</span><div className="flex flex-1 items-center md:w-28"><span className="h-px flex-1 border-t-2 border-dashed border-coral/40" /><span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-coral/10 text-coral"><ModeIcon size={17} /></span><span className="h-px flex-1 border-t-2 border-dashed border-coral/40 animate-route" /></div><span className="font-mono text-[10px] text-ink-soft">{selected.distance.toFixed(1)} KM</span></div><PlaceStop label="到达" node={to} detail={toDetail} onClick={() => onPlaceClick(to)} /></div><div className="flex flex-wrap gap-2 border-t border-dashed border-slate-200 p-4 md:px-6">{options.map(({ value, label, icon: Icon }) => { const estimate = estimateSegment(distance, value); return <button type="button" key={value} onClick={() => onModeChange(value)} className={`flex items-center gap-2 rounded-full px-3 py-2 text-xs font-semibold transition focus-visible:outline-offset-2 ${mode === value ? "bg-ink text-white" : "bg-paper text-ink-soft hover:text-ink"}`}><Icon size={14} />{label}<span className={mode === value ? "text-white/60" : "text-ink-soft"}>{estimate.distance.toFixed(1)} km · {estimate.minutes} 分</span></button>; })}</div></article>;
}

function PlaceStop({ label, node, detail, onClick }: { label: string; node: typeof activeTrip.itineraryNodes[number]; detail: ReturnType<typeof getPlaceDetail>; onClick: () => void }) {
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

export function BudgetPage() {
  const expenses = [{ label: "住宿 · 外滩茂悦", amount: 760 }, { label: "餐饮 · 老吉士", amount: 168 }, { label: "交通 · 高铁", amount: 332 }];
  return <><PageHeader eyebrow="BUDGET & EXPENSES" title="预算与费用" description="预算不是一条紧绷的线，而是让团队知道什么时候该留一点余地。" action={<Button>记录一笔费用</Button>} /><div className="grid gap-5 md:grid-cols-3"><Stat label="总预算" value="¥2,400" detail="成员最低预算上限" tone="coral" /><Stat label="已用金额" value="¥1,260" detail="52.5% 已使用" tone="mint" /><Stat label="剩余预算" value="¥1,140" detail="预计足够覆盖替代方案" tone="sky" /></div><Card className="mt-5 p-6"><div className="flex items-center justify-between"><h2 className="font-display text-xl font-bold">费用明细</h2><Badge tone="mint">未超支</Badge></div><div className="mt-5 divide-y divide-slate-100">{expenses.map((item) => <div key={item.label} className="flex justify-between py-4 text-sm"><span className="text-ink-soft">{item.label}</span><span className="font-mono font-bold">¥{item.amount}</span></div>)}</div></Card></>;
}

export function SettlementPage() {
  return <><PageHeader eyebrow="AA SETTLEMENT" title="分账与结算" description="共同消费按人平分，尽量用最少的转账次数结清。" action={<Button>录入共同消费</Button>} /><div className="grid gap-5 lg:grid-cols-[1fr_0.8fr]"><Card className="p-6"><p className="eyebrow">WHO PAYS WHO</p><h2 className="mt-2 font-display text-xl font-bold">建议结算</h2><div className="mt-6 space-y-3"><SettlementRow from="周知远" to="林小满" amount="¥316" /><SettlementRow from="陈一禾" to="林小满" amount="¥84" /><SettlementRow from="许桃" to="周知远" amount="¥42" /></div></Card><Card className="p-6"><p className="eyebrow">TRIP SHARES</p><h2 className="mt-2 font-display text-xl font-bold">成员账单</h2><div className="mt-6 space-y-4"><div className="flex justify-between text-sm"><span>林小满</span><span className="font-mono">¥420 · 已垫付</span></div><div className="flex justify-between text-sm"><span>周知远</span><span className="font-mono">¥330</span></div><div className="flex justify-between text-sm"><span>许桃</span><span className="font-mono">¥255</span></div><div className="flex justify-between text-sm"><span>陈一禾</span><span className="font-mono">¥255</span></div></div></Card></div></>;
}

function SettlementRow({ from, to, amount }: { from: string; to: string; amount: string }) {
  return <div className="flex items-center justify-between rounded-xl bg-paper p-4"><div className="flex items-center gap-3 text-sm font-semibold"><span>{from}</span><ArrowRight size={15} className="text-coral" /><span>{to}</span></div><span className="font-mono text-sm font-bold text-coral">{amount}</span></div>;
}

function Stat({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: "coral" | "mint" | "sky" | "sun" }) {
  return <Card className="p-5"><div className={`mb-5 h-2 w-10 rounded-full ${tone === "coral" ? "bg-coral" : tone === "mint" ? "bg-mint" : tone === "sky" ? "bg-sky" : "bg-sun"}`} /><p className="text-xs text-ink-soft">{label}</p><p className="mt-2 font-display text-2xl font-bold">{value}</p><p className="mt-1 text-xs text-ink-soft">{detail}</p></Card>;
}
