import { ChevronDown, MapPinned } from "lucide-react";
import type { TripScope } from "../hooks/useTripScope";

export function TripSwitcher({ scope }: { scope: TripScope }) {
  if (scope.isLoading) return null;
  if (scope.isEmpty) {
    return (
      <div className="mb-6 flex items-center gap-3 rounded-card border border-dashed border-slate-200 bg-paper p-4 text-sm text-ink-soft">
        <MapPinned size={17} className="text-sky" />
        还没有可切换的行程，先创建或加入一段旅程。
      </div>
    );
  }
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-card border border-slate-100 bg-white p-4 shadow-soft">
      <div className="flex items-center gap-3">
        <span className="grid h-9 w-9 place-items-center rounded-xl bg-coral/10 text-coral">
          <MapPinned size={17} />
        </span>
        <div>
          <p className="font-mono text-[10px] font-semibold tracking-[0.16em] text-ink-soft">CURRENT TRIP</p>
          <p className="mt-1 font-display text-base font-bold text-ink">{scope.selectedTrip?.title ?? "选择行程"}</p>
        </div>
      </div>
      <label className="relative flex min-w-52 items-center">
        <span className="sr-only">切换当前行程</span>
        <select
          aria-label="切换当前行程"
          value={scope.tripId ?? ""}
          onChange={(event) => scope.setTripId(Number(event.target.value))}
          className="w-full appearance-none rounded-xl border border-slate-200 bg-paper px-4 py-3 pr-10 text-sm font-semibold text-ink outline-none transition hover:border-sky focus:border-sky focus:ring-2 focus:ring-sky/20"
        >
          {scope.trips.map((trip) => (
            <option key={trip.id} value={trip.id}>{trip.title} · {trip.startDate}</option>
          ))}
        </select>
        <ChevronDown size={16} className="pointer-events-none absolute right-3 text-ink-soft" />
      </label>
    </div>
  );
}
