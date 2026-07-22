import { useMemo, useState, type FormEvent } from "react";
import { ArrowLeft, CalendarDays, Car, Check, CircleDollarSign, Footprints, Map as MapIcon, MoreHorizontal, Pencil, Plus, Share2, Trash2, Users, X } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge, Button, Card, PageHeader, RiskGauge, RouteTrail } from "../components/ui";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import { BaiduMap } from "../components/BaiduMap";
import { MapNodeDetailSheet } from "../components/MapNodeDetailSheet";
import type { ExternalEvent, ItineraryNode, NodeType } from "../types";

type NodeDraft = { name: string; placeName: string; latitude: string; longitude: string; nodeType: NodeType; plannedStart: string; plannedEnd: string; cost: string; sequenceOrder: string };

function draftFromNode(node: ItineraryNode): NodeDraft {
  return { name: node.name, placeName: node.placeName, latitude: String(node.latitude), longitude: String(node.longitude), nodeType: node.nodeType, plannedStart: node.plannedStart.slice(0, 16), plannedEnd: node.plannedEnd.slice(0, 16), cost: String(node.cost), sequenceOrder: String(node.sequenceOrder) };
}

function emptyNodeDraft(sequenceOrder: number, start?: string, end?: string): NodeDraft {
  return { name: "", placeName: "", latitude: "", longitude: "", nodeType: "ATTRACTION", plannedStart: start ?? "", plannedEnd: end ?? "", cost: "0", sequenceOrder: String(sequenceOrder) };
}

