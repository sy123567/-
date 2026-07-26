import { useState } from "react";
import { ArrowUpRight, Bell, CalendarDays, ChevronDown, ChevronRight, CloudRain, Compass, MapPin, Plus, Route, Send, Sparkles, Users } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { BoardingPassCard, Button, Card, EventIcon, Input, PageHeader, RiskGauge } from "../components/ui";
import { EmptyState, ErrorState, LoadingState } from "../components/AsyncState";
import type { AssistantAnswer } from "../types";

const ASSISTANT_PROMPTS = ["周末想去海边，有推荐吗？", "杭州三天怎么安排比较松弛？", "在哪里给成员填写时间和预算约束？", "行程被暴雨影响了要怎么改？"];

const ASSISTANT_SOURCE_LABEL: Record<string, string> = { ai: "AI 生成", local: "来自攻略与站内目录", offline: "离线提示" };

/** 首页智能体：优先用 AI 回答，AI 不可用时退回攻略社区检索与站内目录导航。 */
function TravelAssistant() {
  const navigate = useNavigate();
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AssistantAnswer | null>(null);
  const [errorText, setErrorText] = useState("");
  const ask = useMutation({
    mutationFn: (value: string) => api.assistant(value),
    onSuccess: (result) => { setAnswer(result); setErrorText(""); },
    onError: (cause) => { setAnswer(null); setErrorText(cause instanceof Error ? cause.message : "助手暂时没能回答，请稍后再试"); },
  });
  const submit = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed || ask.isPending) return;
    setQuestion(trimmed);
    ask.mutate(trimmed);
  };
  return (
    <Card className="relative overflow-hidden p-6">
      <div className="pointer-events-none absolute -right-14 -top-16 h-44 w-44 rounded-full border-[22px] border-sky/10" />
      <div className="relative flex items-start gap-3">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-ink text-coral"><Sparkles size={20} /></span>
        <div>
          <p className="eyebrow">TRAVEL COPILOT</p>
          <h2 className="mt-1 font-display text-xl font-bold text-ink">问问旅行助手</h2>
          <p className="mt-1 text-sm leading-6 text-ink-soft">想去哪、怎么安排、功能在哪一页，都可以直接问。</p>
        </div>
      </div>
      <form
        className="relative mt-5 flex gap-2"
        onSubmit={(event) => { event.preventDefault(); submit(question); }}
      >
        <Input
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="例如：五一三天两晚，带老人去哪比较合适？"
          maxLength={500}
          aria-label="向旅行助手提问"
        />
        <Button className="flex shrink-0 items-center gap-2" disabled={!question.trim() || ask.isPending}>
          <Send size={15} />{ask.isPending ? "思考中…" : "提问"}
        </Button>
      </form>
      <div className="relative mt-3 flex flex-wrap gap-2">
        {ASSISTANT_PROMPTS.map((prompt) => (
          <button key={prompt} type="button" onClick={() => submit(prompt)} disabled={ask.isPending} className="rounded-full bg-paper px-3 py-1.5 text-xs font-semibold text-ink-soft transition hover:bg-sky/10 hover:text-sky disabled:opacity-50">
            {prompt}
          </button>
        ))}
      </div>
      {errorText && <p className="relative mt-4 rounded-xl bg-coral/5 px-4 py-3 text-sm text-coral-deep" role="alert">{errorText}</p>}
      {ask.isPending && <p className="relative mt-4 text-sm text-ink-soft">正在为你查找攻略与建议…</p>}
      {answer && !ask.isPending && (
        <div className="relative mt-5 space-y-4">
          <div className="rounded-card bg-paper p-4">
            <span className="inline-flex items-center gap-1 rounded-full bg-white px-2 py-1 text-[11px] font-semibold text-ink-soft">
              <Compass size={12} />{ASSISTANT_SOURCE_LABEL[answer.source] ?? answer.source}
            </span>
            <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-ink">{answer.answer}</p>
          </div>
          {answer.guides.length > 0 && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">攻略社区里的相关内容</p>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {answer.guides.map((guide) => (
                  <button key={guide.id} type="button" onClick={() => navigate(`/guides/${guide.id}`)} className="card p-4 text-left transition hover:-translate-y-0.5">
                    <p className="font-semibold text-ink">{guide.title}</p>
                    <p className="mt-1 flex items-center gap-1 text-xs text-ink-soft"><MapPin size={12} />{guide.city || "不限城市"}{guide.theme ? ` · ${guide.theme}` : ""}</p>
                    {guide.description && <p className="mt-2 line-clamp-2 text-xs leading-5 text-ink-soft">{guide.description}</p>}
                  </button>
                ))}
              </div>
            </div>
          )}
          {answer.links.length > 0 && (
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft">可以直接去这些页面</p>
              <div className="mt-3 flex flex-wrap gap-2">
                {answer.links.map((link) => (
                  <Link key={link.path} to={link.path} className="flex items-center gap-1 rounded-full bg-sky/10 px-3 py-2 text-xs font-semibold text-sky transition hover:bg-sky/20">
                    {link.label}<ChevronRight size={13} />
                  </Link>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

export function DashboardPage() {
  const navigate = useNavigate();
  const [alertsOpen, setAlertsOpen] = useState(true);
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard });
  if (isLoading) return <LoadingState label="正在整理你的路线…" />;
  if (isError) return <ErrorState onRetry={() => void refetch()} message={error instanceof Error ? error.message : undefined} />;
  if (!data || !data.user || !data.activeTrip) return <><EmptyState title="还没有行程" message="先创建一段行程，旅伴和路线会在这里集合。" /><div className="mt-6"><TravelAssistant /></div></>;
  const activeTrip = data.activeTrip;
  return <><PageHeader eyebrow="YOUR TRAVEL BOARD" title={`早上好，${data.user.name}`} description="查看你的真实行程、天气事件和需要共同决定的下一步。" action={<Button className="flex items-center gap-2" onClick={() => navigate("/trips/new")}><Plus size={17} />新建行程</Button>} /><div className="grid gap-5 xl:grid-cols-[1.45fr_0.9fr]"><Card className="relative overflow-hidden bg-ink p-6 text-white md:p-8"><div className="absolute -right-16 -top-20 h-72 w-72 rounded-full border-[38px] border-white/5" /><div className="relative flex flex-col justify-between gap-10 md:flex-row"><div><div className="flex items-center gap-2 text-xs font-semibold text-mint"><span className="h-2 w-2 rounded-full bg-mint" />正在进行 · {activeTrip.title ?? "当前行程"}</div><h2 className="mt-4 max-w-md font-display text-3xl font-bold leading-tight md:text-4xl">{activeTrip.destination ?? activeTrip.title ?? "你的下一段旅程"}<br /><span className="text-coral">路线已准备好</span></h2><p className="mt-4 max-w-md text-sm leading-6 text-white/55">天气和外部事件会持续同步，出现变化时你可以和旅伴一起决定下一步。</p><Button className="mt-7 flex items-center gap-2" onClick={() => navigate(`/trips/${activeTrip.id}`)}>查看实时行程<ArrowUpRight size={16} /></Button></div><div className="flex shrink-0 items-end md:items-center"><RiskGauge score={activeTrip.riskScore ?? 0} label="当前风险" /></div></div><div className="relative mt-8 flex flex-wrap gap-5 border-t border-white/10 pt-5 text-xs text-white/50">{activeTrip.startDate && activeTrip.endDate && <span className="flex items-center gap-2"><CalendarDays size={15} className="text-coral" />{activeTrip.startDate} — {activeTrip.endDate}</span>}{activeTrip.group && <span className="flex items-center gap-2"><Users size={15} className="text-mint" />{activeTrip.group.members?.length ?? activeTrip.group.memberCount ?? 0} 位成员</span>}{activeTrip.roomCode && <span className="flex items-center gap-2"><Route size={15} className="text-sky" />{activeTrip.roomCode}</span>}</div></Card><Card className="overflow-hidden p-0"><button type="button" aria-expanded={alertsOpen} onClick={() => setAlertsOpen((open) => !open)} className="flex w-full items-start justify-between p-6 text-left transition hover:bg-paper"><div><p className="eyebrow">LIVE ALERTS</p><h2 className="mt-2 font-display text-xl font-bold text-ink">需要你留意</h2></div><div className="flex items-center gap-3"><span className="rounded-full bg-coral/10 px-2 py-1 text-xs font-bold text-coral">{data.events.length} 条</span><ChevronDown size={18} className={`text-ink-soft transition-transform ${alertsOpen ? "rotate-180" : ""}`} /></div></button>{alertsOpen && <div className="space-y-4 px-6 pb-6\">{data.events.length > 0 ? data.events.map((event) => <div key={event.id} className="flex gap-3 rounded-xl bg-paper p-3"><div className="mt-0.5 rounded-lg bg-coral/10 p-2 text-coral"><EventIcon type={event.eventType ?? "OTHER"} /></div><div><p className="text-sm font-semibold text-ink">{event.title ?? "未命名事件"}</p><p className="mt-1 text-xs leading-5 text-ink-soft">{event.description ?? "暂无详细描述"}</p>{event.placeName && <p className="mt-2 font-mono text-[10px] text-coral">{event.placeName}{event.severity ? ` · ${event.severity}` : ""}</p>}</div></div>) : <p className="py-6 text-sm text-ink-soft">当前没有活跃事件。</p>}<Link to="/impacts" className="flex items-center justify-between pt-2 text-sm font-semibold text-sky">打开影响分析<ChevronRight size={16} /></Link></div>}</Card></div><div className="mt-9"><div className="mb-4 flex items-center justify-between"><div><p className="eyebrow">YOUR ROUTES</p><h2 className="mt-2 font-display text-2xl font-bold text-ink">最近的行程</h2></div><Link to="/trips" className="text-sm font-semibold text-sky">查看全部</Link></div><div className="grid gap-5 md:grid-cols-2">{data.trips.map((trip) => <BoardingPassCard key={trip.id} trip={trip} onClick={() => navigate(`/trips/${trip.id}`)} />)}</div></div><div className="mt-9"><TravelAssistant /></div><div className="mt-9 grid gap-5 lg:grid-cols-[1fr_1.1fr]"><Card className="p-6"><div className="flex items-center justify-between"><div><p className="eyebrow">ACTIVITY</p><h2 className="mt-2 font-display text-xl font-bold">团队动态</h2></div><Bell size={18} className="text-ink-soft" /></div><div className="mt-6">{data.notifications.length > 0 ? <div className="space-y-4">{data.notifications.map((item) => <div key={item.id} className="flex gap-3"><div className={`mt-1 h-2 w-2 rounded-full ${item.tone === "coral" ? "bg-coral" : item.tone === "mint" ? "bg-mint" : "bg-sky"}`} /><div><p className="text-sm font-semibold text-ink">{item.title}</p><p className="mt-1 text-xs text-ink-soft">{item.detail} · {item.time}</p></div></div>)}</div> : <p className="py-4 text-sm text-ink-soft">暂无团队动态。</p>}</div></Card><Card className="flex items-center justify-between gap-5 overflow-hidden bg-coral p-6 text-white"><div><p className="font-mono text-[10px] tracking-[0.18em] text-white/60">MAKE IT YOURS</p><h2 className="mt-3 font-display text-2xl font-bold">想换一条路线？</h2><p className="mt-2 max-w-sm text-sm leading-6 text-white/75">从旅伴攻略里套用一个灵感，再按你们的约束重新安排。</p><Button variant="secondary" className="mt-5 flex items-center gap-2" onClick={() => navigate("/guides")}>逛攻略社区<ArrowUpRight size={16} /></Button></div><CloudRain size={100} className="-mr-8 hidden rotate-12 text-white/15 sm:block" /></Card></div></>;
}
