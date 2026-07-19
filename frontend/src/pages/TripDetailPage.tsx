import { ArrowLeft, CalendarDays, CircleDollarSign, MoreHorizontal, Share2, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge, Button, Card, PageHeader, RiskGauge, RouteTrail } from "../components/ui";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import type { ExternalEvent } from "../types";

export function TripDetailPage() {
  const { id = "1" } = useParams();
  const { data: trip, isLoading, isError, error, refetch } = useQuery({ queryKey: ["trip", id], queryFn: () => api.trip(Number(id)) });
  const impactsQuery = useQuery({ queryKey: ["impacts", id], queryFn: () => api.impacts(Number(id)), enabled: Boolean(id) });
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
  return <><div className="mb-7 flex items-center gap-3 text-sm text-ink-soft"><Link to="/" className="flex items-center gap-2 transition hover:text-ink"><ArrowLeft size={16} />返回首页</Link><span>/</span><span className="text-ink">{trip.title ?? "未命名行程"}</span></div><PageHeader eyebrow="TRIP · LIVE ROUTE" title={trip.title ?? "未命名行程"} description="每个节点都在和现实保持同步。出现变化时，团队会一起决定下一步。" action={<div className="flex gap-2"><Link to={`/trips/${trip.id}/changelog`}><Button variant="ghost">变更记录</Button></Link><Button variant="ghost" className="flex items-center gap-2"><Share2 size={16} />分享</Button><Button variant="secondary" className="p-3" aria-label="更多操作"><MoreHorizontal size={18} /></Button></div>} /><div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]"><Card className="p-6 md:p-8"><div className="mb-8 flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-5"><div><div className="flex items-center gap-2"><Badge tone="mint">{trip.status ?? "未知状态"}</Badge>{trip.roomCode && <span className="font-mono text-xs text-ink-soft">{trip.roomCode}</span>}</div><h2 className="mt-3 font-display text-2xl font-bold text-ink">今天的路线</h2></div><div className="flex items-center gap-4 text-xs text-ink-soft">{trip.startDate && trip.endDate && <span className="flex items-center gap-1.5"><CalendarDays size={15} />{trip.startDate} — {trip.endDate}</span>}{trip.group && <span className="flex items-center gap-1.5"><Users size={15} />{trip.group.members?.length ?? trip.group.memberCount ?? 0}人协作</span>}</div></div><RouteTrail nodes={trip.itineraryNodes ?? []} eventsByNode={eventsByNode} /></Card><div className="space-y-5"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">RISK STATUS</p><h2 className="mt-2 font-display text-xl font-bold text-ink">这条路线还安全吗？</h2></div><Badge tone="risk">{trip.riskLabel ?? "暂无评级"}</Badge></div><div className="mt-6 flex items-center gap-5"><RiskGauge score={trip.riskScore ?? 0} label="总体风险" /><div className="flex-1"><p className="text-sm leading-6 text-ink-soft">风险详情和替代方案会在外部事件命中行程节点后出现。</p><Link to="/plans" className="mt-4 inline-flex text-sm font-semibold text-sky">查看替代方案 →</Link></div></div></Card><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">BUDGET TRACKER</p><h2 className="mt-2 font-display text-xl font-bold text-ink">预算进度</h2></div><CircleDollarSign size={20} className="text-mint" /></div><div className="mt-6 flex items-end justify-between"><p className="font-mono text-2xl font-bold text-ink">¥{spentBudget.toLocaleString()}</p><p className="text-xs text-ink-soft">/ ¥{totalBudget.toLocaleString()}</p></div><div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full rounded-full bg-mint" style={{ width: `${spendPercent}%` }} /></div><div className="mt-3 flex justify-between text-xs text-ink-soft"><span>已使用 {spendPercent}%</span><span>剩余 ¥{Math.max(0, totalBudget - spentBudget).toLocaleString()}</span></div></Card><Card className="overflow-hidden bg-coral p-6 text-white"><p className="eyebrow text-white/60">TEAM DECISION</p><h2 className="mt-3 font-display text-xl font-bold">需要团队共同确认下一步</h2><p className="mt-2 text-sm leading-6 text-white/75">当有替代方案进入投票状态后，成员可以在投票中心完成确认。</p><Link to="/votes"><Button variant="secondary" className="mt-5 w-full">去投票</Button></Link></Card></div></div></>;
}
