import { getToken, type AuthUser } from "../auth";
import { activeTrip, changeLogs, events, groupMembers, guides, mockDashboard, plans } from "../mocks/data";
import type { DashboardData, TravelGuide, Trip } from "../types";

const useMocks = import.meta.env.VITE_USE_MOCKS !== "false";
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
    if (useMocks) return mockDashboard;
    const [trips, user] = await Promise.all([request<Trip[]>("/api/trips"), api.me()]);
    return { ...mockDashboard, user, trips, activeTrip: trips[0] };
  },
  async guides(): Promise<TravelGuide[]> {
    if (useMocks) return guides;
    return request<TravelGuide[]>("/api/guides");
  },
  async trip(id: number): Promise<Trip> {
    if (useMocks) return mockDashboard.trips.find((trip) => trip.id === id) ?? mockDashboard.activeTrip;
    return request<Trip>(`/api/trips/${id}`);
  },
  async groups() {
    if (useMocks) return [{ ...mockDashboard.activeTrip.group, roomCode: "SH24-7K" }];
    return request<{ id: number; name: string; description: string }[]>("/api/groups");
  },
  async members(groupId: number) {
    if (useMocks) return groupMembers;
    return request<any[]>(`/api/groups/${groupId}/members`);
  },
  async events() { return events; },
  async plans() { return plans; },
  async changelogs() { return changeLogs; },
  async risk() { return { score: activeTrip.riskScore, factors: [{ label: "事件严重度", value: 30, detail: "1 个 HIGH 级天气事件" }, { label: "受影响节点占比", value: 22, detail: "1 / 4 个节点" }, { label: "成本暴露", value: 8, detail: "额外成本约 ¥160" }, { label: "时间缓冲", value: 8, detail: "距离节点开始还有 4 小时" }] }; },
  async removeMember(groupId: number, memberId: number, operatorId: number = 1): Promise<void> {
    await request<void>(`/api/groups/${groupId}/members/${memberId}?operatorId=${operatorId}`, { method: "DELETE" });
  },
  async transferOwner(groupId: number, newOwnerId: number, operatorId: number = 1): Promise<{ id: number; name: string; roomCode?: string }> {
    return request<{ id: number; name: string; roomCode?: string }>(
      `/api/groups/${groupId}/transfer?newOwnerId=${newOwnerId}&operatorId=${operatorId}`,
      { method: "PUT" },
    );
  },
};
