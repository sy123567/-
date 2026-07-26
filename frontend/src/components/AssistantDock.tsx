import { useEffect, useRef, useState } from "react";
import { ChevronRight, Compass, MapPin, Send, Sparkles, X } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import { api } from "../api/client";
import type { AssistantAnswer } from "../types";

const PROMPTS = ["周末想去海边，有推荐吗？", "杭州三天怎么安排比较松弛？", "在哪里给成员填写时间和预算约束？", "行程被暴雨影响了要怎么改？"];

const SOURCE_LABEL: Record<string, string> = { ai: "AI 生成", local: "来自攻略与站内目录", offline: "离线提示" };

/**
 * 全局旅行助手：右下角常驻的悬浮入口，任意页面都能唤起。
 * 回答优先来自 AI，AI 不可用时退回攻略社区检索与站内目录导航。
 */
export function AssistantDock() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AssistantAnswer | null>(null);
  const [errorText, setErrorText] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const ask = useMutation({
    mutationFn: (value: string) => api.assistant(value),
    onSuccess: (result) => { setAnswer(result); setErrorText(""); },
    onError: (cause) => { setAnswer(null); setErrorText(cause instanceof Error ? cause.message : "助手暂时没能回答，请稍后再试"); },
  });
  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  const submit = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed || ask.isPending) return;
    setQuestion(trimmed);
    ask.mutate(trimmed);
  };
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-label={open ? "收起旅行助手" : "打开旅行助手"}
        className="boarding-float fixed bottom-6 right-6 z-40 grid h-14 w-14 place-items-center rounded-full bg-ink text-coral shadow-xl ring-4 ring-white/70 transition hover:scale-105 hover:text-white"
      >
        {open ? <X size={22} /> : <Sparkles size={22} />}
      </button>
      {open && (
        <div className="fixed inset-x-4 bottom-24 z-40 max-h-[72vh] overflow-y-auto rounded-card border border-slate-100 bg-surface p-5 shadow-2xl sm:inset-x-auto sm:right-6 sm:w-[26rem]">
          <div className="flex items-start gap-3">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-ink text-coral"><Sparkles size={18} /></span>
            <div>
              <p className="eyebrow">TRAVEL COPILOT</p>
              <h2 className="mt-1 font-display text-lg font-bold text-ink">问问旅行助手</h2>
              <p className="mt-1 text-xs leading-5 text-ink-soft">想去哪、怎么安排、功能在哪一页，都可以直接问。</p>
            </div>
          </div>
          <form className="mt-4 flex gap-2" onSubmit={(event) => { event.preventDefault(); submit(question); }}>
            <input
              ref={inputRef}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="例如：五一三天两晚，带老人去哪合适？"
              maxLength={500}
              aria-label="向旅行助手提问"
              className="w-full rounded-xl border border-slate-200 bg-paper px-3 py-2.5 text-sm text-ink outline-none transition focus:border-sky"
            />
            <button type="submit" disabled={!question.trim() || ask.isPending} className="flex shrink-0 items-center gap-1.5 rounded-xl bg-coral px-3 text-sm font-semibold text-white transition hover:bg-coral-deep disabled:opacity-50">
              <Send size={14} />{ask.isPending ? "思考中" : "提问"}
            </button>
          </form>
          <div className="mt-3 flex flex-wrap gap-2">
            {PROMPTS.map((prompt) => (
              <button key={prompt} type="button" onClick={() => submit(prompt)} disabled={ask.isPending} className="rounded-full bg-paper px-3 py-1.5 text-[11px] font-semibold text-ink-soft transition hover:bg-sky/10 hover:text-sky disabled:opacity-50">
                {prompt}
              </button>
            ))}
          </div>
          {errorText && <p className="mt-4 rounded-xl bg-coral/5 px-4 py-3 text-sm text-coral-deep" role="alert">{errorText}</p>}
          {ask.isPending && <p className="mt-4 text-sm text-ink-soft">正在为你查找攻略与建议…</p>}
          {answer && !ask.isPending && (
            <div className="mt-4 space-y-4">
              <div className="rounded-card bg-paper p-4">
                <span className="inline-flex items-center gap-1 rounded-full bg-white px-2 py-1 text-[10px] font-semibold text-ink-soft">
                  <Compass size={11} />{SOURCE_LABEL[answer.source] ?? answer.source}
                </span>
                <p className="mt-3 whitespace-pre-wrap text-sm leading-7 text-ink">{answer.answer}</p>
              </div>
              {answer.guides.length > 0 && (
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">攻略社区里的相关内容</p>
                  <div className="mt-2 space-y-2">
                    {answer.guides.map((guide) => (
                      <button key={guide.id} type="button" onClick={() => { setOpen(false); navigate(`/guides/${guide.id}`); }} className="w-full rounded-xl bg-paper p-3 text-left transition hover:bg-sky/5">
                        <p className="text-sm font-semibold text-ink">{guide.title}</p>
                        <p className="mt-1 flex items-center gap-1 text-[11px] text-ink-soft"><MapPin size={11} />{guide.city || "不限城市"}{guide.theme ? ` · ${guide.theme}` : ""}</p>
                      </button>
                    ))}
                  </div>
                </div>
              )}
              {answer.links.length > 0 && (
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">可以直接去这些页面</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {answer.links.map((link) => (
                      <Link key={link.path} to={link.path} onClick={() => setOpen(false)} className="flex items-center gap-1 rounded-full bg-sky/10 px-3 py-2 text-[11px] font-semibold text-sky transition hover:bg-sky/20">
                        {link.label}<ChevronRight size={12} />
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </>
  );
}
