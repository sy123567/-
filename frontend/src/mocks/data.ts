import type { DashboardData, ExternalEvent, GroupMember, ItineraryNode, TravelGuide, Trip, User } from "../types";

const user: User = { id: 1, name: "林小满", email: "xiaoman@example.com", avatar: "https://i.pravatar.cc/100?img=47" };
const group = { id: 1, name: "周末慢游组", description: "一起把周末过成一张好看的地图", ownerUser: user, memberCount: 4, roomCode: "SH24-7K" };

export const groupMembers: GroupMember[] = [
  { id: 1, user, role: "OWNER", constraint: { availableFrom: "2025-04-19", availableTo: "2025-04-21", maxBudget: 2400, mustVisitPlaces: ["上海外滩", "豫园"], fitnessLevel: "MEDIUM", dietaryNeeds: [], accessibilityNeeds: [] } },
  { id: 2, user: { id: 2, name: "周知远", email: "zhi@example.com", avatar: "https://i.pravatar.cc/100?img=13" }, role: "MEMBER", constraint: { availableFrom: "2025-04-19", availableTo: "2025-04-20", maxBudget: 1800, mustVisitPlaces: ["朱家角"], fitnessLevel: "LOW", dietaryNeeds: ["不吃辣"], accessibilityNeeds: [] } },
  { id: 3, user: { id: 3, name: "许桃", email: "tao@example.com", avatar: "https://i.pravatar.cc/100?img=44" }, role: "MEMBER", constraint: { availableFrom: "2025-04-19", availableTo: "2025-04-21", maxBudget: 2200, mustVisitPlaces: ["武康路"], fitnessLevel: "HIGH", dietaryNeeds: ["素食"], accessibilityNeeds: [] } },
  { id: 4, user: { id: 4, name: "陈一禾", email: "yihe@example.com", avatar: "https://i.pravatar.cc/100?img=61" }, role: "MEMBER", constraint: { availableFrom: "2025-04-20", availableTo: "2025-04-21", maxBudget: 2000, mustVisitPlaces: ["上海博物馆"], fitnessLevel: "MEDIUM", dietaryNeeds: [], accessibilityNeeds: ["少走楼梯"] } },
];

const nodes: ItineraryNode[] = [
  { id: 1, name: "上海外滩", placeName: "上海外滩", latitude: 31.23, longitude: 121.47, nodeType: "ATTRACTION", plannedStart: "2025-04-19T09:30:00", plannedEnd: "2025-04-19T11:30:00", cost: 0, sequenceOrder: 1, status: "CONFIRMED" },
  { id: 2, name: "本帮菜午餐", placeName: "老吉士", latitude: 31.21, longitude: 121.45, nodeType: "MEAL", plannedStart: "2025-04-19T12:00:00", plannedEnd: "2025-04-19T13:30:00", cost: 168, sequenceOrder: 2, status: "PLANNED" },
  { id: 3, name: "豫园", placeName: "豫园", latitude: 31.23, longitude: 121.49, nodeType: "ATTRACTION", plannedStart: "2025-04-19T14:30:00", plannedEnd: "2025-04-19T17:00:00", cost: 40, sequenceOrder: 3, status: "AFFECTED" },
  { id: 4, name: "外滩茂悦酒店", placeName: "上海外滩茂悦大酒店", latitude: 31.24, longitude: 121.49, nodeType: "LODGING", plannedStart: "2025-04-19T18:00:00", plannedEnd: "2025-04-20T09:00:00", cost: 760, sequenceOrder: 4, status: "PLANNED" },
];

const activeTrip: Trip = {
  id: 1,
  title: "上海春日漫游",
  status: "ONGOING",
  startDate: "2025-04-19",
  endDate: "2025-04-20",
  totalBudget: 2400,
  spentBudget: 1260,
  group,
  itineraryNodes: nodes,
  routes: [],
  riskScore: 68,
  riskLabel: "需要留意",
  destination: "上海 · 2天1夜",
  roomCode: "SH24-7K",
};

