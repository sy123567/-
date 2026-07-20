import { useEffect, useMemo, useRef, useState } from "react";
import { MapPin, ShieldAlert } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import type { ItineraryNode } from "../types";

type BMapPoint = {
  lat: number;
  lng: number;
};

type BMapMap = {
  centerAndZoom: (point: BMapPoint, zoom: number) => void;
  addOverlay: (overlay: unknown) => void;
  clearOverlays: () => void;
};

type BMapNamespace = {
  Map: new (container: HTMLElement) => BMapMap;
  Point: new (lng: number, lat: number) => BMapPoint;
  Marker: new (point: BMapPoint) => unknown;
  Polyline: new (points: BMapPoint[], options: Record<string, unknown>) => unknown;
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
  className = "",
  onMarkerClick,
}: {
  nodes: ItineraryNode[];
  className?: string;
  onMarkerClick?: (node: ItineraryNode) => void;
}) {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstance = useRef<BMapMap | null>(null);
  const [scriptReady, setScriptReady] = useState<boolean | null>(null);
  const configQuery = useQuery({ queryKey: ["map-config"], queryFn: api.mapConfig });
  const mapNodes = useMemo(
    () =>
      nodes.filter(
        (node) =>
          Number.isFinite(node.latitude) &&
          Number.isFinite(node.longitude) &&
          node.latitude !== 0 &&
          node.longitude !== 0,
      ),
    [nodes],
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
    const points = mapNodes.map((node) => new window.BMap!.Point(node.longitude, node.latitude));
    map.centerAndZoom(points[0], mapNodes.length > 1 ? 12 : 14);
    map.clearOverlays();
    points.forEach((point, index) => {
      const marker = new window.BMap!.Marker(point) as { addEventListener?: (event: string, callback: () => void) => void };
      marker.addEventListener?.("click", () => onMarkerClick?.(mapNodes[index]));
      map.addOverlay(marker);
    });
    if (points.length > 1) {
      map.addOverlay(
        new window.BMap!.Polyline(points, {
          strokeColor: "#FF6B5F",
          strokeWeight: 4,
          strokeOpacity: 0.7,
        }),
      );
    }
    return () => {
      map.clearOverlays();
      mapInstance.current = null;
    };
  }, [mapNodes, onMarkerClick, scriptReady]);

  if (configQuery.isLoading) return <MapPlaceholder className={className} label="正在准备地图…" />;
  if (mapNodes.length === 0) return <MapPlaceholder className={className} label="节点还没有可用坐标" />;
  if (scriptReady === false || configQuery.isError || !configQuery.data?.available) {
    return <MapPlaceholder className={className} label="地图暂不可用，仍可查看节点列表" />;
  }
  return <div ref={mapRef} className={`min-h-[220px] overflow-hidden rounded-card bg-sky/10 ${className}`} aria-label="行程节点地图" />;
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
