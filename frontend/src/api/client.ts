import { getToken, type AuthUser } from "../auth";
import { guides } from "../mocks/data";
import type {
  AlternativePlan,
  ChangeLog,
  DashboardData,
  ExternalEvent,
  GroupMember,
  ImpactAssessment,
  ItineraryNode,
  NodeNote,
  TravelGuide,
  TravelGroup,
  Trip,
} from "../types";

export const apiBase = import.meta.env.VITE_API_BASE ?? "http://localhost:8080";

export class ApiError extends Error {
  readonly status?: number;
  readonly endpoint: string;
  readonly isNetworkError: boolean;

  constructor(message: string, options: { status?: number; endpoint: string; isNetworkError?: boolean }) {
    super(message);
    this.name = "ApiError";
    this.status = options.status;
    this.endpoint = options.endpoint;
    this.isNetworkError = options.isNetworkError ?? false;
  }
}

export type AuthResponse = {
  token: string;
  userId: number;
  name: string;
  email: string;
  phone?: string;
};

export type FriendshipRequest = {
  id: number;
  requester: AuthUser;
  addressee: AuthUser;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
};

export type TripRisk = {
  tripId: number;
  overallScore: number;
  riskLevel: string;
  totalNodes: number;
  affectedNodes: number;
  assessments: ImpactAssessment[];
};

export type WeatherPreview = {
  available: boolean;
  placeName?: string;
  tempMin?: number;
  tempMax?: number;
  phrase?: string;
  hasAlert: boolean;
  hasPrecipitation: boolean;
  message?: string;
};

export type AiPlace = {
  placeName: string;
  category: "吃" | "喝" | "玩" | "乐" | "住";
  nodeType: ItineraryNode["nodeType"];
  description: string;
  latitude: number;
  longitude: number;
  suggestedDurationMinutes: number;
};

export type AiPlanResult = {
  available: boolean;
  source: "ai" | "offline";
  city: string;
  places: AiPlace[];
  message?: string;
};

export type MapPlace = {
  name?: string;
  lat?: number;
  lng?: number;
  address?: string;
  province?: string;
  city?: string;
  area?: string;
  uid?: string;
  telephone?: string;
  overallRating?: number;
  commentNum?: number;
  price?: number;
  tag?: string;
  image?: string;
  distanceMeters?: number;
};

export type MapPlaceDetail = {
  uid?: string;
  name?: string;
  lat?: number;
  lng?: number;
  address?: string;
  telephone?: string;
  overallRating?: number;
  commentNum?: number;
  price?: number;
  tag?: string;
  image?: string;
};

export type MapRoute = {
  available: boolean;
  mode: "driving" | "riding" | "walking";
  distanceMeters?: number;
  durationSeconds?: number;
  message?: string;
};

export type MapConfig = {
  available: boolean;
  ak: string;
};

export type MapSearchResult = {
  available: boolean;
  places: MapPlace[];
  message?: string;
};

export type MapPlaceResult = {
  available: boolean;
  place?: MapPlaceDetail;
  message?: string;
};

export type MapGeocode = {
  available: boolean;
  lat?: number;
  lng?: number;
  message?: string;
};

export type MapResolve = {
  available: boolean;
  lat?: number;
  lng?: number;
  uid?: string;
  name?: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const endpoint = `${apiBase}${path}`;
  const token = getToken();
  const headers = new Headers(init?.headers);
  headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  let response: Response;
  try {
    response = await fetch(endpoint, { ...init, headers });
  } catch {
    throw new ApiError(`无法连接后端服务：${apiBase}`, { endpoint, isNetworkError: true });
  }
  if (!response.ok) {
    let message = `后端请求失败（${response.status}）`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // Keep the HTTP status message when the response is not JSON.
    }
    throw new ApiError(message, { endpoint, status: response.status });
  }
  if (response.status === 204) return undefined as T;
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError("后端返回了无法解析的数据", { endpoint, status: response.status });
  }
}