export function TripDetailPage() {
  const { id = "1" } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [shareFeedback, setShareFeedback] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  const [editingNodeId, setEditingNodeId] = useState<number | null>(null);
  const [draft, setDraft] = useState<NodeDraft | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [addDraft, setAddDraft] = useState<NodeDraft>(emptyNodeDraft(1));
  const [selectedNode, setSelectedNode] = useState<ItineraryNode | null>(null);
  const tripQuery = useQuery({ queryKey: ["trip", id], queryFn: () => api.trip(Number(id)) });
  const expensesQuery = useQuery({ queryKey: ["expenses", Number(id)], queryFn: () => api.expenses(Number(id)), enabled: Boolean(id) });
  const impactsQuery = useQuery({ queryKey: ["impacts", id], queryFn: () => api.impacts(Number(id)), enabled: Boolean(id) });
  const itineraryNodes = useMemo(
    () => [...(tripQuery.data?.itineraryNodes ?? [])].sort((left, right) => left.sequenceOrder - right.sequenceOrder),
    [tripQuery.data?.itineraryNodes],
  );
  const topLevelNodes = useMemo(
    () => itineraryNodes.filter((node) => node.parentId === null || node.parentId === undefined),
    [itineraryNodes],
  );
  const overviewResolveQueries = useQueries({
    queries: topLevelNodes.map((node) => ({
      queryKey: ["trip-map-resolve-overview", node.id, node.placeName || node.name, node.latitude, node.longitude],
      queryFn: () => api.mapResolve(node.placeName || node.name, node.latitude, node.longitude),
      enabled: hasCoordinates(node) && Boolean(node.placeName || node.name),
    })),
  });
  const overviewNodes = useMemo(
    () => topLevelNodes.map((node, index) => {
      const resolved = overviewResolveQueries[index]?.data;
      return resolved?.available && resolved.lat !== undefined && resolved.lng !== undefined
        ? { ...node, latitude: resolved.lat, longitude: resolved.lng }
        : node;
    }),
    [overviewResolveQueries, topLevelNodes],
  );
  const updateNodeMutation = useMutation({
    mutationFn: ({ nodeId, payload }: { nodeId: number; payload: NodeDraft }) => api.updateNode(Number(id), nodeId, {
      name: payload.name.trim(), placeName: payload.placeName.trim(), latitude: Number(payload.latitude), longitude: Number(payload.longitude),
      nodeType: payload.nodeType, plannedStart: payload.plannedStart, plannedEnd: payload.plannedEnd, cost: Number(payload.cost) || 0, sequenceOrder: Number(payload.sequenceOrder) || 1,
    }),
    onSuccess: () => { setEditingNodeId(null); setDraft(null); void queryClient.invalidateQueries({ queryKey: ["trip", id] }); },
  });
  const deleteNodeMutation = useMutation({
    mutationFn: (nodeId: number) => api.deleteNode(Number(id), nodeId),
    onSuccess: () => { setEditingNodeId(null); setDraft(null); setSelectedNode(null); void queryClient.invalidateQueries({ queryKey: ["trip", id] }); },
  });
  const addNodeMutation = useMutation({
    mutationFn: (payload: NodeDraft) => api.addNode(Number(id), {
      name: (payload.name || payload.placeName).trim(), placeName: payload.placeName.trim(), latitude: Number(payload.latitude), longitude: Number(payload.longitude),
      nodeType: payload.nodeType, plannedStart: payload.plannedStart, plannedEnd: payload.plannedEnd, cost: Number(payload.cost) || 0, sequenceOrder: Number(payload.sequenceOrder) || 1, status: "PLANNED",
    }),
    onSuccess: () => { setAddOpen(false); void queryClient.invalidateQueries({ queryKey: ["trip", id] }); },
  });
  if (tripQuery.isLoading) return <LoadingState label="正在加载路线…" />;
  if (tripQuery.isError) return <ErrorState onRetry={() => void tripQuery.refetch()} message={tripQuery.error instanceof Error ? tripQuery.error.message : undefined} />;
  const trip = tripQuery.data;
  if (!trip) return <EmptyState title="找不到这段行程" message="它可能已被删除，或者你还没有加入对应的小组。" />;
  const totalBudget = trip.totalBudget ?? 0;
  const spentBudget = (expensesQuery.data ?? []).reduce((sum, item) => sum + item.amount, 0);
  const spendPercent = totalBudget > 0 ? Math.min(100, Math.round((spentBudget / totalBudget) * 100)) : 0;
  const eventsByNode = (impactsQuery.data ?? []).reduce<Record<number, ExternalEvent[]>>((map, impact) => {
    if (impact.affectedNode?.id && impact.event) map[impact.affectedNode.id] = [...(map[impact.affectedNode.id] ?? []), impact.event];
    return map;
  }, {});
  const setDraftField = <K extends keyof NodeDraft>(key: K, value: NodeDraft[K]) => setDraft((current) => current ? { ...current, [key]: value } : current);
  const startEditing = (node: ItineraryNode) => { setEditingNodeId(node.id); setDraft(draftFromNode(node)); updateNodeMutation.reset(); deleteNodeMutation.reset(); };
  const cancelEditing = () => { setEditingNodeId(null); setDraft(null); updateNodeMutation.reset(); deleteNodeMutation.reset(); };
  const saveEditing = (event: FormEvent) => { event.preventDefault(); if (editingNodeId !== null && draft) updateNodeMutation.mutate({ nodeId: editingNodeId, payload: draft }); };
  const editingError = updateNodeMutation.error ?? deleteNodeMutation.error ?? addNodeMutation.error;
  const setAddField = <K extends keyof NodeDraft>(key: K, value: NodeDraft[K]) => setAddDraft((current) => ({ ...current, [key]: value }));
  const openAdd = () => {
    cancelEditing();
    const nextOrder = (tripQuery.data?.itineraryNodes ?? []).reduce((max, node) => Math.max(max, node.sequenceOrder), 0) + 1;
    const startBase = tripQuery.data?.startDate ? `${tripQuery.data.startDate}T09:00` : "";
    const endBase = tripQuery.data?.startDate ? `${tripQuery.data.startDate}T11:00` : "";
    setAddDraft(emptyNodeDraft(nextOrder, startBase, endBase));
    addNodeMutation.reset();
    setAddOpen(true);
  };
  const submitAdd = (event: FormEvent) => {
    event.preventDefault();
    addNodeMutation.mutate(addDraft);
  };
  const shareTrip = async () => {
    const shareText = `【${trip.title ?? "未命名行程"}】${window.location.origin}/trips/${trip.id}${trip.roomCode ? ` · 房间码 ${trip.roomCode}` : ""}`;
    try {
      await navigator.clipboard.writeText(shareText);
      setShareFeedback("链接已复制，发给同伴吧");
    } catch {
      setShareFeedback(shareText);
    }
    window.setTimeout(() => setShareFeedback(""), 3000);
  };
  return <>
    <div className="mb-7 flex items-center gap-3 text-sm text-ink-soft"><Link to="/" className="flex items-center gap-2 transition hover:text-ink motion-reduce:transition-none"><ArrowLeft size={16} />返回首页</Link><span>/</span><span className="text-ink">{trip.title ?? "未命名行程"}</span></div>
    <PageHeader eyebrow="TRIP · LIVE ROUTE" title={trip.title ?? "未命名行程"} description="每个节点都在和现实保持同步。出现变化时，团队会一起决定下一步。" action={<div className="flex gap-2"><Link to={`/trips/${trip.id}/changelog`}><Button variant="ghost">变更记录</Button></Link><Button variant="ghost" className="flex items-center gap-2" onClick={() => void shareTrip()}><Share2 size={16} />分享</Button><div className="relative"><Button variant="secondary" className="p-3" aria-label="更多操作" onClick={() => setMenuOpen((current) => !current)}><MoreHorizontal size={18} /></Button>{menuOpen && <div className="absolute right-0 z-20 mt-2 w-44 overflow-hidden rounded-xl border border-slate-100 bg-white shadow-soft">{[{ label: "行程讨论区", to: "/discussions" }, { label: "预算与费用", to: "/budget" }, { label: "分账与结算", to: "/settlement" }, { label: "替代方案", to: "/plans" }].map((item) => <button key={item.to} type="button" onClick={() => { setMenuOpen(false); navigate(item.to); }} className="block w-full px-4 py-3 text-left text-sm text-ink-soft transition hover:bg-paper hover:text-ink">{item.label}</button>)}</div>}</div></div>} />
    {shareFeedback && <div className="mb-4 rounded-xl bg-mint/10 px-4 py-3 text-sm text-emerald-700" role="status">{shareFeedback}</div>}
    <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]"><div>
      <TripMapCard
        nodes={overviewNodes}
        overviewNodes={topLevelNodes}
        onMarkerClick={setSelectedNode}
      />
      <Card className="p-6 md:p-8"><div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5"><div><div className="flex items-center gap-2"><Badge tone="mint">{trip.status ?? "未知状态"}</Badge>{trip.roomCode && <span className="font-mono text-xs text-ink-soft">{trip.roomCode}</span>}</div><h2 className="mt-3 font-display text-2xl font-bold text-ink">今天的路线</h2></div><div className="flex items-center gap-4 text-xs text-ink-soft">{trip.startDate && trip.endDate && <span className="flex items-center gap-1.5"><CalendarDays size={15} />{trip.startDate} — {trip.endDate}</span>}{trip.group && <span className="flex items-center gap-1.5"><Users size={15} />{trip.group.members?.length ?? trip.group.memberCount ?? 0}人协作</span>}</div></div><RouteTrail nodes={trip.itineraryNodes ?? []} eventsByNode={eventsByNode} onNodeClick={setSelectedNode} showSegments showNearby /></Card>
      <Card className="mt-5 p-6"><div className="flex items-start justify-between gap-4"><div><p className="eyebrow">NODE EDITOR</p><h2 className="mt-2 font-display text-xl font-bold text-ink">自由增删调整节点</h2></div><div className="flex items-center gap-3"><Badge tone="sky">{trip.itineraryNodes?.length ?? 0} 个节点</Badge><Button type="button" variant="secondary" onClick={() => addOpen ? setAddOpen(false) : openAdd()} className="inline-flex items-center gap-1.5 px-3 py-2 text-xs"><Plus size={14} />{addOpen ? "收起" : "新增节点"}</Button></div></div>{editingError && <div className="mt-4 rounded-xl border border-coral/20 bg-coral/5 px-4 py-3 text-sm text-coral-deep">{editingError instanceof Error ? editingError.message : "节点操作失败，请稍后重试。"}</div>}{addOpen && <form onSubmit={submitAdd} className="mt-5 rounded-card border border-sky/25 bg-sky/5 p-4"><div className="flex items-center justify-between gap-3"><p className="font-semibold text-ink">新增节点</p><Badge tone="sky">#{addDraft.sequenceOrder}</Badge></div><div className="mt-4 grid gap-3 md:grid-cols-2">
      <label className="text-xs font-semibold text-ink">地点名称<input required value={addDraft.placeName} onChange={(event) => { setAddField("placeName", event.target.value); setAddField("name", event.target.value); }} placeholder="外滩、故宫…" className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">节点类型<select value={addDraft.nodeType} onChange={(event) => setAddField("nodeType", event.target.value as NodeType)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm"><option value="ATTRACTION">景点</option><option value="MEAL">餐饮</option><option value="LODGING">住宿</option><option value="TRANSPORT">交通</option><option value="OTHER">其他</option></select></label>
      <label className="text-xs font-semibold text-ink">开始时间<input type="datetime-local" required value={addDraft.plannedStart} onChange={(event) => setAddField("plannedStart", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">结束时间<input type="datetime-local" required value={addDraft.plannedEnd} onChange={(event) => setAddField("plannedEnd", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">纬度<input type="number" step="any" required value={addDraft.latitude} onChange={(event) => setAddField("latitude", event.target.value)} placeholder="31.24" className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">经度<input type="number" step="any" required value={addDraft.longitude} onChange={(event) => setAddField("longitude", event.target.value)} placeholder="121.49" className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">顺序<input type="number" min="1" value={addDraft.sequenceOrder} onChange={(event) => setAddField("sequenceOrder", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      <label className="text-xs font-semibold text-ink">费用<input type="number" min="0" step="0.01" value={addDraft.cost} onChange={(event) => setAddField("cost", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
    </div><div className="mt-4 flex flex-wrap gap-2"><Button type="submit" disabled={addNodeMutation.isPending} className="inline-flex items-center gap-2"><Check size={15} />{addNodeMutation.isPending ? "添加中…" : "添加节点"}</Button><Button type="button" variant="ghost" onClick={() => setAddOpen(false)} disabled={addNodeMutation.isPending} className="inline-flex items-center gap-2"><X size={15} />取消</Button></div></form>}<div className="mt-5 space-y-3">{(trip.itineraryNodes ?? []).map((node) => editingNodeId === node.id && draft ? <form key={node.id} onSubmit={saveEditing} className="rounded-card border border-coral/20 bg-coral/5 p-4 transition motion-reduce:transition-none"><div className="flex items-center justify-between gap-3"><div><p className="font-semibold text-ink">编辑节点</p><p className="mt-1 text-xs text-ink-soft">状态由事件影响流程管理，不在这里修改。</p></div><Badge tone="coral">{node.sequenceOrder}</Badge></div><div className="mt-4 grid gap-3 md:grid-cols-2">
        <label className="text-xs font-semibold text-ink">地点名称<input value={draft.placeName} onChange={(event) => { setDraftField("placeName", event.target.value); setDraftField("name", event.target.value); }} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">节点类型<select value={draft.nodeType} onChange={(event) => setDraftField("nodeType", event.target.value as NodeType)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm"><option value="ATTRACTION">景点</option><option value="MEAL">餐饮</option><option value="LODGING">住宿</option><option value="TRANSPORT">交通</option><option value="OTHER">其他</option></select></label>
        <label className="text-xs font-semibold text-ink">开始时间<input type="datetime-local" required value={draft.plannedStart} onChange={(event) => setDraftField("plannedStart", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">结束时间<input type="datetime-local" required value={draft.plannedEnd} onChange={(event) => setDraftField("plannedEnd", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">纬度<input type="number" step="any" value={draft.latitude} onChange={(event) => setDraftField("latitude", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">经度<input type="number" step="any" value={draft.longitude} onChange={(event) => setDraftField("longitude", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">顺序<input type="number" min="1" value={draft.sequenceOrder} onChange={(event) => setDraftField("sequenceOrder", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
        <label className="text-xs font-semibold text-ink">费用<input type="number" min="0" step="0.01" value={draft.cost} onChange={(event) => setDraftField("cost", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label>
      </div><div className="mt-4 flex flex-wrap gap-2"><Button type="submit" disabled={updateNodeMutation.isPending} className="inline-flex items-center gap-2"><Check size={15} />{updateNodeMutation.isPending ? "保存中…" : "保存修改"}</Button><Button type="button" variant="ghost" onClick={cancelEditing} disabled={updateNodeMutation.isPending} className="inline-flex items-center gap-2"><X size={15} />取消</Button><Button type="button" variant="ghost" onClick={() => { if (window.confirm("确认删除这个节点吗？")) deleteNodeMutation.mutate(node.id); }} disabled={deleteNodeMutation.isPending || updateNodeMutation.isPending} className="inline-flex items-center gap-2 text-coral-deep"><Trash2 size={15} />{deleteNodeMutation.isPending ? "删除中…" : "删除节点"}</Button></div></form> : <div key={node.id} className="flex items-center justify-between gap-3 rounded-card border border-slate-100 bg-paper/50 p-4 transition hover:-translate-y-0.5 hover:border-coral/20 hover:bg-coral/5 motion-reduce:transition-none"><button type="button" onClick={() => setSelectedNode(node)} className="min-w-0 text-left"><div className="flex flex-wrap items-center gap-2"><span className="font-semibold text-ink">{node.placeName || node.name}</span><Badge tone="neutral">{node.nodeType}</Badge><span className="font-mono text-[11px] text-ink-soft">#{node.sequenceOrder}</span></div><p className="mt-1 text-xs text-ink-soft">{node.plannedStart.replace("T", " ")} — {node.plannedEnd.slice(11, 16)}</p></button><Button type="button" variant="ghost" onClick={() => startEditing(node)} className="inline-flex shrink-0 items-center gap-2"><Pencil size={14} />编辑</Button></div>)}</div></Card>
    </div><div className="space-y-5"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">RISK STATUS</p><h2 className="mt-2 font-display text-xl font-bold text-ink">这条路线还安全吗？</h2></div><Badge tone="risk">{trip.riskLabel ?? "暂无评级"}</Badge></div><div className="mt-6 flex items-center gap-5"><RiskGauge score={trip.riskScore ?? 0} label="总体风险" /><div className="flex-1"><p className="text-sm leading-6 text-ink-soft">风险详情和替代方案会在外部事件命中行程节点后出现。</p><Link to="/plans" className="mt-4 inline-flex text-sm font-semibold text-sky">查看替代方案 →</Link></div></div></Card><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">BUDGET TRACKER</p><h2 className="mt-2 font-display text-xl font-bold text-ink">预算进度</h2></div><CircleDollarSign size={20} className="text-mint" /></div><div className="mt-6 flex items-end justify-between"><p className="font-mono text-2xl font-bold text-ink">¥{spentBudget.toLocaleString()}</p><p className="text-xs text-ink-soft">/ ¥{totalBudget.toLocaleString()}</p></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-mint" style={{ width: `${spendPercent}%` }} /></div><div className="mt-3 flex justify-between text-xs text-ink-soft"><span>已使用 {spendPercent}%</span><span>剩余 ¥{Math.max(0, totalBudget - spentBudget).toLocaleString()}</span></div></Card><Card className="overflow-hidden bg-coral p-6 text-white"><p className="eyebrow text-white/60">TEAM DECISION</p><h2 className="mt-3 font-display text-xl font-bold">需要团队共同确认下一步</h2><p className="mt-2 text-sm leading-6 text-white/75">当有替代方案进入投票状态后，成员可以在投票中心完成确认。</p><Link to="/votes"><Button variant="secondary" className="mt-5 w-full">去投票</Button></Link></Card></div></div>
    {selectedNode && <MapNodeDetailSheet key={selectedNode.id} node={selectedNode} trip={trip} onClose={() => setSelectedNode(null)} />}
  </>;
}

function TripMapCard({
  nodes,
  overviewNodes,
  onMarkerClick,
}: {
  nodes: ItineraryNode[];
  overviewNodes: ItineraryNode[];
  onMarkerClick: (node: ItineraryNode) => void;
}) {
  return (
    <Card className="mb-5 overflow-hidden border-sky/20 bg-surface p-0 shadow-soft">
      <div className="border-b border-slate-100 bg-gradient-to-r from-ink via-[#1d4e83] to-sky px-5 py-5 text-white md:px-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="font-mono text-[10px] tracking-[0.24em] text-coral">TRIP MAP / FULL ROUTE</p>
            <h2 className="mt-2 font-display text-2xl font-bold">把整段行程串在一张地图上</h2>
            <p className="mt-1 text-sm text-white/70">{overviewNodes.length} 个节点 · 点击地图标记查看完整详情</p>
          </div>
          <div className="flex items-center gap-2 rounded-full bg-white/10 px-3 py-2 text-xs text-white/80">
            <MapIcon size={14} />
            全程概览
          </div>
        </div>
      </div>
      <div className="grid gap-5 p-4 md:p-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(260px,0.55fr)]">
        <BaiduMap
          nodes={nodes}
          places={[]}
          selectedPlace={null}
          onMarkerClick={onMarkerClick}
          className="h-[360px] min-h-[360px] md:h-[430px] md:min-h-[430px]"
        />
        <div className="rounded-card border border-slate-100 bg-paper/60 p-4">
          <p className="eyebrow text-sky">FULL ROUTE</p>
          <h3 className="mt-1 font-display text-xl font-bold text-ink">行程路段</h3>
          <p className="mt-1 text-xs leading-5 text-ink-soft">地图会跟随主节点顺序连接；点击节点标记打开完整详情。</p>
          <div className="mt-4 space-y-2">
            {overviewNodes.length < 2 && <p className="rounded-xl bg-white px-3 py-3 text-xs text-ink-soft">至少需要两个节点才能展示路段。</p>}
            {overviewNodes.slice(0, -1).map((from, index) => (
              <TripMapSegment key={`${from.id}-${overviewNodes[index + 1].id}`} from={from} to={overviewNodes[index + 1]} index={index} />
            ))}
          </div>
        </div>
      </div>
    </Card>
  );
}

function TripMapSegment({ from, to, index }: { from: ItineraryNode; to: ItineraryNode; index: number }) {
  const mode = tripSegmentMode(from, to);
  const routeQuery = useQuery({
    queryKey: ["route-trail-segment", from.id, to.id, mode],
    queryFn: () => api.mapRoute(from.latitude, from.longitude, to.latitude, to.longitude, mode),
    enabled: hasCoordinates(from) && hasCoordinates(to),
  });
  const route = routeQuery.data;
  const ModeIcon = mode === "walking" ? Footprints : Car;
  return (
    <div className="flex items-center gap-3 rounded-xl border border-white bg-white px-3 py-2.5">
      <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-coral/10 text-coral"><ModeIcon size={14} /></span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-semibold text-ink">第 {index + 1} 段 · {mode === "walking" ? "步行" : "驾车"}</span>
        <span className="mt-0.5 block text-[10px] text-ink-soft">{from.placeName} → {to.placeName}</span>
      </span>
      <span className="shrink-0 text-right font-mono text-[10px] text-ink-soft">
        {route?.available && route.durationSeconds !== undefined && route.distanceMeters !== undefined ? `${formatMapDuration(route.durationSeconds)} · ${formatMapDistance(route.distanceMeters)}` : "暂不可用"}
      </span>
    </div>
  );
}

function tripSegmentMode(from: ItineraryNode, to: ItineraryNode): "walking" | "driving" {
  return haversine(from.latitude, from.longitude, to.latitude, to.longitude) <= 2 ? "walking" : "driving";
}

function hasCoordinates(node: ItineraryNode) {
  return Number.isFinite(node.latitude) && Number.isFinite(node.longitude) && node.latitude !== 0 && node.longitude !== 0;
}

function haversine(lat1: number, lng1: number, lat2: number, lng2: number) {
  const radians = Math.PI / 180;
  const latitudeDelta = (lat2 - lat1) * radians;
  const longitudeDelta = (lng2 - lng1) * radians;
  const value = Math.sin(latitudeDelta / 2) ** 2 + Math.cos(lat1 * radians) * Math.cos(lat2 * radians) * Math.sin(longitudeDelta / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}

function formatMapDistance(meters: number) {
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}

function formatMapDuration(seconds: number) {
  const minutes = Math.max(1, Math.round(seconds / 60));
  return minutes < 60 ? `${minutes} 分钟` : `${Math.floor(minutes / 60)} 小时${minutes % 60 ? ` ${minutes % 60} 分钟` : ""}`;
}
