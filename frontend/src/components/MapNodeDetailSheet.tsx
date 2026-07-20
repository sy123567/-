import { useEffect, useMemo, useState } from "react";
import { Clock3, MapPin, Phone, Route, Star, X } from "lucide-react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { api, type MapPlace, type MapRoute } from "../api/client";
import type { ItineraryNode, Trip } from "../types";
import { getPlaceImage } from "../mocks/places";
import { Badge, Button } from "./ui";
import { ImageFallback } from "./pass2";
import { BaiduMap } from "./BaiduMap";

export function MapNodeDetailSheet({
  node,
  trip,
  onClose,
}: {
  node: ItineraryNode;
  trip: Trip;
  onClose: () => void;
}) {
  const [selectedUid, setSelectedUid] = useState("");
  const resolveQuery = useQuery({
    queryKey: ["map-resolve", node.id, node.placeName || node.name, node.latitude, node.longitude],
    queryFn: () => api.mapResolve(node.placeName || node.name, node.latitude, node.longitude),
    enabled: hasCoordinates(node) && Boolean(node.placeName || node.name),
  });
  const resolvedNode = useMemo(() => {
    const resolved = resolveQuery.data;
    if (!resolved?.available || resolved.lat === undefined || resolved.lng === undefined) return node;
    return { ...node, latitude: resolved.lat, longitude: resolved.lng };
  }, [node, resolveQuery.data]);
  const resolvedCoordinatesReady = !resolveQuery.isLoading && hasCoordinates(resolvedNode);
  const mapNodes = useMemo(() => [resolvedNode], [resolvedNode]);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  const sortedNodes = useMemo(
    () => [...(trip.itineraryNodes ?? [])].sort((left, right) => left.sequenceOrder - right.sequenceOrder),
    [trip.itineraryNodes],
  );
  const nodeIndex = sortedNodes.findIndex((item) => item.id === node.id);
  const adjacentNodes = [sortedNodes[nodeIndex - 1], sortedNodes[nodeIndex + 1]].filter(
    (item): item is ItineraryNode => Boolean(item),
  );
  const weatherQuery = useQuery({
    queryKey: ["weather-preview", resolvedNode.id, resolvedNode.latitude, resolvedNode.longitude],
    queryFn: () => api.previewWeather(resolvedNode.latitude, resolvedNode.longitude),
    enabled: resolvedCoordinatesReady,
  });
  const routeQueries = useQueries({
    queries: adjacentNodes.map((adjacent) => ({
      queryKey: ["map-route", node.id, adjacent.id],
      queryFn: () =>
        api.mapRoute(
          resolvedNode.latitude,
          resolvedNode.longitude,
          adjacent.latitude,
          adjacent.longitude,
          "driving",
        ),
      enabled: resolvedCoordinatesReady && hasCoordinates(adjacent),
    })),
  });
  const recommendationQueries = useQueries({
    queries: ["美食", "景点", "休闲娱乐"].map((query) => ({
      queryKey: ["map-nearby", query, node.id],
      queryFn: () => api.mapNearby(query, resolvedNode.latitude, resolvedNode.longitude, 3000),
      enabled: resolvedCoordinatesReady,
    })),
  });
  const detailQuery = useQuery({
    queryKey: ["map-place", selectedUid],
    queryFn: () => api.mapPlace(selectedUid),
    enabled: Boolean(selectedUid),
  });
  const recommendations = useMemo(() => {
    const unique = new Map<string, MapPlace>();
    recommendationQueries
      .flatMap((query) => query.data?.places ?? [])
      .forEach((place) => {
        const key = place.uid ?? `${place.name ?? ""}:${place.lat ?? ""}:${place.lng ?? ""}`;
        if (!unique.has(key)) unique.set(key, place);
      });
    return [...unique.values()]
      .sort(
        (left, right) =>
          (left.distanceMeters ?? Number.POSITIVE_INFINITY) -
          (right.distanceMeters ?? Number.POSITIVE_INFINITY),
      )
      .slice(0, 8);
  }, [recommendationQueries]);
  const selectedPlace = useMemo(
    () => recommendations.find((place) => place.uid === selectedUid),
    [recommendations, selectedUid],
  );
  const recommendationRouteQuery = useQuery({
    queryKey: [
      "map-route-recommendation",
      node.id,
      selectedUid,
      selectedPlace?.lat,
      selectedPlace?.lng,
    ],
    queryFn: () =>
      api.mapRoute(
        resolvedNode.latitude,
        resolvedNode.longitude,
        selectedPlace?.lat ?? 0,
        selectedPlace?.lng ?? 0,
        "walking",
      ),
    enabled:
      Boolean(selectedUid) &&
      resolvedCoordinatesReady &&
      selectedPlace !== undefined &&
      selectedPlace.lat !== undefined &&
      selectedPlace.lng !== undefined &&
      Number.isFinite(selectedPlace.lat) &&
      Number.isFinite(selectedPlace.lng),
  });
  const weather = weatherQuery.data;
  const detail = detailQuery.data?.place;

  return (
    <div
      className="fixed inset-0 z-50 flex justify-end bg-ink/30 backdrop-blur-sm"
      role="presentation"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <aside
        className="h-full w-full max-w-2xl overflow-y-auto bg-paper p-5 shadow-2xl motion-safe:animate-[slide-in-right_.25s_ease-out] sm:p-7"
        role="dialog"
        aria-modal="true"
        aria-labelledby="map-node-detail-title"
      >
        <div className="relative overflow-hidden rounded-card bg-ink p-6 text-white">
          <div className="absolute -right-12 -top-12 h-32 w-32 rounded-full border-[18px] border-coral/20" />
          <div className="relative flex items-start justify-between gap-4">
            <div>
              <p className="font-mono text-[10px] tracking-[0.2em] text-coral">MAP NODE / LIVE CONTEXT</p>
              <h2 id="map-node-detail-title" className="mt-3 font-display text-2xl font-bold">
                {node.placeName || node.name}
              </h2>
              <p className="mt-2 text-sm text-white/60">{node.name} · {node.sequenceOrder}号节点</p>
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="关闭地图节点详情"
              className="rounded-lg p-2 text-white/60 transition hover:bg-white/10 hover:text-white motion-reduce:transition-none"
            >
              <X size={20} />
            </button>
          </div>
        </div>

        <div className="space-y-5 py-6">
          <BaiduMap
            nodes={mapNodes}
            places={recommendations}
            selectedPlace={selectedPlace}
            routeMode="walking"
            onPlaceClick={(place) => place.uid && setSelectedUid(place.uid)}
            className="h-56"
          />

          <section>
            <SectionHeading icon={<MapPin size={17} />} title="天气上下文" tone="coral" />
            {weatherQuery.isLoading ? (
              <InfoPanel>正在查询该地点的天气…</InfoPanel>
            ) : weather?.available && weather.tempMin !== undefined && weather.tempMax !== undefined ? (
              <div className={`rounded-card p-4 ${weather.hasAlert || weather.hasPrecipitation ? "bg-sun/20 text-amber-900" : "bg-mint/10 text-emerald-900"}`}>
                <p className="font-mono text-lg font-bold">
                  {Math.round(weather.tempMin)}~{Math.round(weather.tempMax)}°C
                  {weather.phrase && <span className="ml-2 font-sans text-sm font-semibold">{weather.phrase}</span>}
                </p>
                {(weather.hasAlert || weather.hasPrecipitation) && (
                  <p className="mt-2 text-sm font-semibold">该地点近期有降水或预警，注意安排室内备选。</p>
                )}
              </div>
            ) : (
              <InfoPanel>{weather?.message ?? "天气服务暂不可用，可稍后再查"}</InfoPanel>
            )}
          </section>

          <section>
            <SectionHeading icon={<Route size={17} />} title="相邻节点交通" tone="sky" />
            {adjacentNodes.length === 0 ? (
              <InfoPanel>这是当前路线唯一节点，暂时没有相邻路段。</InfoPanel>
            ) : (
              <div className="space-y-3">
                {adjacentNodes.map((adjacent, index) => (
                  <RouteRow key={adjacent.id} node={adjacent} route={routeQueries[index]?.data} loading={routeQueries[index]?.isLoading} />
                ))}
              </div>
            )}
          </section>

          <section>
            <div className="flex items-end justify-between gap-3">
              <SectionHeading icon={<Star size={17} />} title="周边推荐" tone="mint" />
              <span className="text-xs text-ink-soft">附近 3km · 美食 / 景点 / 休闲</span>
            </div>
            {recommendationQueries.some((query) => query.isLoading) ? (
              <InfoPanel>正在寻找附近值得停留的地方…</InfoPanel>
            ) : recommendations.length === 0 ? (
              <InfoPanel>暂时没有推荐结果，可稍后再试。</InfoPanel>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2">
                {recommendations.map((place, index) => (
                  <RecommendationCard
                    key={`${place.uid ?? place.name}-${index}`}
                    place={place}
                    onClick={() => place.uid && setSelectedUid(place.uid)}
                  />
                ))}
              </div>
            )}
          </section>

          {selectedUid && (
            <section className="rounded-card border border-coral/15 bg-white p-4">
              {selectedPlace && (
                <RecommendationRoute
                  route={recommendationRouteQuery.data}
                  loading={recommendationRouteQuery.isLoading}
                />
              )}
              {detailQuery.isLoading ? (
                <InfoPanel>正在读取地点详情…</InfoPanel>
              ) : detail ? (
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="eyebrow">PLACE DETAIL</p>
                      <h3 className="mt-2 font-display text-xl font-bold text-ink">{detail.name ?? "未命名地点"}</h3>
                    </div>
                    <Button type="button" variant="ghost" onClick={() => setSelectedUid("")}>收起</Button>
                  </div>
                  <div className="mt-4 h-40 overflow-hidden rounded-2xl">
                    <ImageFallback
                      src={detail.image || getPlaceImage(detail.name ?? "推荐地点", "OTHER")}
                      alt={detail.name ?? "推荐地点"}
                      city={detail.name ?? "推荐地点"}
                    />
                  </div>
                  <div className="mt-4 flex flex-wrap gap-2">
                    {detail.overallRating !== undefined && <Badge tone="sun">评分 {detail.overallRating}</Badge>}
                    {detail.price !== undefined && <Badge tone="mint">人均 ¥{detail.price}</Badge>}
                    {detail.tag && <Badge tone="sky">{detail.tag}</Badge>}
                  </div>
                  <div className="mt-4 space-y-2 text-sm text-ink-soft">
                    {detail.address && <p className="flex gap-2"><MapPin size={16} className="mt-0.5 shrink-0 text-coral" />{detail.address}</p>}
                    {detail.telephone && <p className="flex gap-2"><Phone size={16} className="mt-0.5 shrink-0 text-mint" />{detail.telephone}</p>}
                  </div>
                </div>
              ) : (
                <InfoPanel>{detailQuery.data?.message ?? "该地点详情暂不可用。"}</InfoPanel>
              )}
            </section>
          )}
        </div>
      </aside>
    </div>
  );
}