const guides: TravelGuide[] = [
  { id: 1, title: "上海春日漫游：从梧桐区走到黄浦江", city: "上海", days: 2, theme: "城市漫游", price: 980, rating: 4.9, reviews: 328, cover: "https://images.unsplash.com/photo-1548919973-5cef591cdbc9?auto=format&fit=crop&w=900&q=80", tags: ["Citywalk", "咖啡", "拍照"], author: { id: 7, name: "阿麦去远方", email: "amai@example.com", avatar: "https://i.pravatar.cc/100?img=32" }, saves: 1204, description: "把外滩、武康路和一顿本帮菜，放进一个松弛的周末。"},
  { id: 2, title: "成都不赶路：熊猫、茶馆和一场慢火锅", city: "成都", days: 3, theme: "美食探索", price: 1280, rating: 4.8, reviews: 512, cover: "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=900&q=80", tags: ["熊猫", "火锅", "慢生活"], author: { id: 8, name: "蓉城小满", email: "rong@example.com", avatar: "https://i.pravatar.cc/100?img=23" }, saves: 876, description: "不塞满景点，留出午后在人民公园喝茶的时间。"},
  { id: 3, title: "杭州西湖：一条不重复的环湖路线", city: "杭州", days: 2, theme: "自然风光", price: 860, rating: 4.7, reviews: 204, cover: "https://images.unsplash.com/photo-1538485399081-7c897a9a3d20?auto=format&fit=crop&w=900&q=80", tags: ["西湖", "骑行", "茶园"], author: { id: 9, name: "西湖边散步", email: "westlake@example.com", avatar: "https://i.pravatar.cc/100?img=12" }, saves: 633, description: "从北山街的清晨开始，把湖光山色交给脚步。"},
  { id: 4, title: "大理留白计划：苍山脚下的四天三夜", city: "大理", days: 4, theme: "疗愈放空", price: 1680, rating: 4.9, reviews: 187, cover: "https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=900&q=80", tags: ["苍山", "洱海", "民宿"], author: { id: 10, name: "白日放空", email: "dayoff@example.com", avatar: "https://i.pravatar.cc/100?img=5" }, saves: 924, description: "在海风、咖啡和日落之间，给自己留一点空白。"},
];

const events: ExternalEvent[] = [
  { id: 1, eventType: "WEATHER", title: "黄浦江沿线短时阵雨", description: "预计 14:00—17:00 有降雨，建议将室外节点顺延。", placeName: "黄浦江沿线", severity: "HIGH", startTime: "2025-04-19T14:00:00", endTime: "2025-04-19T17:00:00" },
  { id: 2, eventType: "LARGE_EVENT", title: "豫园周末人流上升", description: "现场客流较平日增加，入口排队约 40 分钟。", placeName: "豫园", severity: "MEDIUM", startTime: "2025-04-19T13:00:00", endTime: "2025-04-19T18:00:00" },
];

export const changeLogs = [{ id: 1, description: "将豫园调整至 4 月 20 日上午，替换为雨天路线", extraCost: 80, refundDeadline: "2025-04-20T18:00:00", plan: "最少延误", createdAt: "2025-04-19T09:05:00" }];

export const plans = [
  { id: 1, title: "轻量调整 · 把雨留在行程外", strategy: "MIN_EXTRA_COST", extraCost: 80, extraDelayMinutes: 30, changedNodeCount: 1, summary: "仅调整豫园时间，保留已确认的餐厅和住宿。", status: "PROPOSED", changes: [{ type: "RESCHEDULE", place: "豫园", note: "改至次日 09:30—12:00" }] },
  { id: 2, title: "少等一会儿 · 先去室内", strategy: "MIN_DELAY", extraCost: 160, extraDelayMinutes: 0, changedNodeCount: 2, summary: "用上海博物馆替换受影响节点，路线几乎不延误。", status: "VOTING", changes: [{ type: "REPLACE", place: "上海博物馆", note: "替换豫园" }, { type: "RESCHEDULE", place: "老吉士", note: "午餐顺延 30 分钟" }] },
  { id: 3, title: "少改一点 · 保留原来的上海", strategy: "MIN_CHANGE", extraCost: 0, extraDelayMinutes: 90, changedNodeCount: 1, summary: "等待雨势减弱后再出发，保留原计划中的所有地点。", status: "PROPOSED", changes: [{ type: "RESCHEDULE", place: "豫园", note: "下午 16:30 出发" }] },
];

export const mockDashboard: DashboardData = {
  user,
  trips: [activeTrip, { ...activeTrip, id: 2, title: "成都慢旅行", status: "PLANNED", destination: "成都 · 3天2夜", roomCode: "CD24-2M", riskScore: 12, riskLabel: "一路顺风", startDate: "2025-05-03", endDate: "2025-05-05" }],
  activeTrip,
  events,
  notifications: [
    { id: 1, title: "上海行程有新风险", detail: "豫园节点需要重新确认", time: "8分钟前", tone: "coral" },
    { id: 2, title: "小组成员已确认", detail: "周知远确认了晚餐安排", time: "1小时前", tone: "mint" },
    { id: 3, title: "新攻略适合你的偏好", detail: "大理留白计划获得 4.9 分", time: "昨天", tone: "sky" },
  ],
};

export { activeTrip, events, guides, user };
