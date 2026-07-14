import { useEffect, useState, type ReactNode } from "react";
import { Check, X } from "lucide-react";

export function Modal({ open, title, children, onClose }: { open: boolean; title: string; children: ReactNode; onClose: () => void }) {
  if (!open) return null;
  return <div className="fixed inset-0 z-[80] grid place-items-center bg-ink/40 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={title}><div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-card bg-white shadow-2xl"><div className="flex items-center justify-between border-b border-slate-100 px-6 py-5"><h2 className="font-display text-xl font-bold text-ink">{title}</h2><button onClick={onClose} className="rounded-lg p-2 text-ink-soft hover:bg-paper hover:text-ink" aria-label="关闭"><X size={18} /></button></div><div className="p-6">{children}</div></div></div>;
}

export function Toast({ message, tone = "success", onClose }: { message: string; tone?: "success" | "info"; onClose: () => void }) {
  useEffect(() => { const timer = window.setTimeout(onClose, 3200); return () => window.clearTimeout(timer); }, [onClose]);
  return <div className="fixed bottom-5 right-5 z-[90] flex max-w-sm items-center gap-3 rounded-xl bg-ink px-4 py-3 text-sm text-white shadow-xl"><span className={`grid h-7 w-7 place-items-center rounded-full ${tone === "success" ? "bg-mint" : "bg-sky"}`}><Check size={15} /></span>{message}</div>;
}

export function ImageFallback({ src, alt, city }: { src: string; alt: string; city: string }) {
  const [failed, setFailed] = useState(false);
  if (failed) return <div className="flex h-full w-full items-end bg-gradient-to-br from-coral via-sun to-mint p-5 text-white"><div><p className="font-mono text-[10px] tracking-[0.18em] text-white/70">TRAVEL GUIDE</p><p className="mt-1 font-display text-2xl font-bold">{city}</p></div></div>;
  return <img src={src} alt={alt} onError={() => setFailed(true)} className="h-full w-full object-cover" />;
}
