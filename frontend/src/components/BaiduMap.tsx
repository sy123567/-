import { useEffect, useMemo, useRef, useState } from "react";
import { MapPin, ShieldAlert } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api, type MapPlace } from "../api/client";
import type { ItineraryNode } from "../types";

type BMapPoint = {
  lat: number;
  lng: number;
};

type BMapSize = {
  width: number;
  height: number;
};

type BMapOverlay = {
  addEventListener?: (event: string, callback: () => void) => void;
  openInfoWindow?: (infoWindow: BMapInfoWindow) => void;
  setLabel?: (label: BMapLabel) => void;
  setIcon?: (icon: unknown) => void;
};

type BMapInfoWindow = {
  close?: () => void;
};

type BMapLabel = {
  setStyle?: (style: Record<string, string>) => void;
};

type BMapMap = {
  centerAndZoom: (point: BMapPoint, zoom: number) => void;
  addOverlay: (overlay: unknown) => void;
  enableScrollWheelZoom?: (enable: boolean) => void;
  addControl?: (control: unknown) => void;
  clearOverlays: () => void;
  removeOverlay?: (overlay: unknown) => void;
  openInfoWindow?: (infoWindow: BMapInfoWindow, point: BMapPoint) => void;
  closeInfoWindow?: () => void;
  setViewport?: (points: BMapPoint[], options?: Record<string, unknown>) => void;
};

type BMapRoute = {
  search: (from: BMapPoint, to: BMapPoint) => void;
  clearResults?: () => void;
  setSearchCompleteCallback?: (callback: (result: { getStatus?: () => number }) => void) => void;
  setErrorCallback?: (callback: () => void) => void;
};

type BMapNamespace = {
  Map: new (container: HTMLElement) => BMapMap;
  Point: new (lng: number, lat: number) => BMapPoint;
  Size: new (width: number, height: number) => BMapSize;
  Icon: new (url: string, size: BMapSize, options?: Record<string, unknown>) => unknown;
  Label?: new (content: string, options?: Record<string, unknown>) => BMapLabel;
  Marker: new (point: BMapPoint, options?: Record<string, unknown>) => BMapOverlay;
  Polyline: new (points: BMapPoint[], options: Record<string, unknown>) => BMapOverlay;
  InfoWindow: new (content: string, options?: Record<string, unknown>) => BMapInfoWindow;
  NavigationControl?: new (options?: Record<string, unknown>) => unknown;
  WalkingRoute?: new (map: BMapMap, options?: Record<string, unknown>) => BMapRoute;
  DrivingRoute?: new (map: BMapMap, options?: Record<string, unknown>) => BMapRoute;
};

declare global {
  interface Window {
    BMap?: BMapNamespace;
  }
}

let baiduScriptPromise: Promise<boolean> | null = null;

function loadBaiduScript(ak: string): Promise<boolean> {
  if (window.BMap) return Promise.resolve(true);
  if (baiduScriptPromise) return baiduScriptPromise;
  baiduScriptPromise = new Promise((resolve) => {
    const callbackName = "__routeTrailBaiduMapReady";
    const global = window as unknown as Record<string, unknown>;
    let settled = false;
    const finish = (available: boolean) => {
      if (settled) return;
      settled = true;
      delete global[callbackName];
      resolve(available && Boolean(window.BMap));
    };
    global[callbackName] = () => finish(true);
    const script = document.createElement("script");
    script.id = "route-trail-baidu-map-script";
    script.async = true;
    script.src = `https://api.map.baidu.com/api?v=3.0&ak=${encodeURIComponent(ak)}&callback=${callbackName}`;
    script.onerror = () => finish(false);
    window.setTimeout(() => finish(Boolean(window.BMap)), 10000);
    document.head.appendChild(script);
  });
  return baiduScriptPromise;
}

