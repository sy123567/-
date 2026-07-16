import { Clock3, MapPin, Ticket, Utensils, X } from "lucide-react";
import { useEffect, useRef } from "react";
import type { ReactNode } from "react";
import type { ItineraryNode, PlaceDetail } from "../types";

export function PlaceDetailSheet({ detail, node, onClose }: { detail: PlaceDetail; node?: ItineraryNode; onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-ink/30 backdrop-blur-sm" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <aside className="h-full w-full max-w-lg overflow-y-auto bg-paper p-5 shadow-2xl motion-safe:animate-[slide-in-right_.25s_ease-out] sm:p-7" role="dialog" aria-modal="true" aria-labelledby="place-sheet-title">
        <div className="relative overflow-hidden rounded-card bg-ink p-6 text-white">
          <div className="absolute -right-12 -top-12 h-32 w-32 rounded-full border-[18px] border-coral/20" />
          <div className="relative flex items-start justify-between gap-4">
            <div>
              <p className="font-mono text-[10px] tracking-[0.2em] text-coral">PLACE DETAIL / {detail.code ?? "ROUTE"}</p>
              <h2 id="place-sheet-title" className="mt-3 font-display text-2xl font-bold">{detail.placeName}</h2>
              <div className="mt-3 flex flex-wrap items-center gap-2"><span className="inline-flex items-center rounded-full bg-coral/10 px-2.5 py-1 text-xs font-semibold text-coral">{detail.category}</span>{node && <span className="font-mono text-xs text-white/55">{formatTime(node.plannedStart)} · ¥{node.cost}</span>}</div>
            </div>
            <button ref={closeRef} type="button" onClick={onClose} aria-label="关闭地点详情" className="rounded-lg p-2 text-white/60 transition hover:bg-white/10 hover:text-white focus-visible:outline-offset-2"><X size={20} /></button>
          </div>
        </div>
        <div className="space-y-6 py-6">
          <p className="text-sm leading-7 text-ink-soft">{detail.intro}</p>
          <section><h3 className="flex items-center gap-2 font-display text-lg font-bold"><MapPin size={18} className="text-coral" />必看地点</h3><ul className="mt-3 grid gap-2 sm:grid-cols-2">{detail.highlights.map((item) => <li key={item} className="rounded-xl bg-white px-4 py-3 text-sm text-ink shadow-sm">{item}</li>)}</ul></section>
          <section><h3 className="flex items-center gap-2 font-display text-lg font-bold"><Utensils size={18} className="text-mint" />本地美食</h3><div className="mt-3 space-y-3">{detail.foods.map((food) => <div key={food.name} className="rounded-xl border border-slate-100 bg-white p-4"><p className="text-sm font-semibold">{food.name}</p><p className="mt-1 text-xs leading-5 text-ink-soft">{food.desc}</p></div>)}</div></section>
          <section className="grid gap-3 sm:grid-cols-2">{detail.bestTime && <Info icon={<Clock3 size={16} />} label="最佳时段" value={detail.bestTime} />}{detail.ticket && <Info icon={<Ticket size={16} />} label="门票 / 费用" value={detail.ticket} />}</section>
          {detail.tips && <div className="rounded-xl bg-sun/15 p-4 text-sm leading-6 text-amber-900/80"><strong>出发提示：</strong>{detail.tips}</div>}
        </div>
      </aside>
    </div>
  );
}

function Info({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return <div className="rounded-xl bg-white p-4 shadow-sm"><p className="flex items-center gap-2 text-xs font-semibold text-ink-soft">{icon}{label}</p><p className="mt-2 text-sm leading-5 text-ink">{value}</p></div>;
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}
