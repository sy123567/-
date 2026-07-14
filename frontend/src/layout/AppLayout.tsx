import { useState, type ReactNode } from "react";
import { Bell, Compass, LayoutDashboard, Menu, Search, Settings, Users, X } from "lucide-react";
import { Link, NavLink } from "react-router-dom";
import { user } from "../mocks/data";

const groups = [
  { label: "社交", items: [{ label: "我的小组", to: "/groups", icon: Users }, { label: "好友与邀请", to: "/friends", icon: Users }] },
  { label: "规划", items: [{ label: "行程总览", to: "/trips", icon: Compass }, { label: "地图路线", to: "/routes", icon: Compass }, { label: "预算与费用", to: "/budget", icon: Compass }, { label: "攻略社区", to: "/guides", icon: Compass }] },
  { label: "行程中", items: [{ label: "事件监测", to: "/events", icon: Bell }, { label: "影响与风险", to: "/impacts", icon: LayoutDashboard }] },
  { label: "决策", items: [{ label: "替代方案", to: "/plans", icon: Compass }, { label: "投票中心", to: "/votes", icon: Users }] },
  { label: "社区", items: [{ label: "讨论区", to: "/discussions", icon: Users }, { label: "通知", to: "/notifications", icon: Bell }] },
  { label: "我的", items: [{ label: "分账与结算", to: "/settlement", icon: WalletIcon }, { label: "个人设置", to: "/settings", icon: Settings }, { label: "数据后台", to: "/admin", icon: LayoutDashboard }] },
];

function WalletIcon() {
  return <span className="text-[16px]">¥</span>;
}

export function AppLayout({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return <div className="min-h-screen bg-paper"><Sidebar open={open} onClose={() => setOpen(false)} /><div className="lg:pl-[248px]"><TopBar onMenu={() => setOpen(true)} /><main className="mx-auto max-w-[1440px] px-5 py-7 md:px-8 lg:px-10">{children}</main></div></div>;
}

function Sidebar({ open, onClose }: { open: boolean; onClose: () => void }) {
  return <><div className={`fixed inset-0 z-40 bg-ink/30 backdrop-blur-sm transition lg:hidden ${open ? "opacity-100" : "pointer-events-none opacity-0"}`} onClick={onClose} /><aside className={`fixed inset-y-0 left-0 z-50 w-[248px] bg-ink px-4 py-6 text-white transition-transform lg:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}><div className="flex items-center justify-between px-3"><Link to="/" className="flex items-center gap-3" onClick={onClose}><div className="grid h-9 w-9 place-items-center rounded-xl bg-coral font-display font-bold">行</div><div><p className="font-display text-lg font-bold">行迹应变</p><p className="text-[10px] tracking-wider text-white/40">TRIP ADAPTIVE</p></div></Link><button className="text-white/50 lg:hidden" onClick={onClose} aria-label="关闭导航"><X size={20} /></button></div><nav className="mt-9 space-y-6">{groups.map((group) => <div key={group.label}><p className="px-3 text-[10px] font-bold tracking-[0.18em] text-white/35">{group.label}</p><div className="mt-2 space-y-1">{group.items.map(({ label, to, icon: Icon }) => <NavLink key={to} to={to} onClick={onClose} className={({ isActive }) => `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition ${isActive ? "bg-white/10 font-semibold text-white" : "text-white/55 hover:bg-white/5 hover:text-white"}`}><Icon size={17} />{label}</NavLink>)}</div></div>)}</nav><div className="absolute bottom-5 left-4 right-4 rounded-2xl border border-white/10 bg-white/5 p-4"><p className="font-mono text-[10px] text-coral">NEXT STOP</p><p className="mt-2 text-sm font-semibold">去看看今天的路线</p><p className="mt-1 text-xs leading-5 text-white/45">每一次变化，都能找到下一站。</p></div></aside></>;
}

function TopBar({ onMenu }: { onMenu: () => void }) {
  return <header className="sticky top-0 z-30 border-b border-slate-200/70 bg-paper/85 px-5 py-4 backdrop-blur md:px-8 lg:px-10"><div className="flex items-center justify-between gap-4"><button className="text-ink lg:hidden" onClick={onMenu} aria-label="打开导航"><Menu /></button><div className="relative hidden max-w-md flex-1 md:block"><Search size={17} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-soft" /><input className="w-full rounded-xl border-0 bg-white py-2.5 pl-10 pr-4 text-sm shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-sky/30" placeholder="搜索行程、攻略或城市" /></div><div className="ml-auto flex items-center gap-2"><button className="relative grid h-10 w-10 place-items-center rounded-xl text-ink-soft transition hover:bg-white hover:text-ink" aria-label="通知"><Bell size={19} /><span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-coral" /></button><div className="ml-2 flex items-center gap-2 border-l border-slate-200 pl-4"><img src={user.avatar} alt="" className="h-9 w-9 rounded-full object-cover ring-2 ring-white" /><div className="hidden sm:block"><p className="text-sm font-semibold text-ink">{user.name}</p><p className="text-[11px] text-ink-soft">周末旅行家</p></div></div></div></div></header>;
}
