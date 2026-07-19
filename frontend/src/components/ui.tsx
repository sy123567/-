import { useState } from "react";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";
import { Check, ChevronRight, CircleAlert, CloudRain, Info, MapPin, Sparkles } from "lucide-react";
import type { ItineraryNode, NodeStatus } from "../types";
import { getPlaceDetail } from "../mocks/places";
import { PlaceDetailSheet } from "./PlaceDetailSheet";

export function Button({ children, variant = "primary", className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ghost" }) {
  const styles = {
    primary: "bg-coral text-white shadow-coral hover:bg-coral-deep",
    secondary: "bg-ink text-white hover:bg-ink/90",
    ghost: "bg-transparent text-ink-soft hover:bg-ink/5 hover:text-ink",
  };
  return <button className={`rounded-xl px-4 py-2.5 text-sm font-semibold transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none ${styles[variant]} ${className}`} {...props}>{children}</button>;
}

export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "coral" | "mint" | "sky" | "sun" | "risk" }) {
  const styles = { neutral: "bg-slate-100 text-ink-soft", coral: "bg-coral/10 text-coral-deep", mint: "bg-mint/10 text-emerald-700", sky: "bg-sky/10 text-blue-700", sun: "bg-sun/20 text-amber-700", risk: "bg-risk-high/10 text-orange-700" };
  return <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${styles[tone]}`}>{children}</span>;
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`card ${className}`}>{children}</div>;
}

export function PageHeader({ eyebrow, title, description, action }: { eyebrow: string; title: string; description?: string; action?: ReactNode }) {
  return <div className="mb-7 flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow mb-2">{eyebrow}</p><h1 className="font-display text-3xl font-bold tracking-tight text-ink md:text-4xl">{title}</h1>{description && <p className="mt-2 max-w-2xl text-sm leading-6 text-ink-soft">{description}</p>}</div>{action}</div>;
}

export function StatCard({ label, value, detail, icon: Icon, tone = "coral" }: { label: string; value: string; detail: string; icon: typeof Sparkles; tone?: "coral" | "mint" | "sky" }) {
  const iconStyle = { coral: "bg-coral/10 text-coral", mint: "bg-mint/10 text-mint", sky: "bg-sky/10 text-sky" };
  return <Card className="p-5"><div className="flex items-start justify-between"><div><p className="text-xs font-medium text-ink-soft">{label}</p><p className="mt-2 font-display text-3xl font-bold text-ink">{value}</p><p className="mt-1 text-xs text-ink-soft">{detail}</p></div><div className={`rounded-xl p-2.5 ${iconStyle[tone]}`}><Icon size={18} /></div></div></Card>;
}

export function RiskGauge({ score, label }: { score: number; label: string }) {
  const circumference = 2 * Math.PI * 47;
  return <div className="relative grid h-36 w-36 place-items-center"><svg className="-rotate-90" width="136" height="136" viewBox="0 0 136 136"><circle cx="68" cy="68" r="47" fill="none" stroke="#edf0f5" strokeWidth="12" /><circle className="transition-all duration-1000 ease-out motion-reduce:transition-none" cx="68" cy="68" r="47" fill="none" stroke={score >= 60 ? "#FF922B" : "#17C3A2"} strokeWidth="12" strokeLinecap="round" strokeDasharray={circumference} strokeDashoffset={circumference * (1 - score / 100)} /></svg><div className="absolute text-center"><p className="font-display text-3xl font-bold text-ink">{score}</p><p className="text-[11px] font-medium text-ink-soft">{label}</p></div></div>;
}

const nodeIcon = { ATTRACTION: MapPin, MEAL: Sparkles, LODGING: Check, TRANSPORT: ChevronRight, OTHER: Sparkles };
const nodeNames = { ATTRACTION: "景点", MEAL: "餐饮", LODGING: "住宿", TRANSPORT: "交通", OTHER: "其他" };
const statusStyle: Record<NodeStatus, string> = { PLANNED: "bg-sky/10 text-blue-700", CONFIRMED: "bg-mint/10 text-emerald-700", AFFECTED: "bg-coral/10 text-coral-deep", CANCELLED: "bg-slate-100 text-ink-soft", REPLACED: "bg-sun/20 text-amber-700" };

export function StatusBadge({ status }: { status: NodeStatus }) {
  const labels = { PLANNED: "已规划", CONFIRMED: "已确认", AFFECTED: "受影响", CANCELLED: "已取消", REPLACED: "已替换" };
  return <span className={`rounded-full px-2 py-1 text-[11px] font-semibold ${statusStyle[status]}`}>{labels[status]}</span>;
}

export function RouteTrail({ nodes, compact = false }: { nodes: ItineraryNode[]; compact?: boolean }) {
  const [selectedNode, setSelectedNode] = useState<ItineraryNode | null>(null);
  return <>{<div className={`relative ${compact ? "space-y-4" : "space-y-7"}`}><div className="absolute bottom-6 left-[17px] top-6 border-l-2 border-dashed border-coral/30" />{nodes.map((node) => { const Icon = nodeIcon[node.nodeType]; return <div key={node.id} className="relative flex gap-4"><div className={`z-10 grid h-9 w-9 shrink-0 place-items-center rounded-full border-4 border-paper ${node.status === "AFFECTED" ? "bg-coral text-white" : "bg-mint text-white"}`}><Icon size={14} /></div><div className={`min-w-0 flex-1 ${compact ? "pb-1" : "pb-1"}`}><div className="flex flex-wrap items-center justify-between gap-2"><p className="font-mono text-xs font-bold text-ink-soft">{formatTime(node.plannedStart)} — {formatTime(node.plannedEnd)}</p><StatusBadge status={node.status} /></div><button type="button" onClick={() => setSelectedNode(node)} className="group mt-1 flex max-w-full items-center gap-2 rounded-lg text-left font-semibold text-ink transition hover:-translate-y-0.5 hover:text-coral focus-visible:outline-offset-2"><span className="truncate">{node.name}</span><Info size={14} className="shrink-0 text-sky opacity-60 transition group-hover:opacity-100" /></button><p className="mt-1 text-xs text-ink-soft">{node.placeName} · {nodeNames[node.nodeType]} · ¥{node.cost}</p>{node.status === "AFFECTED" && <div className="mt-3 flex items-start gap-2 rounded-xl bg-coral/5 px-3 py-2 text-xs leading-5 text-coral-deep"><CircleAlert size={14} className="mt-0.5 shrink-0" />阵雨可能影响此节点，已有 3 位成员关注</div>}</div></div>; })}</div>}{selectedNode && <PlaceDetailSheet detail={getPlaceDetail(selectedNode.placeName, selectedNode.nodeType)} node={selectedNode} onClose={() => setSelectedNode(null)} />}</>;
}

function formatTime(value: string) { return new Date(value).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }); }

export function BoardingPassCard({ trip, onClick }: { trip: import("../types").Trip; onClick?: () => void }) {
  const startDate = trip.startDate?.slice(5) ?? "待定";
  const endDate = trip.endDate?.slice(5) ?? "待定";
  return <button onClick={onClick} className="group relative w-full overflow-hidden rounded-card bg-ink text-left text-white shadow-soft transition hover:-translate-y-1 hover:shadow-xl focus-visible:outline-offset-4"><div className="absolute -right-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="absolute -left-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" /><div className="border-b border-dashed border-white/20 p-5 pr-10"><div className="flex items-start justify-between gap-4"><div><p className="font-mono text-[10px] tracking-[0.2em] text-white/50">TRIP BOARDING PASS</p><p className="mt-2 font-display text-xl font-bold">{trip.title ?? "未命名行程"}</p>{trip.destination && <p className="mt-1 text-sm text-white/60">{trip.destination}</p>}</div>{trip.roomCode && <div className="text-right"><p className="font-mono text-xs text-coral">{trip.roomCode}</p><p className="mt-1 text-[10px] text-white/40">ROOM CODE</p></div>}</div></div><div className="flex items-center justify-between p-5 pr-10"><div><p className="font-mono text-lg font-bold">{startDate} <span className="text-white/30">→</span> {endDate}</p>{trip.group && <p className="mt-1 text-xs text-white/50">{trip.group.name} · {trip.group.memberCount ?? trip.group.members?.length ?? 0} 位成员</p>}</div><ChevronRight className="text-coral transition group-hover:translate-x-1" /></div></button>;
}

export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-ink placeholder:text-slate-400 focus:border-sky focus:outline-none ${className}`} {...props} />;
}

export function EventIcon({ type }: { type: string }) { return type === "WEATHER" ? <CloudRain size={18} /> : <CircleAlert size={18} />; }
