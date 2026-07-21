export type Id = number;

export type TripStatus = "DRAFT" | "PLANNED" | "ONGOING" | "COMPLETED" | "CANCELLED";
export type NodeType = "ATTRACTION" | "MEAL" | "LODGING" | "TRANSPORT" | "OTHER";
export type NodeStatus = "PLANNED" | "CONFIRMED" | "AFFECTED" | "CANCELLED" | "REPLACED";
export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type EventType = "WEATHER" | "ROAD_WORK" | "TRAFFIC_CONTROL" | "ATTRACTION_CLOSURE" | "LARGE_EVENT";
export type ImpactLevel = "NONE" | "MINOR" | "MODERATE" | "SEVERE";

export interface User {
  id: Id;
  name: string;
  email: string;
  phone?: string;
  avatar?: string;
}

export interface TravelGroup {
  id: Id;
  name: string;
  description: string;
  ownerUser: User;
  members?: GroupMember[];
  memberCount?: number;
  roomCode: string;
}

export interface GroupMember {
  id: Id;
  user: User;
  role: "OWNER" | "MEMBER";
  constraint: MemberConstraint;
}

export interface MemberConstraint {
  availableFrom: string;
  availableTo: string;
  maxBudget: number;
  mustVisitPlaces: string[];
  fitnessLevel: "LOW" | "MEDIUM" | "HIGH";
  dietaryNeeds: string[];
  accessibilityNeeds: string[];
}

export interface ItineraryNode {
  id: Id;
  name: string;
  placeName: string;
  latitude: number;
  longitude: number;
  parentId?: Id | null;
  nodeType: NodeType;
  plannedStart: string;
  plannedEnd: string;
  cost: number;
  sequenceOrder: number;
  status: NodeStatus;
}

export interface NodeNote {
  id: Id;
  content: string;
  authorId: Id;
  authorName: string;
  createdAt: string;
}

export interface PlaceFood {
  name: string;
  desc: string;
}

export interface PlaceDetail {
  placeName: string;
  code?: string;
  category: string;
  intro: string;
  highlights: string[];
  foods: PlaceFood[];
  tips?: string;
  bestTime?: string;
  ticket?: string;
  image?: string;
}

export interface Route {
  id: Id;
  fromNodeId: Id;
  toNodeId: Id;
  transportMode: "WALK" | "DRIVE" | "TRANSIT" | "TRAIN" | "FLIGHT";
  distanceKm: number;
  durationMinutes: number;
  cost: number;
}

export interface Trip {
  id: Id;
  title: string;
  status: TripStatus;
  startDate: string;
  endDate: string;
  totalBudget: number;
  spentBudget?: number;
  group?: TravelGroup;
  itineraryNodes: ItineraryNode[];
  routes: Route[];
  riskScore?: number;
  riskLabel?: string;
  destination?: string;
  roomCode?: string;
}

export interface ExternalEvent {
  id: Id;
  eventType?: EventType;
  title?: string;
  description?: string;
  placeName?: string;
  severity?: Severity;
  startTime?: string;
  endTime?: string;
  tripId?: number;
  tripTitle?: string;
  tempMin?: number;
  tempMax?: number;
}

export interface ImpactAssessment {
  id: Id;
  event?: ExternalEvent;
  affectedNode?: ItineraryNode;
  riskScore?: number;
  impactLevel?: ImpactLevel;
  description?: string;
}

export interface AlternativePlan {
  id: Id;
  title?: string;
  strategy?: "MIN_EXTRA_COST" | "MIN_DELAY" | "MIN_CHANGE";
  extraCost?: number;
  extraDelayMinutes?: number;
  changedNodeCount?: number;
  summary?: string;
  status?: "PROPOSED" | "VOTING" | "ACCEPTED" | "REJECTED";
  proposedNodeChanges?: NodeChange[];
}

export interface NodeChange {
  id: Id;
  originalNode?: ItineraryNode;
  changeType?: "RESCHEDULE" | "REPLACE" | "REMOVE" | "ADD";
  newPlaceName?: string;
  newStart?: string;
  newEnd?: string;
  newCost?: number;
  note?: string;
}

export interface PlanVote {
  id: Id;
  memberName: string;
  choice: "APPROVE" | "REJECT" | "ABSTAIN";
}

export interface ChangeLog {
  id: Id;
  description?: string;
  extraCost?: number;
  refundDeadline?: string;
  createdAt?: string;
  relatedPlan?: AlternativePlan;
}

export interface TravelGuide {
  id: Id;
  title: string;
  city: string;
  days: number;
  theme: string;
  price: number;
  rating: number;
  reviews: number;
  cover: string;
  tags: string[];
  author: User;
  saves: number;
  description: string;
}

export interface DiscussionPost {
  id: Id;
  authorId: Id;
  authorName: string;
  body: string;
  createdAt: string;
  likes: number;
  likedByMe: boolean;
}

export interface NotificationItem {
  id: Id;
  type: string;
  title: string;
  detail: string;
  read: boolean;
  tripId?: Id;
  createdAt: string;
}

export interface DashboardData {
  user: User;
  trips: Trip[];
  activeTrip?: Trip;
  events: ExternalEvent[];
  notifications: { id: Id; title: string; detail: string; time: string; tone: "coral" | "mint" | "sky" }[];
}
