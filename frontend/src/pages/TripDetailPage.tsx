import { useState, type FormEvent } from "react";
import { ArrowLeft, CalendarDays, Check, CircleDollarSign, MoreHorizontal, Pencil, Share2, Trash2, Users, X } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge, Button, Card, PageHeader, RiskGauge, RouteTrail } from "../components/ui";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import type { ExternalEvent, ItineraryNode, NodeType } from "../types";

type NodeDraft = {
  name: string;
  placeName: string;
  latitude: string;
  longitude: string;
  nodeType: NodeType;
  plannedStart: string;
  plannedEnd: string;
  cost: string;
  sequenceOrder: string;
};

function draftFromNode(node: ItineraryNode): NodeDraft {
  return {
    name: node.name,
    placeName: node.placeName,
    latitude: String(node.latitude),
    longitude: String(node.longitude),
    nodeType: node.nodeType,
    plannedStart: node.plannedStart.slice(0, 16),
    plannedEnd: node.plannedEnd.slice(0, 16),
    cost: String(node.cost),
    sequenceOrder: String(node.sequenceOrder),
  };
}

export function TripDetailPage() {
  const { id = "1" } = useParams();
  const queryClient = useQueryClient();
  const [editingNodeId, setEditingNodeId] = useState<number | null>(null);
  const [draft, setDraft] = useState<NodeDraft | null>(null);
  const { data: trip, isLoading, isError, error, refetch } = useQuery({ queryKey: ["trip", id], queryFn: () => api.trip(Number(id)) });
  const impactsQuery = useQuery({ queryKey: ["impacts", id], queryFn: () => api.impacts(Number(id)), enabled: Boolean(id) });
  const updateNodeMutation = useMutation({
    mutationFn: ({ nodeId, payload }: { nodeId: number; payload: NodeDraft }) =>
      api.updateNode(Number(id), nodeId, {
        name: payload.name.trim(),
        placeName: payload.placeName.trim(),
        latitude: Number(payload.latitude),
        longitude: Number(payload.longitude),
        nodeType: payload.nodeType,
        plannedStart: payload.plannedStart,
        plannedEnd: payload.plannedEnd,
        cost: Number(payload.cost) || 0,
        sequenceOrder: Number(payload.sequenceOrder) || 1,
      }),
    onSuccess: () => {
      setEditingNodeId(null);
      setDraft(null);
      void queryClient.invalidateQueries({ queryKey: ["trip", id] });
    },
  });
  const deleteNodeMutation = useMutation({
    mutationFn: (nodeId: number) => api.deleteNode(Number(id), nodeId),
    onSuccess: () => {
      setEditingNodeId(null);
      setDraft(null);
      void queryClient.invalidateQueries({ queryKey: ["trip", id] });
    },
  });
  if (isLoading) return <LoadingState label="正在加载路线…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!trip) return <EmptyState title="找不到这段行程" message="它可能已被删除，或者你还没有加入对应的小组。" />;
  const totalBudget = trip.totalBudget ?? 0;
  const spentBudget = trip.spentBudget ?? 0;
  const spendPercent = totalBudget > 0 ? Math.min(100, Math.round((spentBudget / totalBudget) * 100)) : 0;
  const eventsByNode = (impactsQuery.data ?? []).reduce<Record<number, ExternalEvent[]>>((map, impact) => {
    if (impact.affectedNode?.id && impact.event) {
      map[impact.affectedNode.id] = [...(map[impact.affectedNode.id] ?? []), impact.event];
    }
    return map;
  }, {});
  const setDraftField = <K extends keyof NodeDraft>(key: K, value: NodeDraft[K]) => {
    setDraft((current) => current ? { ...current, [key]: value } : current);
  };
  const startEditing = (node: ItineraryNode) => {
    setEditingNodeId(node.id);
    setDraft(draftFromNode(node));
    updateNodeMutation.reset();
    deleteNodeMutation.reset();
  };
  const cancelEditing = () => {
    setEditingNodeId(null);
    setDraft(null);
    updateNodeMutation.reset();
    deleteNodeMutation.reset();
  };
  const saveEditing = (event: FormEvent) => {
    event.preventDefault();
    if (editingNodeId !== null && draft) updateNodeMutation.mutate({ nodeId: editingNodeId, payload: draft });
  };
  const editingError = updateNodeMutation.error ?? deleteNodeMutation.error;
  return <><div className="mb-7 flex items-center gap-3 text-sm text-ink-soft"><Link to="/" className="flex items-center gap-2 transition hover:text-ink"><ArrowLeft size={16} />返回首页</Link><span>/</span><span className="text-ink">{trip.title ?? "未命名行程"}</span></div><PageHeader eyebrow="TRIP · LIVE ROUTE" title={trip.title ?? "未命名行程"} description="每个节点都在和现实保持同步。出现变化时，团队会一起决定下一步。" action={<div className="flex gap-2"><Link to={`/trips/${trip.id}/changelog`}><Button variant="ghost">变更记录</Button></Link><Button variant="ghost" className="flex items-center gap-2"><Share2 size={16} />分享</Button><Button variant="secondary" className="p-3" aria-label="更多操作"><MoreHorizontal size={18} /></Button></div>} /><div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]"><div><Card className="p-6 md:p-8"><div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5"><div><div className="flex items-center gap-2"><Badge tone="mint">{trip.status ?? "未知状态"}</Badge>{trip.roomCode && <span className="font-mono text-xs text-ink-soft">{trip.roomCode}</span>}</div><h2 className="mt-3 font-display text-2xl font-bold text-ink">今天的路线</h2></div><div className="flex items-center gap-4 text-xs text-ink-soft">{trip.startDate && trip.endDate && <span className="flex items-center gap-1.5"><CalendarDays size={15} />{trip.startDate} — {trip.endDate}</span>}{trip.group && <span className="flex items-center gap-1.5"><Users size={15} />{trip.group.members?.length ?? trip.group.memberCount ?? 0}人协作</span>}</div></div><RouteTrail nodes={trip.itineraryNodes ?? []} eventsByNode={eventsByNode} /></Card><Card className="mt-5 p-6"><div className="flex items-start justify-between gap-4"><div><p className="eyebrow">NODE EDITOR</p><h2 className="mt-2 font-display text-xl font-bold text-ink">调整路线节点</h2></div><Badge tone="sky">{trip.itineraryNodes?.length ?? 0} 个节点</Badge></div>{editingError && <div className="mt-4 rounded-xl border border-coral/20 bg-coral/5 px-4 py-3 text-sm text-coral-deep">{editingError instanceof Error ? editingError.message : "节点操作失败，请稍后重试。"}</div>}<div className="mt-5 space-y-3">{(trip.itineraryNodes ?? []).map((node) => editingNodeId === node.id && draft ? <form key={node.id} onSubmit={saveEditing} className="rounded-card border border-coral/20 bg-coral/5 p-4 transition motion-reduce:transition-none"><div className="flex items-center justify-between gap-3"><div><p className="font-semibold text-ink">编辑节点</p><p className="mt-1 text-xs text-ink-soft">状态由事件影响流程管理，不在这里修改。</p></div><Badge tone="coral">{node.sequenceOrder}</Badge></div><div className="mt-4 grid gap-3 md:grid-cols-2"><label className="text-xs font-semibold text-ink">地点名称<input value={draft.placeName} onChange={(event) => { setDraftField("placeName", event.target.value); setDraftField("name", event.target.value); }} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">节点类型<select value={draft.nodeType} onChange={(event) => setDraftField("nodeType", event.target.value as NodeType)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm"><option value="ATTRACTION">景点</option><option value="MEAL">餐饮</option><option value="LODGING">住宿</option><option value="TRANSPORT">交通</option><option value="OTHER">其他</option></select></label><label className="text-xs font-semibold text-ink">开始时间<input type="datetime-local" required value={draft.plannedStart} onChange={(event) => setDraftField("plannedStart", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">结束时间<input type="datetime-local" required value={draft.plannedEnd} onChange={(event) => setDraftField("plannedEnd", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">纬度<input type="number" step="any" value={draft.latitude} onChange={(event) => setDraftField("latitude", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">经度<input type="number" step="any" value={draft.longitude} onChange={(event) => setDraftField("longitude", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">顺序<input type="number" min="1" value={draft.sequenceOrder} onChange={(event) => setDraftField("sequenceOrder", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label><label className="text-xs font-semibold text-ink">费用<input type="number" min="0" step="0.01" value={draft.cost} onChange={(event) => setDraftField("cost", event.target.value)} className="mt-1 w-full rounded-xl border border-white bg-white px-3 py-2.5 text-sm" /></label></div><div className="mt-4 flex flex-wrap gap-2"><Button type="submit" disabled={updateNodeMutation.isPending} className="inline-flex items-center gap-2"><Check size={15} />{updateNodeMutation.isPending ? "保存中…" : "保存修改"}</Button><Button type="button" variant="ghost" onClick={cancelEditing} disabled={updateNodeMutation.isPending} className="inline-flex items-center gap-2"><X size={15} />取消</Button><Button type="button" variant="ghost" onClick={() => { if (window.confirm("确认删除这个节点吗？")) deleteNodeMutation.mutate(node.id); }} disabled={deleteNodeMutation.isPending || updateNodeMutation.isPending} className="inline-flex items-center gap-2 text-coral-deep"><Trash2 size={15} />{deleteNodeMutation.isPending ? "删除中…" : "删除节点"}</Button></div></form> : <div key={node.id} className="flex items-center justify-between gap-3 rounded-card border border-slate-100 bg-paper/50 p-4 transition hover:-translate-y-0.5 hover:border-coral/20 hover:bg-coral/5 motion-reduce:transition-none"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="font-semibold text-ink">{node.placeName || node.name}</span><Badge tone="neutral">{node.nodeType}</Badge><span className="font-mono text-[11px] text-ink-soft">#{node.sequenceOrder}</span></div><p className="mt-1 text-xs text-ink-soft">{node.plannedStart.replace("T", " ")} — {node.plannedEnd.slice(11, 16)}</p></div><Button type="button" variant="ghost" onClick={() => startEditing(node)} className="inline-flex shrink-0 items-center gap-2"><Pencil size={14} />编辑</Button></div>)}</div></Card></div><div className="space-y-5"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">RISK STATUS</p><h2 className="mt-2 font-display text-xl font-bold text-ink">这条路线还安全吗？</h2></div><Badge tone="risk">{trip.riskLabel ?? "暂无评级"}</Badge></div><div className="mt-6 flex items-center gap-5"><RiskGauge score={trip.riskScore ?? 0} label="总体风险" /><div className="flex-1"><p className="text-sm leading-6 text-ink-soft">风险详情和替代方案会在外部事件命中行程节点后出现。</p><Link to="/plans" className="mt-4 inline-flex text-sm font-semibold text-sky">查看替代方案 →</Link></div></div></Card><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">BUDGET TRACKER</p><h2 className="mt-2 font-display text-xl font-bold text-ink">预算进度</h2></div><CircleDollarSign size={20} className="text-mint" /></div><div className="mt-6 flex items-end justify-between"><p className="font-mono text-2xl font-bold text-ink">¥{spentBudget.toLocaleString()}</p><p className="text-xs text-ink-soft">/ ¥{totalBudget.toLocaleString()}</p></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-mint" style={{ width: `${spendPercent}%` }} /></div><div className="mt-3 flex justify-between text-xs text-ink-soft"><span>已使用 {spendPercent}%</span><span>剩余 ¥{Math.max(0, totalBudget - spentBudget).toLocaleString()}</span></div></Card><Card className="overflow-hidden bg-coral p-6 text-white"><p className="eyebrow text-white/60">TEAM DECISION</p><h2 className="mt-3 font-display text-xl font-bold">需要团队共同确认下一步</h2><p className="mt-2 text-sm leading-6 text-white/75">当有替代方案进入投票状态后，成员可以在投票中心完成确认。</p><Link to="/votes"><Button variant="secondary" className="mt-5 w-full">去投票</Button></Link></Card></div></div></>;
}
