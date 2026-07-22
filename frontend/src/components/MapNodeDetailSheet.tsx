import { useEffect, useMemo, useState } from "react";
import { Clock3, MapPin, MessageCircle, Phone, Plus, Route, Star, X } from "lucide-react";
import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, type HotelRecommendation, type MapPlace, type MapRoute } from "../api/client";
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
  const [addedPlaceKeys, setAddedPlaceKeys] = useState<string[]>([]);
  const [selectedHotelCategory, setSelectedHotelCategory] = useState("value");
  const [noteDraft, setNoteDraft] = useState("");
  const queryClient = useQueryClient();
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
    queries: ["美食", "景点"].map((query) => ({
      queryKey: ["map-nearby", query, node.id],
      queryFn: () => api.mapNearby(query, resolvedNode.latitude, resolvedNode.longitude, 3000),
      enabled: resolvedCoordinatesReady,
    })),
  });
  const hotelsQuery = useQuery({
    queryKey: ["map-hotels", node.id, resolvedNode.latitude, resolvedNode.longitude],
    queryFn: () => api.mapHotels(resolvedNode.latitude, resolvedNode.longitude, 2500),
    enabled: resolvedCoordinatesReady,
  });
  const detailQuery = useQuery({
    queryKey: ["map-place", selectedUid],
    queryFn: () => api.mapPlace(selectedUid),
    enabled: Boolean(selectedUid),
  });
  const notesQuery = useQuery({
    queryKey: ["node-notes", trip.id, node.id],
    queryFn: () => api.nodeNotes(trip.id, node.id),
  });
  const addNoteMutation = useMutation({
    mutationFn: (content: string) => api.addNodeNote(trip.id, node.id, content),
    onSuccess: () => {
      setNoteDraft("");
      void queryClient.invalidateQueries({ queryKey: ["node-notes", trip.id, node.id] });
    },
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
  const addRecommendation = (place: MapPlace, nodeType: ItineraryNode["nodeType"] = "ATTRACTION") => {
    const key = place.uid ?? `${place.name ?? "place"}:${place.lat ?? ""}:${place.lng ?? ""}`;
    if (!place.lat || !place.lng || addedPlaceKeys.includes(key)) return;
    void api.addNode(trip.id, {
      name: place.name ?? "附近推荐地点",
      placeName: place.name ?? "附近推荐地点",
      latitude: place.lat,
      longitude: place.lng,
      parentId: node.id,
      nodeType,
      plannedStart: node.plannedStart,
      plannedEnd: node.plannedEnd,
      cost: 0,
      sequenceOrder: node.sequenceOrder,
      status: "PLANNED",
    }).then(() => {
      setAddedPlaceKeys((current) => [...current, key]);
      void queryClient.invalidateQueries({ queryKey: ["trip", trip.id] });
    }).catch(() => undefined);
  };
  const addHotel = (hotel: HotelRecommendation) => {
    if (hotel.lat === undefined || hotel.lng === undefined) return;
    const key = hotel.uid ?? `${hotel.name}:${hotel.lat}:${hotel.lng}`;
    if (addedPlaceKeys.includes(key)) return;
    void api.addNode(trip.id, {
      name: hotel.name,
      placeName: hotel.name,
      latitude: hotel.lat,
      longitude: hotel.lng,
      parentId: node.id,
      nodeType: "LODGING",
      plannedStart: node.plannedStart,
      plannedEnd: node.plannedEnd,
      cost: hotel.price ?? 0,
      sequenceOrder: node.sequenceOrder,
      status: "PLANNED",
    }).then(() => {
      setAddedPlaceKeys((current) => [...current, key]);
      void queryClient.invalidateQueries({ queryKey: ["trip", trip.id] });
    }).catch(() => undefined);
  };
  const hotelCategories = hotelsQuery.data?.categories ?? [];
  const activeHotelCategory =
    hotelCategories.find((category) => category.key === selectedHotelCategory)
      ?? hotelCategories.find((category) => category.hotels.length > 0);
  const selectedHotels =
    activeHotelCategory?.hotels
      ?? [];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/30 p-3 backdrop-blur-sm sm:p-5"
      role="presentation"
      onMouseDown={(event) => event.target === event.currentTarget && onClose()}
    >
      <aside
        className="flex h-[92vh] w-[94vw] max-w-6xl flex-col overflow-hidden rounded-card bg-paper p-4 shadow-2xl motion-safe:animate-[slide-in-right_.25s_ease-out] motion-reduce:animate-none sm:p-6 lg:h-[90vh]"
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
            <div className="flex items-center gap-2">
              <Link to={`/trips/${trip.id}`} onClick={onClose} className="rounded-lg px-3 py-2 text-xs font-semibold text-white/70 transition hover:bg-white/10 hover:text-white">
                查看行程详情
              </Link>
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
        </div>

        <div className="grid min-h-0 flex-1 gap-5 overflow-y-auto py-5 lg:grid-cols-[minmax(0,1.08fr)_minmax(320px,0.92fr)] lg:overflow-hidden">
          <BaiduMap
            nodes={mapNodes}
            places={recommendations}
            selectedPlace={selectedPlace}
            routeMode="walking"
            onPlaceClick={(place) => place.uid && setSelectedUid(place.uid)}
            className="h-64 min-h-64 lg:h-[45vh] lg:min-h-[420px]"
          />

          <div className="space-y-5 lg:min-h-0 lg:overflow-y-auto lg:pr-2">
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
              <span className="text-xs text-ink-soft">附近 3km · 美食 / 景点</span>
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
                    added={addedPlaceKeys.includes(place.uid ?? `${place.name ?? "place"}:${place.lat ?? ""}:${place.lng ?? ""}`)}
                    onAdd={() => addRecommendation(place)}
                  />
                ))}
              </div>
            )}
          </section>

          <section className="rounded-card border border-coral/15 bg-coral/5 p-4">
            <div className="flex items-end justify-between gap-3">
              <SectionHeading icon={<Route size={17} />} title="住宿推荐" tone="coral" />
              <span className="text-xs text-ink-soft">节点周边 2.5km</span>
            </div>
            {hotelsQuery.isLoading ? (
              <InfoPanel>正在寻找适合落脚的酒店…</InfoPanel>
            ) : hotelsQuery.isError || hotelsQuery.data?.available === false ? (
              <InfoPanel>{hotelsQuery.data?.message ?? "住宿服务暂不可用，可稍后再试。"}</InfoPanel>
            ) : hotelCategories.every((category) => category.hotels.length === 0) ? (
              <InfoPanel>附近暂时没有可用酒店推荐。</InfoPanel>
            ) : (
              <>
                <div className="mt-3 flex flex-wrap gap-2">
                  {hotelCategories.map((category) => (
                    <button
                      key={category.key}
                      type="button"
                      onClick={() => setSelectedHotelCategory(category.key)}
                      className={`rounded-full px-3 py-1.5 text-xs font-semibold transition motion-reduce:transition-none ${selectedHotelCategory === category.key ? "bg-coral text-white" : "bg-white text-ink-soft hover:bg-coral/10 hover:text-coral-deep"}`}
                    >
                      {category.label}
                      <span className="ml-1 font-mono text-[10px] opacity-70">{category.hotels.length}</span>
                    </button>
                  ))}
                </div>
                {selectedHotels.length === 0 ? (
                  <div className="mt-3">
                    <InfoPanel>这个分类暂时没有合适的酒店，可以切换其他标签。</InfoPanel>
                  </div>
                ) : (
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    {selectedHotels.map((hotel) => (
                      <HotelCard
                        key={hotel.uid ?? `${hotel.name}-${hotel.lat}-${hotel.lng}`}
                        hotel={hotel}
                        added={addedPlaceKeys.includes(hotel.uid ?? `${hotel.name}:${hotel.lat}:${hotel.lng}`)}
                        onAdd={() => addHotel(hotel)}
                      />
                    ))}
                  </div>
                )}
              </>
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

          <section className="rounded-card border border-sky/15 bg-sky/5 p-4">
            <SectionHeading icon={<MessageCircle size={17} />} title="团队备注" tone="sky" />
            <div className="mt-3 space-y-2">
              {notesQuery.isLoading && <InfoPanel>正在读取团队备注…</InfoPanel>}
              {notesQuery.isError && <InfoPanel>备注暂不可用，请稍后重试。</InfoPanel>}
              {!notesQuery.isLoading && !notesQuery.isError && (notesQuery.data ?? []).length === 0 && <InfoPanel>还没有备注，留下第一条现场线索。</InfoPanel>}
              {(notesQuery.data ?? []).map((note) => (
                <div key={note.id} className="rounded-xl bg-white px-3 py-2.5">
                  <div className="flex items-center justify-between gap-2 text-[10px] text-ink-soft">
                    <span className="font-semibold text-sky">{note.authorName}</span>
                    <span>{formatNoteTime(note.createdAt)}</span>
                  </div>
                  <p className="mt-1 whitespace-pre-wrap text-sm leading-5 text-ink">{note.content}</p>
                </div>
              ))}
            </div>
            <form
              className="mt-3 flex gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                if (noteDraft.trim()) addNoteMutation.mutate(noteDraft.trim());
              }}
            >
              <textarea
                value={noteDraft}
                onChange={(event) => setNoteDraft(event.target.value)}
                rows={2}
                placeholder="写下给团队的提醒…"
                className="min-w-0 flex-1 resize-none rounded-xl border border-white bg-white px-3 py-2 text-sm text-ink outline-none focus:border-sky"
              />
              <Button type="submit" disabled={!noteDraft.trim() || addNoteMutation.isPending} className="self-end whitespace-nowrap">
                {addNoteMutation.isPending ? "发布中…" : "发布"}
              </Button>
            </form>
            {addNoteMutation.isError && <p className="mt-2 text-xs text-coral-deep">备注发布失败，请稍后重试。</p>}
          </section>
        </div>
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

