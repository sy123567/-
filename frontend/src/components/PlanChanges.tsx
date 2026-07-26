import { useState } from "react";
import { ArrowRight, Check, Footprints, Landmark, Repeat, Sparkles, Star, Users, Wallet } from "lucide-react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge } from "./ui";
import type { PlanCandidate } from "../types";

const CHANGE_TYPE_LABEL: Record<string, string> = { RESCHEDULE: "调整时间", REPLACE: "替换节点", REMOVE: "移除节点", ADD: "新增节点" };

const SOURCE_LABEL: Record<string, string> = { ai: "AI 提名", nearby: "地图就近", community: "队伍走过", member: "成员选择" };

export interface PlanChangeView {
  id?: number;
  key: number | string;
  type: string;
  fromPlace: string;
  fromStart?: string;
  fromEnd?: string;
  fromCost?: number;
  toPlace?: string;
  toStart?: string;
  toEnd?: string;
  toCost?: number;
  note: string;
}

/**
 * 一条节点变更：左边原安排、右边调整后的安排，方案还没进投票时可以展开候选地点重新挑选。
 */
export function PlanChangeCard({
  change,
  editable,
  formatRange,
  onChosen,
  onError,
}: {
  change: PlanChangeView;
  editable: boolean;
  formatRange: (start?: string, end?: string) => string;
  onChosen: (placeName: string) => void;
  onError: (message: string) => void;
}) {
  const [skyOpen, setSkyOpen] = useState(false);
  return (
    <div className="rounded-card border border-slate-100 bg-white p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={change.type === "REMOVE" ? "coral" : change.type === "REPLACE" ? "sky" : "sun"}>{CHANGE_TYPE_LABEL[change.type] ?? change.type}</Badge>
        <span className="text-xs text-ink-soft">{change.note}</span>
        {editable && change.id !== undefined && (
          <button
            type="button"
            onClick={() => setSkyOpen((open) => !open)}
            aria-expanded={skyOpen}
            className="ml-auto flex items-center gap-1.5 rounded-full bg-ink px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-ink/85"
          >
            <Repeat size={13} />
            {skyOpen ? "收起可选地点" : "换个地点"}
          </button>
        )}
      </div>
      <div className="mt-3 grid grid-cols-[1fr_auto_1fr] items-stretch gap-2">
        <div className="rounded-xl bg-paper p-3">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">原安排</p>
          <p className="mt-1 text-sm font-semibold text-ink line-through decoration-coral/50">{change.fromPlace}</p>
          <p className="mt-1 text-xs text-ink-soft">{formatRange(change.fromStart, change.fromEnd)}</p>
          {change.fromCost !== undefined && <p className="text-xs text-ink-soft">费用 ¥{change.fromCost}</p>}
        </div>
        <div className="grid place-items-center px-1 text-coral"><ArrowRight size={20} /></div>
        {change.type === "REMOVE" ? (
          <div className="grid place-items-center rounded-xl bg-coral/5 p-3 text-center ring-1 ring-coral/20">
            <div>
              <p className="text-sm font-bold text-coral-deep">移除该节点</p>
              <p className="mt-1 text-xs text-ink-soft">该事件无法安全避让</p>
            </div>
          </div>
        ) : (
          <div className="rounded-xl bg-mint/10 p-3 ring-1 ring-mint/30">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-emerald-700">调整为</p>
            <p className="mt-1 text-sm font-bold text-emerald-800">{change.toPlace ?? change.fromPlace}</p>
            <p className="mt-1 text-xs text-emerald-700">{formatRange(change.toStart, change.toEnd)}</p>
            {change.toCost !== undefined && (
              <p className="text-xs text-emerald-700">
                费用 ¥{change.toCost}
                {change.fromCost !== undefined && (
                  <span className="ml-1 font-semibold">（{Number(change.toCost) - Number(change.fromCost) >= 0 ? "+" : ""}{Number(change.toCost) - Number(change.fromCost)}）</span>
                )}
              </p>
            )}
          </div>
        )}
      </div>
      {skyOpen && change.id !== undefined && (
        <CandidateSky changeId={change.id} currentPlace={change.toPlace} onChosen={onChosen} onError={onError} />
      )}
    </div>
  );
}

/**
 * 候选地点"念头天空"：每个通过校验的地点是一团缓慢漂浮的想法，选中后落定为方案里的替代节点。
 */
