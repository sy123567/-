import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import "./index.css";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { AppLayout } from "./layout/AppLayout";
import { AuthPage } from "./pages/AuthPage";
import { DashboardPage } from "./pages/DashboardPage";
import { GuidesPage } from "./pages/GuidesPage";
import { PlaceholderPage } from "./pages/PlaceholderPage";
import { TripDetailPage } from "./pages/TripDetailPage";
import {
  AdminPage,
  ChangelogPage,
  ConstraintPage,
  DiscussionsPage,
  EventsPage,
  FriendsPage,
  GroupDetailPage,
  GroupsPage,
  GuideDetailPage,
  ImpactsPage,
  NewTripPage,
  NotificationsPage,
  PlansPage,
  RouteMapPage,
  BudgetPage,
  SettlementPage,
  SettingsPage,
  TripsPage,
  VotesPage,
} from "./pages/Pass2Pages";

const queryClient = new QueryClient();
const placeholders: [string, string][] = [
  ["/groups", "我的小组"],
  ["/friends", "好友与邀请"],
  ["/trips", "行程总览"],
  ["/events", "事件监测"],
  ["/impacts", "影响与风险"],
  ["/plans", "替代方案"],
  ["/votes", "投票中心"],
  ["/discussions", "讨论区"],
  ["/notifications", "通知"],
  ["/settings", "个人设置"],
  ["/trips/new", "新建行程"],
];

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage register />} />
          <Route path="/" element={<AppLayout><DashboardPage /></AppLayout>} />
          <Route path="/guides" element={<AppLayout><GuidesPage /></AppLayout>} />
          <Route path="/guides/:id" element={<AppLayout><GuideDetailPage /></AppLayout>} />
          <Route path="/trips/:id" element={<AppLayout><TripDetailPage /></AppLayout>} />
          <Route path="/trips/:id/changelog" element={<AppLayout><ChangelogPage /></AppLayout>} />
          <Route path="/trips" element={<AppLayout><TripsPage /></AppLayout>} />
          <Route path="/trips/new" element={<AppLayout><NewTripPage /></AppLayout>} />
          <Route path="/routes" element={<AppLayout><RouteMapPage /></AppLayout>} />
          <Route path="/budget" element={<AppLayout><BudgetPage /></AppLayout>} />
          <Route path="/settlement" element={<AppLayout><SettlementPage /></AppLayout>} />
          <Route path="/groups" element={<AppLayout><GroupsPage /></AppLayout>} />
          <Route path="/groups/:id" element={<AppLayout><GroupDetailPage /></AppLayout>} />
          <Route path="/groups/:id/constraints/:memberId" element={<AppLayout><ConstraintPage /></AppLayout>} />
          <Route path="/friends" element={<AppLayout><FriendsPage /></AppLayout>} />
          <Route path="/events" element={<AppLayout><EventsPage /></AppLayout>} />
          <Route path="/impacts" element={<AppLayout><ImpactsPage /></AppLayout>} />
          <Route path="/plans" element={<AppLayout><PlansPage /></AppLayout>} />
          <Route path="/votes" element={<AppLayout><VotesPage /></AppLayout>} />
          <Route path="/discussions" element={<AppLayout><DiscussionsPage /></AppLayout>} />
          <Route path="/notifications" element={<AppLayout><NotificationsPage /></AppLayout>} />
          <Route path="/settings" element={<AppLayout><SettingsPage /></AppLayout>} />
          <Route path="/admin" element={<AppLayout><AdminPage /></AppLayout>} />
          {placeholders.filter(([path]) => !["/groups", "/friends", "/trips", "/events", "/impacts", "/plans", "/votes", "/discussions", "/notifications", "/settings", "/trips/new"].includes(path)).map(([path, title]) => (
            <Route key={path} path={path} element={<AppLayout><PlaceholderPage title={title} /></AppLayout>} />
          ))}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>,
);