function RecommendationCard({
  place,
  onClick,
  onAdd,
  added,
}: {
  place: MapPlace;
  onClick: () => void;
  onAdd: () => void;
  added: boolean;
}) {
  const clickable = Boolean(place.uid || (place.lat !== undefined && place.lng !== undefined));
  return (
    <div
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : -1}
      onClick={() => {
        if (place.uid) onClick();
      }}
      onKeyDown={(event) => {
        if (clickable && (event.key === "Enter" || event.key === " ")) {
          event.preventDefault();
          if (place.uid) onClick();
        }
      }}
      className={`rounded-card border border-slate-100 bg-white p-4 text-left transition motion-reduce:transition-none ${clickable ? "hover:-translate-y-0.5 hover:border-coral/30 hover:shadow-soft" : "opacity-70"}`}
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
      <button
        type="button"
        disabled={!place.lat || !place.lng || added}
        onClick={(event) => {
          event.stopPropagation();
          onAdd();
        }}
        className="mt-3 inline-flex items-center gap-1 rounded-full bg-mint/10 px-2.5 py-1 text-[11px] font-semibold text-emerald-700 transition hover:bg-mint/20 motion-reduce:transition-none"
      >
        {added ? "已加入行程" : <><Plus size={12} />加入行程</>}
      </button>
    </div>
  );
}