function CandidateSky({
  changeId,
  currentPlace,
  onChosen,
  onError,
}: {
  changeId: number;
  currentPlace?: string;
  onChosen: (placeName: string) => void;
  onError: (message: string) => void;
}) {
  const candidatesQuery = useQuery({ queryKey: ["plan-candidates", changeId], queryFn: () => api.planChangeCandidates(changeId) });
  const choose = useMutation({
    mutationFn: (candidate: PlanCandidate) => api.choosePlanReplacement(changeId, candidate),
    onSuccess: (_result, candidate) => onChosen(candidate.name),
    onError: (cause) => onError(cause instanceof Error ? cause.message : "换地点失败，请稍后再试"),
  });
  const candidates = candidatesQuery.data ?? [];
  return (
    <section className="relative mt-4 overflow-hidden rounded-[28px] bg-gradient-to-b from-sky/10 via-white to-paper p-5">
      <div className="sky-glow pointer-events-none absolute -left-10 -top-16 h-52 w-52 rounded-full bg-sky/20 blur-3xl" />
      <div className="sky-glow pointer-events-none absolute -right-16 top-10 h-64 w-64 rounded-full bg-coral/10 blur-3xl" style={{ animationDelay: "-6s" }} />
      <div className="relative flex flex-wrap items-end justify-between gap-2">
        <div>
          <p className="eyebrow">FLOATING OPTIONS</p>
          <h3 className="mt-1 font-display text-lg font-bold text-ink">还可以去这些地方</h3>
          <p className="mt-1 text-xs leading-5 text-ink-soft">都已通过预算、体力、饮食、天气和事件校验。点一团想法，它就会落进方案里。</p>
        </div>
        <span className="rounded-full bg-white/70 px-3 py-1 text-[11px] font-semibold text-ink-soft">{candidates.length} 个可选</span>
      </div>
      {candidatesQuery.isLoading && <p className="relative mt-6 text-sm text-ink-soft">正在从地图和其他队伍的路线里找可替代的地点…</p>}
      {candidatesQuery.isError && (
        <p className="relative mt-6 text-sm text-coral-deep" role="alert">候选地点没能加载出来，稍后再试一次。</p>
      )}
      {!candidatesQuery.isLoading && !candidatesQuery.isError && candidates.length === 0 && (
        <p className="relative mt-6 rounded-2xl bg-white/70 p-4 text-sm leading-6 text-ink-soft">附近暂时没有同时满足天气、预算和体力的地点。可以先顺延时间，或者在行程页手动改这个节点。</p>
      )}
      <div className="relative mt-6 grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {candidates.map((candidate, index) => {
          const settled = currentPlace === candidate.name;
          const pending = choose.isPending && choose.variables?.name === candidate.name;
          return (
            <button
              key={`${candidate.name}-${candidate.lat}`}
              type="button"
              disabled={choose.isPending}
              onClick={() => choose.mutate(candidate)}
              style={{
                "--drift-delay": `${-index * 1.7}s`,
                "--drift-duration": `${11 + (index % 4) * 1.6}s`,
                "--drift-x": `${index % 2 === 0 ? 9 : -7}px`,
                "--drift-y": `${-10 - (index % 3) * 4}px`,
                marginTop: index % 3 === 1 ? "1.25rem" : index % 3 === 2 ? "0.5rem" : undefined,
              } as React.CSSProperties}
              className={`thought group relative overflow-hidden p-5 text-left shadow-soft ring-1 backdrop-blur transition-shadow disabled:cursor-progress ${
                settled ? "thought-settled bg-ink text-white ring-ink" : "bg-white/85 ring-white hover:shadow-xl"
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className={`truncate font-display text-base font-bold ${settled ? "text-white" : "text-ink"}`}>{candidate.name}</p>
                  <p className={`mt-1 truncate text-[11px] ${settled ? "text-white/60" : "text-ink-soft"}`}>{candidate.address || candidate.category || "位置已核对"}</p>
                </div>
                <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-full ${settled ? "bg-coral text-white" : "bg-sky/10 text-sky"}`}>
                  {settled ? <Check size={15} /> : candidate.source === "ai" ? <Sparkles size={15} /> : candidate.source === "community" ? <Users size={15} /> : <Landmark size={15} />}
                </span>
              </div>
              <div className={`mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-[11px] ${settled ? "text-white/70" : "text-ink-soft"}`}>
                <span className="flex items-center gap-1"><Footprints size={12} />{candidate.distanceKm < 0.1 ? "就在附近" : `${candidate.distanceKm} km`}</span>
                {candidate.cost !== undefined && candidate.cost !== null && <span className="flex items-center gap-1"><Wallet size={12} />¥{candidate.cost}</span>}
                {candidate.rating ? <span className="flex items-center gap-1"><Star size={12} />{candidate.rating.toFixed(1)}{candidate.reviewCount ? ` · ${candidate.reviewCount} 条` : ""}</span> : null}
              </div>
              {candidate.highlights.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {candidate.highlights.map((highlight) => (
                    <span key={highlight} className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${settled ? "bg-white/15 text-white" : "bg-mint/15 text-emerald-700"}`}>{highlight}</span>
                  ))}
                </div>
              )}
              <p className={`mt-3 line-clamp-2 text-xs leading-5 ${settled ? "text-white/70" : "text-ink-soft"}`}>{candidate.reason}</p>
              <p className={`mt-3 text-[10px] font-mono uppercase tracking-widest ${settled ? "text-coral" : "text-ink-soft/70"}`}>
                {pending ? "落定中…" : settled ? "已落进方案" : SOURCE_LABEL[candidate.source] ?? candidate.source}
              </p>
            </button>
          );
        })}
      </div>
    </section>
  );
}
