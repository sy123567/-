import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";

const STORAGE_KEY = "selectedTripId";

export function useTripScope() {
  const tripsQuery = useQuery({ queryKey: ["trips"], queryFn: api.trips });
  const [searchParams, setSearchParams] = useSearchParams();
  const trips = tripsQuery.data ?? [];
  const urlTripId = Number(searchParams.get("trip"));
  const storedTripId = Number(localStorage.getItem(STORAGE_KEY));
  const preferredId = Number.isFinite(urlTripId) && urlTripId > 0
    ? urlTripId
    : Number.isFinite(storedTripId) && storedTripId > 0
      ? storedTripId
      : trips.find((trip) => trip.status === "ONGOING")?.id ?? trips[0]?.id;
  const selectedTrip = trips.find((trip) => trip.id === preferredId) ?? trips[0];
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
    const next = trips.find((trip) => trip.id === nextTripId);
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