function HotelCard({
  hotel,
  added,
  onAdd,
}: {
  hotel: HotelRecommendation;
  added: boolean;
  onAdd: () => void;
}) {
  return (
    <article className="rounded-card border border-white bg-white p-3 transition hover:-translate-y-0.5 hover:shadow-soft motion-reduce:transition-none">
      <div className="h-24 overflow-hidden rounded-2xl">
        <ImageFallback
          src={hotel.image || getPlaceImage(hotel.name, "LODGING")}
          alt={hotel.name}
          city={hotel.address || "住宿推荐"}
        />
      </div>
      <div className="mt-3 flex items-start justify-between gap-2">
        <h4 className="line-clamp-2 text-sm font-semibold text-ink">{hotel.name}</h4>
        {hotel.rating !== undefined && <Badge tone="sun">评分 {hotel.rating}</Badge>}
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {hotel.price !== undefined && <Badge tone="mint">人均 ¥{hotel.price}</Badge>}
        {hotel.distanceMeters !== undefined && <Badge tone="sky">{formatDistance(hotel.distanceMeters)}</Badge>}
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {hotel.transitConvenient && <Badge tone="mint">交通便利 · {hotel.transitNote}</Badge>}
        {hotel.foodNearby && <Badge tone="coral">{hotel.foodNote ?? "楼下小吃街"}</Badge>}
      </div>
      <button
        type="button"
        disabled={added || hotel.lat === undefined || hotel.lng === undefined}
        onClick={onAdd}
        className="mt-3 inline-flex items-center gap-1 rounded-full bg-coral/10 px-3 py-1.5 text-xs font-semibold text-coral-deep transition hover:bg-coral/20 disabled:cursor-default disabled:opacity-60 motion-reduce:transition-none"
      >
        <Plus size={13} />{added ? "已加入行程" : "加入住宿分支"}
      </button>
    </article>
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

function formatNoteTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "刚刚";
  return date.toLocaleString("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}
