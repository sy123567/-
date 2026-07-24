import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";

const STORAGE_KEY = "selectedTripId";

export function useTripScope(options?: { includeCompleted?: boolean }) {
  const includeCompleted = options?.includeCompleted ?? false;
  const tripsQuery = useQuery({ queryKey: ["trips"], queryFn: api.trips });
  const [searchParams, setSearchParams] = useSearchParams();
  const allTrips = tripsQuery.data ?? [];
  // 默认在下拉/切换列表中隐藏已过期（已完成）的行程，避免界面冗杂；讨论区等场景可传 includeCompleted 显示全部。
  const trips = includeCompleted ? allTrips : allTrips.filter((trip) => trip.status !== "COMPLETED");
  const urlTripId = Number(searchParams.get("trip"));
  const storedTripId = Number(localStorage.getItem(STORAGE_KEY));
  const preferredId = Number.isFinite(urlTripId) && urlTripId > 0
    ? urlTripId
    : Number.isFinite(storedTripId) && storedTripId > 0
      ? storedTripId
      : trips.find((trip) => trip.status === "ONGOING")?.id ?? trips[0]?.id;
  const selectedTrip = allTrips.find((trip) => trip.id === preferredId) ?? trips[0];
  const tripId = selectedTrip?.id;

  useEffect(() => {
    if (tripId === undefined) return;
    const currentUrlId = Number(searchParams.get("trip"));
    if (currentUrlId !== tripId) {
      const next = new URLSearchParams(searchParams);
      next.set("trip", String(tripId));
      setSearchParams(next, { replace: true });
    }
    localStorage.setItem(STORAGE_KEY, String(tripId));
  }, [tripId, searchParams, setSearchParams]);

  const setTripId = (nextTripId: number) => {
    const next = allTrips.find((trip) => trip.id === nextTripId);
    if (!next) return;
    localStorage.setItem(STORAGE_KEY, String(nextTripId));
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("trip", String(nextTripId));
    setSearchParams(nextParams);
  };

  return {
    trips,
    tripId,
    selectedTrip,
    setTripId,
    isLoading: tripsQuery.isLoading,
    isEmpty: !tripsQuery.isLoading && trips.length === 0,
  };
}

export type TripScope = ReturnType<typeof useTripScope>;