export function BaiduMap({
  nodes,
  places = [],
  selectedPlace = null,
  routeMode = "walking",
  className = "",
  onMarkerClick,
  onPlaceClick,
}: {
  nodes: ItineraryNode[];
  places?: MapPlace[];
  selectedPlace?: MapPlace | null;
  routeMode?: "walking" | "driving";
  className?: string;
  onMarkerClick?: (node: ItineraryNode) => void;
  onPlaceClick?: (place: MapPlace) => void;
}) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<BMapMap | null>(null);
  const [scriptReady, setScriptReady] = useState<boolean | null>(null);
  const configQuery = useQuery({ queryKey: ["map-config"], queryFn: api.mapConfig });
  const mapNodes = useMemo(
    () => nodes.filter((node) => hasCoordinates(node.latitude, node.longitude)),
    [nodes],
  );
  const mapPlaces = useMemo(
    () => places.filter((place) => hasCoordinates(place.lat, place.lng)),
    [places],
  );

  useEffect(() => {
    let cancelled = false;
    if (configQuery.isLoading) {
      return () => {
        cancelled = true;
      };
    }
    if (configQuery.isError || !configQuery.data?.available || !configQuery.data.ak || mapNodes.length === 0) {
      return () => {
        cancelled = true;
      };
    }
    void loadBaiduScript(configQuery.data.ak).then((available) => {
      if (!cancelled) setScriptReady(available);
    });
    return () => {
      cancelled = true;
    };
  }, [configQuery.data, configQuery.isError, configQuery.isLoading, mapNodes.length]);

  useEffect(() => {
    if (!scriptReady || !mapRef.current || !window.BMap || mapNodes.length === 0) return;
    const map = new window.BMap.Map(mapRef.current);
    mapInstance.current = map;
    map.enableScrollWheelZoom?.(true);
    if (window.BMap.NavigationControl) {
      map.addControl?.(new window.BMap.NavigationControl());
    }
    const nodePoints = mapNodes.map((node) => new window.BMap!.Point(node.longitude, node.latitude));
    const placePoints = mapPlaces.map((place) => new window.BMap!.Point(place.lng!, place.lat!));
    const allPoints = [...nodePoints, ...placePoints];
    const overlays: BMapOverlay[] = [];
    let infoWindow: BMapInfoWindow | null = null;
    let route: BMapRoute | null = null;
    let fallbackRoute: BMapOverlay | null = null;
    map.clearOverlays();
    nodePoints.forEach((point, index) => {
      const marker = new window.BMap!.Marker(point);
      if (window.BMap!.Label) {
        const label = new window.BMap!.Label(String(mapNodes[index].sequenceOrder), { offset: new window.BMap!.Size(-6, -8) });
        label.setStyle?.({
          color: "#ffffff",
          backgroundColor: "#FF6B5F",
          border: "0",
          borderRadius: "999px",
          fontSize: "11px",
          fontWeight: "700",
          lineHeight: "20px",
          textAlign: "center",
          width: "20px",
          height: "20px",
        });
        marker.setLabel?.(label);
      }
      marker.addEventListener?.("click", () => onMarkerClick?.(mapNodes[index]));
      overlays.push(marker);
      map.addOverlay(marker);
    });
    const placeIcon = createPlaceIcon(window.BMap);
    mapPlaces.forEach((place, index) => {
      const point = placePoints[index];
      const marker = new window.BMap!.Marker(point, { icon: placeIcon });
      const showInfo = () => {
        infoWindow = new window.BMap!.InfoWindow(placeInfoHtml(place), {
          width: 220,
          height: 100,
          title: "附近攻略",
        });
        map.openInfoWindow?.(infoWindow, point);
        onPlaceClick?.(place);
      };
      marker.addEventListener?.("click", showInfo);
      if (selectedPlace?.uid && selectedPlace.uid === place.uid) showInfo();
      overlays.push(marker);
      map.addOverlay(marker);
    });
    if (nodePoints.length > 1) {
      const routeLine = new window.BMap!.Polyline(nodePoints, {
        strokeColor: "#FF6B5F",
        strokeWeight: 4,
        strokeOpacity: 0.7,
      });
      routeLine.addEventListener?.("click", () => {
        try {
          map.setViewport?.(nodePoints, { margins: [24, 24, 24, 24] });
        } catch {
          map.centerAndZoom(nodePoints[0], 14);
        }
      });
      overlays.push(routeLine);
      map.addOverlay(routeLine);
    }
    if (selectedPlace && hasCoordinates(selectedPlace.lat, selectedPlace.lng)) {
      const from = nodePoints[0];
      const to = new window.BMap!.Point(selectedPlace.lng!, selectedPlace.lat!);
      fallbackRoute = new window.BMap!.Polyline([from, to], {
        strokeColor: "#2F9BFF",
        strokeWeight: 3,
        strokeOpacity: 0.75,
        strokeStyle: "dashed",
      });
      overlays.push(fallbackRoute);
      map.addOverlay(fallbackRoute);
      route = createRoute(window.BMap, map, routeMode, fallbackRoute, from, to);
    }
    if (allPoints.length > 1) {
      try {
        map.setViewport?.(allPoints, { margins: [24, 24, 24, 24] });
      } catch {
        map.centerAndZoom(allPoints[0], 14);
      }
    } else {
      map.centerAndZoom(allPoints[0], 14);
    }
    return () => {
      route?.clearResults?.();
      map.closeInfoWindow?.();
      infoWindow?.close?.();
      map.clearOverlays();
      overlays.forEach((overlay) => map.removeOverlay?.(overlay));
      mapInstance.current = null;
    };
  }, [mapNodes, mapPlaces, onMarkerClick, onPlaceClick, routeMode, scriptReady, selectedPlace]);

  if (configQuery.isLoading) return <MapPlaceholder className={className} label="正在准备地图…" />;
  if (mapNodes.length === 0) return <MapPlaceholder className={className} label="节点还没有可用坐标" />;
  if (scriptReady === false || configQuery.isError || !configQuery.data?.available) {
    return <MapPlaceholder className={className} label="地图暂不可用，仍可查看节点列表" />;
  }
  return <div ref={mapRef} className={`min-h-[220px] overflow-hidden rounded-card bg-sky/10 ${className}`} aria-label="行程节点地图" />;
}