function RouteRow({
  node,
  route,
  loading,
}: {
  node: ItineraryNode;
  route?: MapRoute;
  loading?: boolean;
}) {
  const distance = route?.distanceMeters;
  const duration = route?.durationSeconds;
  const convenience = distance !== undefined && duration !== undefined ? convenienceLabel(distance, duration) : "待查询";
  return (
    <div className="flex items-center justify-between gap-3 rounded-card border border-slate-100 bg-white p-4">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-ink">{node.placeName || node.name}</p>
        <p className="mt-1 flex items-center gap-1 text-xs text-ink-soft"><Clock3 size={13} />{loading ? "正在计算路程…" : distance !== undefined && duration !== undefined ? `${(distance / 1000).toFixed(1)} km · ${Math.ceil(duration / 60)} 分钟` : route?.message ?? "路线暂不可用"}</p>
      </div>
      <Badge tone={convenience === "便利" ? "mint" : convenience === "一般" ? "sun" : "neutral"}>{convenience}</Badge>
    </div>
  );
}

function RecommendationCard({ place, onClick }: { place: MapPlace; onClick: () => void }) {
  const clickable = Boolean(place.uid);
  return (
    <button
      type="button"
      disabled={!clickable}
      onClick={onClick}
      className="rounded-card border border-slate-100 bg-white p-4 text-left transition hover:-translate-y-0.5 hover:border-coral/30 hover:shadow-soft disabled:cursor-default disabled:opacity-70 motion-reduce:transition-none"
    >
      <div className="mb-3 h-24 overflow-hidden rounded-2xl">
        <ImageFallback
          src={place.image || getPlaceImage(place.name ?? "附近地点", "OTHER")}
          alt={place.name ?? "附近地点"}
          city={place.city || place.area || "附近地点"}
        />
      </div>
      <div className="flex items-start justify-between gap-3">
        <p className="line-clamp-2 font-semibold text-ink">{place.name ?? "附近地点"}</p>
        {place.uid && <Badge tone="coral">详情</Badge>}
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {place.distanceMeters !== undefined && (
          <Badge tone="sky">{formatDistance(place.distanceMeters)}</Badge>
        )}
        {place.overallRating !== undefined && <Badge tone="sun">评分 {place.overallRating}</Badge>}
        {place.price !== undefined && <Badge tone="mint">人均 ¥{place.price}</Badge>}
      </div>
      <p className="mt-2 line-clamp-2 text-xs leading-5 text-ink-soft">{place.address ?? place.area ?? "百度地图推荐地点"}</p>
    </button>
  );
}

