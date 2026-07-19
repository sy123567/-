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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const endpoint = `${apiBase}${path}`;
  let response: Response;
  try {
    response = await fetch(endpoint, {
      headers: { "Content-Type": "application/json", ...init?.headers },
      ...init,
    });
  } catch {
    throw new ApiError(`无法连接后端服务：${apiBase}`, { endpoint, isNetworkError: true });
  }
  if (!response.ok) {
    throw new ApiError(`后端请求失败（${response.status}）`, { endpoint, status: response.status });
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError("后端返回了无法解析的数据", { endpoint, status: response.status });
  }
}

export const api = {
  async dashboard(): Promise<DashboardData> {
    if (useMocks) return mockDashboard;
    const [trips, users] = await Promise.all([request<Trip[]>("/api/trips"), request<DashboardData["user"][]>("/api/users")]);
    return { ...mockDashboard, user: users[0], trips, activeTrip: trips[0] };
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
    const endpoint = `${apiBase}/api/groups/${groupId}/members/${memberId}?operatorId=${operatorId}`;
    const response = await fetch(endpoint, { method: "DELETE" });
    if (!response.ok) {
      throw new ApiError(`删除成员失败（${response.status}）`, { endpoint, status: response.status });
    }
  },
  async transferOwner(groupId: number, newOwnerId: number, operatorId: number = 1): Promise<{ id: number; name: string; roomCode?: string }> {
    const endpoint = `${apiBase}/api/groups/${groupId}/transfer?newOwnerId=${newOwnerId}&operatorId=${operatorId}`;
    const response = await fetch(endpoint, { method: "PUT" });
    if (!response.ok) {
      throw new ApiError(`转移群主失败（${response.status}）`, { endpoint, status: response.status });
    }
    return (await response.json()) as { id: number; name: string; roomCode?: string };
  },
};
