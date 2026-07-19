import { ArrowRight, Check, Mail, MapPin, ShieldCheck, Sparkles } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { Button, Input } from "../components/ui";
import { signIn } from "../auth";

function RouteArtwork() {
  return (
    <svg className="pointer-events-none absolute inset-x-0 bottom-24 h-64 w-full opacity-80" viewBox="0 0 760 270" fill="none" aria-hidden="true">
      <path d="M-40 218C70 218 82 92 190 107s96 104 191 79c77-20 78-124 179-121 72 2 92 61 240 11" stroke="#FF5B4C" strokeWidth="2" strokeDasharray="8 10" className="animate-route" />
      <path d="M-12 218C92 218 103 128 195 139c89 11 92 92 180 71 82-19 98-112 178-101 79 11 93 46 214 0" stroke="#17C3A2" strokeWidth="1.5" strokeDasharray="3 12" opacity=".65" />
      <circle cx="190" cy="107" r="6" fill="#FF5B4C" />
      <circle cx="381" cy="186" r="6" fill="#17C3A2" />
      <circle cx="560" cy="65" r="6" fill="#FFC53D" />
    </svg>
  );
}

export function AuthPage({ register = false }: { register?: boolean }) {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    if (register && name.trim().length < 2) {
      setError("昵称至少需要 2 个字符，方便旅伴认出你。");
      return;
    }
    if (!email.includes("@")) {
      setError("请输入可用的邮箱地址。");
      return;
    }
    if (password.length < 6) {
      setError("密码至少需要 6 个字符。");
      return;
    }
    setSubmitting(true);
    try {
      const response = register
        ? await api.register(name.trim(), email.trim(), password)
        : await api.login(email.trim(), password);
      signIn(response.token, {
        id: response.userId,
        name: response.name,
        email: response.email,
        phone: response.phone,
      });
      navigate("/");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "登录失败，请稍后重试。");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="grid min-h-screen bg-paper lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden bg-ink p-8 text-white lg:flex lg:flex-col lg:justify-between xl:p-12">
        <div className="absolute -right-28 -top-24 h-96 w-96 rounded-full border-[48px] border-coral/20" />
        <div className="absolute bottom-20 left-20 h-40 w-40 rounded-full border border-mint/30" />
        <RouteArtwork />
        <div className="relative">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-coral font-display text-xl font-bold">游</div>
            <div>
              <p className="font-display text-xl font-bold">智能旅游平台</p>
              <p className="font-mono text-[9px] tracking-[0.2em] text-white/40">SMART TRAVEL</p>
            </div>
          </div>
          <div className="mt-20 max-w-lg xl:mt-24">
            <p className="eyebrow text-coral">TRAVEL WITH A PLAN B</p>
            <h1 className="mt-5 font-display text-5xl font-bold leading-[1.05] tracking-tight xl:text-6xl">
              让每段旅程，<span className="text-coral">都有下一站。</span>
            </h1>
            <p className="mt-7 max-w-md text-lg leading-8 text-white/55">
              一起规划，也一起应对变化。把突发天气、临时闭馆，变成团队共同决定的下一步。
            </p>
          </div>
        </div>
        <div className="relative flex flex-wrap items-center gap-5 text-sm text-white/45 xl:gap-8">
          <span className="flex items-center gap-2"><ShieldCheck size={16} className="text-mint" />多人协作</span>
          <span className="flex items-center gap-2"><Sparkles size={16} className="text-sun" />智能应变</span>
          <span className="flex items-center gap-2"><MapPin size={16} className="text-coral" />每步可追踪</span>
        </div>
      </section>

      <section className="flex items-center justify-center p-5 sm:p-8 md:p-12">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <div className="flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-coral font-display text-xl font-bold text-white">游</div>
              <div>
                <p className="font-display text-xl font-bold text-ink">智能旅游平台</p>
                <p className="font-mono text-[9px] tracking-[0.2em] text-ink-soft">SMART TRAVEL</p>
              </div>
            </div>
          </div>
          <div className="relative overflow-hidden rounded-card bg-ink p-5 text-white shadow-soft sm:p-6">
            <div className="absolute -right-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" />
            <div className="absolute -left-4 top-1/2 h-8 w-8 -translate-y-1/2 rounded-full bg-paper" />
            <p className="font-mono text-[10px] tracking-[0.22em] text-coral">BOARDING PASS / 2025</p>
            <div className="mt-8 flex items-end justify-between gap-4">
              <div>
                <p className="text-xs text-white/45">旅伴</p>
                <p className="mt-1 truncate font-display text-2xl font-bold">{name || "你的名字"}</p>
              </div>
              <div className="text-right">
                <p className="font-mono text-sm font-bold">{register ? "NEW / ROUTE" : "SH → NEXT"}</p>
                <p className="mt-1 text-[10px] text-white/40">ROUTE CODE</p>
              </div>
            </div>
            <div className="mt-7 border-t border-dashed border-white/20 pt-4 text-xs text-white/45">先登机，再决定下一站</div>
          </div>

          <div className="mt-9">
            <div className="flex items-center justify-between">
              <p className="eyebrow">{register ? "CREATE YOUR ROUTE" : "WELCOME BACK"}</p>
              <span className="font-mono text-[10px] text-ink-soft">{register ? "02 / 02" : "01 / 02"}</span>
            </div>
            <h2 className="mt-3 font-display text-3xl font-bold text-ink">{register ? "创建你的旅行身份" : "欢迎回来，继续出发"}</h2>
            <p className="mt-2 text-sm leading-6 text-ink-soft">
              {register ? "和旅伴一起，把想去的地方变成一条可应变的路线。" : "你的路线、成员和每个需要确认的变化，都在这里。"}
            </p>
            <form className="mt-7 space-y-4" onSubmit={submit} noValidate>
              {register && (
                <label className="block text-sm font-semibold text-ink">
                  怎么称呼你？
                  <Input className="mt-2" placeholder="例如：林小满" value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" />
                </label>
              )}
              <label className="block text-sm font-semibold text-ink">
                邮箱地址
                <div className="relative mt-2">
                  <Mail size={16} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-ink-soft" />
                  <Input className="pl-11" type="email" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" />
                </div>
              </label>
              <label className="block text-sm font-semibold text-ink">
                密码
                <Input className="mt-2" type="password" placeholder="至少 6 个字符" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={register ? "new-password" : "current-password"} />
              </label>
              {error && <p className="rounded-xl border border-coral/20 bg-coral/5 px-4 py-3 text-sm leading-5 text-coral-deep" role="alert">{error}</p>}
              <Button type="submit" disabled={submitting} className="mt-2 flex w-full items-center justify-center gap-2">
                {submitting ? "正在确认登机信息…" : register ? "开始规划" : "进入我的行程"}
                {!submitting && <ArrowRight size={17} />}
              </Button>
            </form>
            {!register && <div className="mt-4 rounded-xl border border-sky/15 bg-sky/5 p-3 text-xs text-ink-soft"><p>演示账号：<span className="font-mono text-ink">zhangsan@example.com</span></p><p className="mt-1">密码：<span className="font-mono text-ink">password123</span></p><button type="button" className="mt-2 font-semibold text-sky hover:text-ink" onClick={() => { setEmail("zhangsan@example.com"); setPassword("password123"); }}>一键填入演示账号</button></div>}
            <div className="mt-6 flex items-center justify-center gap-2 text-sm text-ink-soft"><Check size={15} className="text-mint" />数据仅用于你的旅行协作</div>
            <p className="mt-8 text-center text-sm text-ink-soft">
              {register ? "已经有账号？" : "还没有账号？"}{" "}
              <Link className="font-semibold text-coral hover:text-coral-deep" to={register ? "/login" : "/register"}>{register ? "直接登录" : "创建账号"}</Link>
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
