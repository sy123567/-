import { useEffect, useState } from "react";
import { ArrowRight, Check, Footprints, Landmark, MinusCircle, PinOff, Sparkles, Star, Users, Vote, Wallet, X } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { Badge } from "./ui";
import type { NodeVoteChoice, PlanCandidate } from "../types";

const KEEP_PLAN_KEY = "__keep_plan__";

const CHOICE_LABEL: Record<NodeVoteChoice, string> = { CANDIDATE: "投给候选", KEEP_PLAN: "维持原安排", ABSTAIN: "弃权" };

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
 * 一条节点变更：左边原安排、右边调整后的安排，方案还没进整体投票时可以展开候选地点由成员投票决定换到哪里。
 */
export function PlanChangeCard({
  change,
  editable,
  memberId,
  formatRange,
  onChosen,
  onError,
}: {
  change: PlanChangeView;
  editable: boolean;
  memberId?: number;
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
            <Vote size={13} />
            {skyOpen ? "收起候选投票" : "投票换地点"}
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
        <CandidateSkyOverlay title={change.fromPlace} onClose={() => setSkyOpen(false)}>
          <CandidateSky changeId={change.id} currentPlace={change.toPlace} memberId={memberId} onChosen={onChosen} onError={onError} />
        </CandidateSkyOverlay>
      )}
    </div>
  );
}

/** 候选投票铺满屏幕展示，避免卡片被窄栏挤压或裁切。 */
function CandidateSkyOverlay({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [onClose]);
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-ink/40 p-3 backdrop-blur-sm sm:p-6" role="dialog" aria-modal="true" aria-label={`${title} 的候选地点投票`}>
      <div className="relative my-auto w-full max-w-[92rem]">
        <button
          type="button"
          onClick={onClose}
          aria-label="关闭候选投票"
          className="absolute right-4 top-4 z-10 grid h-10 w-10 place-items-center rounded-full bg-white/90 text-ink shadow-soft transition hover:bg-white"
        >
          <X size={18} />
        </button>
        {children}
      </div>
    </div>
  );
}

/**
 * 候选地点"念头天空"：每个通过校验的地点是一团缓慢漂浮的想法，选中后落定为方案里的替代节点。
 */