function RecommendationRoute({ route, loading }: { route?: MapRoute; loading?: boolean }) {
  const distance = route?.distanceMeters;
  const duration = route?.durationSeconds;
  const convenience =
    distance !== undefined && duration !== undefined
      ? convenienceLabel(distance, duration)
      : "待查询";
  return (
    <div className="mt-4 flex items-center justify-between gap-3 rounded-2xl bg-paper px-3 py-3">
      <div className="min-w-0">
        <p className="text-xs font-semibold text-ink">从当前节点步行到这里</p>
        <p className="mt-1 flex items-center gap-1 text-xs text-ink-soft">
          <Clock3 size={13} />
          {loading
            ? "正在计算路程…"
            : distance !== undefined && duration !== undefined
              ? `${formatDistance(distance)} · ${Math.ceil(duration / 60)} 分钟`
              : route?.message ?? "路线暂不可用"}
        </p>
      </div>
      <Badge tone={convenience === "便利" ? "mint" : convenience === "一般" ? "sun" : "neutral"}>
        {convenience}
      </Badge>
    </div>
  );
}

function SectionHeading({ icon, title, tone }: { icon: React.ReactNode; title: string; tone: "coral" | "sky" | "mint" }) {
  return <h3 className={`flex items-center gap-2 font-display text-lg font-bold ${tone === "coral" ? "text-coral" : tone === "sky" ? "text-sky" : "text-mint"}`}>{icon}<span className="text-ink">{title}</span></h3>;
}

function InfoPanel({ children }: { children: React.ReactNode }) {
  return <div className="rounded-card bg-slate-100/70 px-4 py-3 text-sm text-ink-soft">{children}</div>;
}

function hasCoordinates(node: ItineraryNode) {
  return Number.isFinite(node.latitude) && Number.isFinite(node.longitude) && node.latitude !== 0 && node.longitude !== 0;
}

function convenienceLabel(distanceMeters: number, durationSeconds: number) {
  const distanceKm = distanceMeters / 1000;
  const durationMinutes = durationSeconds / 60;
  if (distanceKm <= 5 && durationMinutes <= 20) return "便利";
  if (distanceKm <= 15 && durationMinutes <= 45) return "一般";
  return "较远";
}

function formatDistance(distanceMeters: number) {
  return distanceMeters < 1000
    ? `${Math.round(distanceMeters)} m`
    : `${(distanceMeters / 1000).toFixed(1)} km`;
}