export const api = {
  async login(email: string, password: string): Promise<AuthResponse> {
    return request<AuthResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  },
  async register(name: string, email: string, password: string, phone?: string): Promise<AuthResponse> {
    return request<AuthResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password, phone }),
    });
  },
  async me(): Promise<AuthUser> {
    return request<AuthUser>("/api/auth/me");
  },
  async updateProfile(profile: Pick<AuthUser, "name" | "email" | "phone">): Promise<AuthUser> {
    return request<AuthUser>("/api/auth/me", {
      method: "PATCH",
      body: JSON.stringify(profile),
    });
  },
  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    return request<void>("/api/auth/me/password", {
      method: "POST",
      body: JSON.stringify({ currentPassword, newPassword }),
    });
  },
  async deleteAccount(): Promise<void> {
    return request<void>("/api/auth/me", { method: "DELETE" });
  },
  async searchFriends(keyword: string): Promise<AuthUser[]> {
    return request<AuthUser[]>(`/api/friends/search?keyword=${encodeURIComponent(keyword)}`);
  },
  async friends(): Promise<AuthUser[]> {
    return request<AuthUser[]>("/api/friends");
  },
  async incomingFriendRequests(): Promise<FriendshipRequest[]> {
    return request<FriendshipRequest[]>("/api/friends/requests/incoming");
  },
  async outgoingFriendRequests(): Promise<FriendshipRequest[]> {
    return request<FriendshipRequest[]>("/api/friends/requests/outgoing");
  },
  async sendFriendRequest(addresseeId: number): Promise<FriendshipRequest> {
    return request<FriendshipRequest>("/api/friends/request", {
      method: "POST",
      body: JSON.stringify({ addresseeId }),
    });
  },
  async acceptFriendRequest(id: number): Promise<FriendshipRequest> {
    return request<FriendshipRequest>(`/api/friends/requests/${id}/accept`, { method: "POST" });
  },
  async rejectFriendRequest(id: number): Promise<FriendshipRequest> {
    return request<FriendshipRequest>(`/api/friends/requests/${id}/reject`, { method: "POST" });
  },
  async deleteFriend(friendId: number): Promise<void> {
    return request<void>(`/api/friends/${friendId}`, { method: "DELETE" });
  },
  async dashboard(): Promise<DashboardData> {
    const [trips, user, events] = await Promise.all([
      request<Trip[]>("/api/trips"),
      api.me(),
      request<ExternalEvent[]>("/api/events/active"),
    ]);
    const activeTrip = trips.find((trip) => trip.status === "ONGOING") ?? trips[0];
    return { user, trips, activeTrip, events, notifications: [] };
  },
  async guides(): Promise<TravelGuide[]> {
    return guides;
  },
  async trip(id: number): Promise<Trip> {
    return request<Trip>(`/api/trips/${id}`);
  },
  async createTrip(groupId: number, trip: Pick<Trip, "title" | "status" | "startDate" | "endDate" | "totalBudget">): Promise<Trip> {
    return request<Trip>(`/api/trips?groupId=${groupId}`, {
      method: "POST",
      body: JSON.stringify(trip),
    });
  },
  async addNode(tripId: number, node: Omit<ItineraryNode, "id">): Promise<ItineraryNode> {
    return request<ItineraryNode>(`/api/trips/${tripId}/nodes`, {
      method: "POST",
      body: JSON.stringify(node),
    });
  },
  async updateNode(tripId: number, nodeId: number, node: Partial<Omit<ItineraryNode, "id" | "status">>): Promise<ItineraryNode> {
    return request<ItineraryNode>(`/api/trips/${tripId}/nodes/${nodeId}`, {
      method: "PUT",
      body: JSON.stringify(node),
    });
  },
  async deleteNode(tripId: number, nodeId: number): Promise<void> {
    await request<void>(`/api/trips/${tripId}/nodes/${nodeId}`, { method: "DELETE" });
  },
  async nodeNotes(tripId: number, nodeId: number): Promise<NodeNote[]> {
    return request<NodeNote[]>(`/api/trips/${tripId}/nodes/${nodeId}/notes`);
  },
  async addNodeNote(tripId: number, nodeId: number, content: string): Promise<NodeNote> {
    return request<NodeNote>(`/api/trips/${tripId}/nodes/${nodeId}/notes`, {
      method: "POST",
      body: JSON.stringify({ content }),
    });
  },
  async previewWeather(lat: number, lon: number): Promise<WeatherPreview> {
    return request<WeatherPreview>(`/api/weather/preview?lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lon)}`);
  },
  async aiPlan(city: string, days: number, interests?: string, groupId?: number): Promise<AiPlanResult> {
    const params = new URLSearchParams({ city, days: String(days) });
    if (interests?.trim()) params.set("interests", interests.trim());
    if (groupId !== undefined) params.set("groupId", String(groupId));
    return request<AiPlanResult>(`/api/ai/plan?${params.toString()}`);
  },
  async mapConfig(): Promise<MapConfig> {
    return request<MapConfig>("/api/map/config");
  },
  async mapSearch(query: string, region: string): Promise<MapSearchResult> {
    const params = new URLSearchParams({ query, region });
    return request<MapSearchResult>(`/api/map/search?${params.toString()}`);
  },
  async mapNearby(
    query: string,
    lat: number,
    lng: number,
    radius = 3000,
  ): Promise<MapSearchResult> {
    const params = new URLSearchParams({
      query,
      lat: String(lat),
      lng: String(lng),
      radius: String(radius),
    });
    return request<MapSearchResult>(`/api/map/nearby?${params.toString()}`);
  },
  async mapResolve(name: string, lat: number, lng: number): Promise<MapResolve> {
    const params = new URLSearchParams({
      name,
      lat: String(lat),
      lng: String(lng),
    });
    return request<MapResolve>(`/api/map/resolve?${params.toString()}`);
  },
  async mapPlace(uid: string): Promise<MapPlaceResult> {
    return request<MapPlaceResult>(`/api/map/place?uid=${encodeURIComponent(uid)}`);
  },
  async mapRoute(
    fromLat: number,
    fromLng: number,
    toLat: number,
    toLng: number,
    mode: MapRoute["mode"] = "driving",
  ): Promise<MapRoute> {
    const params = new URLSearchParams({
      fromLat: String(fromLat),
      fromLng: String(fromLng),
      toLat: String(toLat),
      toLng: String(toLng),
      mode,
    });
    return request<MapRoute>(`/api/map/route?${params.toString()}`);
  },
  async mapGeocode(address: string): Promise<MapGeocode> {
    return request<MapGeocode>(`/api/map/geocode?address=${encodeURIComponent(address)}`);
  },
  async groups() {
    return request<TravelGroup[]>("/api/groups");
  },
  async group(id: number): Promise<TravelGroup> {
    return request<TravelGroup>(`/api/groups/${id}`);
  },
  async createGroup(name: string, description?: string): Promise<TravelGroup> {
    return request<TravelGroup>("/api/groups", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    });
  },
  async joinGroup(roomCode: string): Promise<TravelGroup> {
    return request<TravelGroup>("/api/groups/join", {
      method: "POST",
      body: JSON.stringify({ roomCode }),
    });
  },
  async members(groupId: number): Promise<GroupMember[]> {
    return request<GroupMember[]>(`/api/groups/${groupId}/members`);
  },
  async events(): Promise<ExternalEvent[]> {
    return request<ExternalEvent[]>("/api/events/active");
  },
  async myEvents(): Promise<ExternalEvent[]> {
    return request<ExternalEvent[]>("/api/events/mine");
  },
  async impacts(tripId: number): Promise<ImpactAssessment[]> {
    return request<ImpactAssessment[]>(`/api/trips/${tripId}/impacts`);
  },
  async plans(tripId: number): Promise<AlternativePlan[]> {
    return request<AlternativePlan[]>(`/api/trips/${tripId}/plans`);
  },
  async changelogs(tripId: number): Promise<ChangeLog[]> {
    return request<ChangeLog[]>(`/api/trips/${tripId}/changelogs`);
  },
  async risk(tripId: number): Promise<TripRisk> {
    return request<TripRisk>(`/api/trips/${tripId}/risk`);
  },
  async removeMember(groupId: number, memberId: number): Promise<void> {
    await request<void>(`/api/groups/${groupId}/members/${memberId}`, { method: "DELETE" });
  },
  async transferOwner(groupId: number, newOwnerId: number): Promise<TravelGroup> {
    return request<TravelGroup>(
      `/api/groups/${groupId}/transfer?newOwnerId=${newOwnerId}`,
      { method: "PUT" },
    );
  },
};