function createRoute(
  BMap: BMapNamespace,
  map: BMapMap,
  mode: "walking" | "driving",
  fallbackRoute: BMapOverlay,
  from: BMapPoint,
  to: BMapPoint,
) {
  const RouteConstructor = mode === "driving" ? BMap.DrivingRoute : BMap.WalkingRoute;
  if (!RouteConstructor) return null;
  try {
    const route = new RouteConstructor(map, {
      renderOptions: { map, autoViewport: false },
    });
    route.setSearchCompleteCallback?.((result) => {
      if (result.getStatus?.() === 0) map.removeOverlay?.(fallbackRoute);
    });
    route.setErrorCallback?.(() => undefined);
    route.search(from, to);
    return route;
  } catch {
    return null;
  }
}

function createPlaceIcon(BMap: BMapNamespace) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 28 28"><circle cx="14" cy="14" r="11" fill="#17C3A2" stroke="#ffffff" stroke-width="3"/><circle cx="14" cy="14" r="3" fill="#ffffff"/></svg>`;
  return new BMap.Icon(
    `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`,
    new BMap.Size(28, 28),
    { anchor: new BMap.Size(14, 14) },
  );
}

function placeInfoHtml(place: MapPlace) {
  const image = place.image
    ? `<img src="${escapeHtml(place.image)}" alt="" style="width:100%;height:58px;object-fit:cover;border-radius:8px;margin-bottom:6px" />`
    : "";
  const rating = place.overallRating === undefined ? "" : `评分 ${place.overallRating}`;
  const price = place.price === undefined ? "" : `人均 ¥${place.price}`;
  return `<div style="font:12px/1.5 sans-serif;color:#17212b">${image}<strong>${escapeHtml(place.name ?? "附近地点")}</strong><br/><span>${escapeHtml([rating, price].filter(Boolean).join(" · "))}</span><br/><span>${escapeHtml(place.address ?? "附近推荐地点")}</span></div>`;
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (character) => {
    const entities: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return entities[character];
  });
}

function hasCoordinates(latitude?: number, longitude?: number) {
  return (
    latitude !== undefined &&
    longitude !== undefined &&
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude !== 0 &&
    longitude !== 0
  );
}

function MapPlaceholder({ className, label }: { className: string; label: string }) {
  return (
    <div className={`grid min-h-[220px] place-items-center rounded-card border border-dashed border-sky/25 bg-sky/5 p-6 text-center ${className}`}>
      <div>
        <div className="mx-auto grid h-11 w-11 place-items-center rounded-2xl bg-white text-sky shadow-sm">
          <MapPin size={20} />
        </div>
        <p className="mt-3 text-sm font-semibold text-ink">{label}</p>
        <p className="mt-1 flex items-center justify-center gap-1 text-xs text-ink-soft">
          <ShieldAlert size={13} />地图服务异常不会影响行程操作
        </p>
      </div>
    </div>
  );
}