function CandidateSky({
  changeId,
  currentPlace,
  memberId,
  onChosen,
  onError,
}: {
  changeId: number;
  currentPlace?: string;
  memberId?: number;
  onChosen: (placeName: string) => void;
  onError: (message: string) => void;
}) {
  const queryClient = useQueryClient();
  const [comment, setComment] = useState("");
  // 候选在后端已固定，不需要反复重取；每次重取都会让漂浮的卡片重排，看起来像界面在刷新。
  const candidatesQuery = useQuery({
    queryKey: ["plan-candidates", changeId],
    queryFn: () => api.planChangeCandidates(changeId),
    staleTime: Infinity,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
  });
  // 票数要能看到其他成员的实时表态。
  const votesQuery = useQuery({
    queryKey: ["node-votes", changeId],
    queryFn: () => api.nodeVotes(changeId),
    refetchInterval: 5000,
  });
  const cast = useMutation({
    mutationFn: (input: { choice: NodeVoteChoice; candidate?: PlanCandidate }) =>
      api.castNodeVote(changeId, {
        memberId: memberId as number,
        choice: input.choice,
        placeName: input.candidate?.name,
        lat: input.candidate?.lat,
        lng: input.candidate?.lng,
        comment: comment.trim() || undefined,
      }),
    onSuccess: (tally) => {
      setComment("");
      void queryClient.invalidateQueries({ queryKey: ["node-votes", changeId] });
      if (tally.appliedOption) onChosen(tally.appliedOption);
    },
    onError: (cause) => onError(cause instanceof Error ? cause.message : "投票失败，请稍后再试"),
  });
  const candidates = candidatesQuery.data ?? [];
  const tally = votesQuery.data;
  const votesFor = (key: string) => tally?.options.find((option) => option.key === key || option.label === key);
  const vote = (choice: NodeVoteChoice, candidate?: PlanCandidate) => {
    if (memberId === undefined) {
      onError("你不是该行程小组的成员，无法参与节点投票");
      return;
    }
    cast.mutate({ choice, candidate });
  };
  return (
    <section className="relative overflow-hidden rounded-[28px] bg-gradient-to-b from-sky/10 via-white to-paper p-6 shadow-2xl sm:p-8">
      <div className="sky-glow pointer-events-none absolute -left-10 -top-16 h-52 w-52 rounded-full bg-sky/20 blur-3xl" />
      <div className="sky-glow pointer-events-none absolute -right-16 top-10 h-64 w-64 rounded-full bg-coral/10 blur-3xl" style={{ animationDelay: "-6s" }} />
      <div className="relative flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="eyebrow">FLOATING OPTIONS</p>
          <h3 className="mt-1 font-display text-lg font-bold text-ink">这个节点，大家想去哪</h3>
          <p className="mt-1 max-w-xl text-xs leading-5 text-ink-soft">候选都已通过预算、体力、饮食、天气和事件校验。每人一票（可改票）。某个选项获得全体成员过半支持，或全员表态后有唯一领先项时自动落定；平票不落定，继续投或换个选项。</p>
        </div>
        {tally && (
          <div className="rounded-2xl bg-white/75 px-4 py-2 text-right">
            <p className="font-mono text-sm font-bold text-ink">{tally.castCount}/{tally.totalMembers} 已表态</p>
            <p className="mt-0.5 text-[11px] text-ink-soft">
              {tally.appliedOption
                ? `已落定：${tally.appliedOption}`
                : tally.tie
                  ? "目前平票，等更多人投票"
                  : tally.quorumReached
                    ? "已过半参与，还差一个明确多数"
                    : "还没过半，继续投"}
              {tally.abstainCount > 0 ? ` · 弃权 ${tally.abstainCount}` : ""}
            </p>
          </div>
        )}
      </div>
      <label className="relative mt-4 block">
        <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">投票备注（可选）</span>
        <input
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          maxLength={200}
          placeholder="说说你为什么选它，比如“带老人，室内更稳妥”"
          className="mt-1 w-full rounded-xl border border-white bg-white/85 px-3 py-2.5 text-sm text-ink outline-none transition focus:border-sky"
        />
      </label>
      {candidatesQuery.isLoading && <p className="relative mt-6 text-sm text-ink-soft">正在从地图和其他队伍的路线里找可替代的地点…</p>}
      {candidatesQuery.isError && <p className="relative mt-6 text-sm text-coral-deep" role="alert">候选地点没能加载出来，稍后再试一次。</p>}
      {!candidatesQuery.isLoading && !candidatesQuery.isError && candidates.length === 0 && (
        <p className="relative mt-6 rounded-2xl bg-white/70 p-4 text-sm leading-6 text-ink-soft">附近暂时没有同时满足天气、预算和体力的地点。可以投「维持原安排」，或者在行程页手动改这个节点。</p>
      )}
      <div className="relative mt-6 grid items-start gap-6 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
        {candidates.map((candidate, index) => {
          const settled = currentPlace === candidate.name;
          const option = votesFor(candidate.name);
          const count = option?.count ?? 0;
          const pending = cast.isPending && cast.variables?.candidate?.name === candidate.name;
          return (
            <button
              key={`${candidate.name}-${candidate.lat}`}
              type="button"
              disabled={cast.isPending}
              onClick={() => vote("CANDIDATE", candidate)}
              style={{
                "--drift-delay": `${-index * 1.7}s`,
                "--drift-duration": `${11 + (index % 4) * 1.6}s`,
                "--drift-x": `${index % 2 === 0 ? 9 : -7}px`,
                "--drift-y": `${-10 - (index % 3) * 4}px`,
                marginTop: index % 3 === 1 ? "1.25rem" : index % 3 === 2 ? "0.5rem" : undefined,
              } as React.CSSProperties}
              className={`thought group relative flex h-full flex-col p-6 text-left shadow-soft ring-1 backdrop-blur transition-shadow disabled:cursor-progress ${
                settled ? "thought-settled bg-ink text-white ring-ink" : "bg-white/85 ring-white hover:shadow-xl"
              }`}
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className={`font-display text-base font-bold leading-6 ${settled ? "text-white" : "text-ink"}`}>{candidate.name}</p>
                  <p className={`mt-1 text-[11px] leading-5 ${settled ? "text-white/60" : "text-ink-soft"}`}>{candidate.address || candidate.category || "位置已核对"}</p>
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
              <p className={`mt-3 text-xs leading-5 ${settled ? "text-white/70" : "text-ink-soft"}`}>{candidate.reason}</p>
              <span className="flex-1" />
              <VoteFooter count={count} voters={option?.voters ?? []} settled={settled} pending={pending} hint={SOURCE_LABEL[candidate.source] ?? candidate.source} />
            </button>
          );
        })}
      </div>
      <div className="relative mt-5 grid gap-3 sm:grid-cols-2">
        <PlainOption
          icon={<PinOff size={15} />}
          title="维持方案原安排"
          detail={`继续用方案给的「${currentPlace ?? "原安排"}」，不再换地点`}
          count={votesFor(KEEP_PLAN_KEY)?.count ?? 0}
          voters={votesFor(KEEP_PLAN_KEY)?.voters ?? []}
          disabled={cast.isPending}
          onClick={() => vote("KEEP_PLAN")}
        />
        <PlainOption
          icon={<MinusCircle size={15} />}
          title="弃权"
          detail="不计入有效票，但会计入参与人数"
          count={tally?.abstainCount ?? 0}
          voters={[]}
          disabled={cast.isPending}
          onClick={() => vote("ABSTAIN")}
        />
      </div>
      {tally && tally.notes.length > 0 && (
        <div className="relative mt-5 rounded-2xl bg-white/70 p-4">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">投票与备注</p>
          <ul className="mt-3 space-y-2">
            {tally.notes.map((note, index) => (
              <li key={`${note.member}-${index}`} className="flex flex-wrap items-baseline gap-2 text-xs">
                <span className="font-semibold text-ink">{note.member}</span>
                <span className="rounded-full bg-sky/10 px-2 py-0.5 text-[10px] font-semibold text-sky">{CHOICE_LABEL[note.choice]}{note.option ? ` · ${note.option}` : ""}</span>
                {note.comment && <span className="text-ink-soft">“{note.comment}”</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function VoteFooter({ count, voters, settled, pending, hint }: { count: number; voters: string[]; settled: boolean; pending: boolean; hint: string }) {
  return (
    <div className="mt-3 flex items-center justify-between gap-2">
      <span className={`text-[10px] font-mono uppercase tracking-widest ${settled ? "text-coral" : "text-ink-soft/70"}`}>
        {pending ? "投票中…" : settled ? "已落进方案" : hint}
      </span>
      <span className={`flex items-center gap-1 rounded-full px-2 py-1 text-[10px] font-semibold ${count > 0 ? "bg-coral/10 text-coral-deep" : settled ? "bg-white/15 text-white/70" : "bg-paper text-ink-soft"}`}>
        <Vote size={11} />{count} 票{voters.length > 0 ? ` · ${voters.slice(0, 2).join("、")}${voters.length > 2 ? "等" : ""}` : ""}
      </span>
    </div>
  );
}

function PlainOption({
  icon,
  title,
  detail,
  count,
  voters,
  disabled,
  onClick,
}: {
  icon: React.ReactNode;
  title: string;
  detail: string;
  count: number;
  voters: string[];
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button type="button" disabled={disabled} onClick={onClick} className="flex items-center gap-3 rounded-2xl border border-dashed border-slate-300 bg-white/70 p-4 text-left transition hover:border-sky hover:bg-white disabled:opacity-60">
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-paper text-ink-soft">{icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold text-ink">{title}</span>
        <span className="mt-0.5 block text-[11px] leading-5 text-ink-soft">{detail}</span>
      </span>
      <span className={`shrink-0 rounded-full px-2 py-1 text-[10px] font-semibold ${count > 0 ? "bg-coral/10 text-coral-deep" : "bg-paper text-ink-soft"}`}>
        {count} 票{voters.length > 0 ? ` · ${voters.slice(0, 2).join("、")}${voters.length > 2 ? "等" : ""}` : ""}
      </span>
    </button>
  );
}
