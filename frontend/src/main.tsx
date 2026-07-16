import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import "./index.css";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { isAuthed } from "./auth";
import { AppLayout } from "./layout/AppLayout";
import { AuthPage } from "./pages/AuthPage";
import { DashboardPage } from "./pages/DashboardPage";
import { GuidesPage } from "./pages/GuidesPage";
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
function RequireAuth({ children }: { children: React.ReactNode }) {
  return isAuthed() ? children : <Navigate to="/login" replace />;
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<AuthPage />} />
          <Route path="/register" element={<AuthPage register />} />
          <Route path="/" element={<RequireAuth><AppLayout><DashboardPage /></AppLayout></RequireAuth>} />
          <Route path="/guides" element={<RequireAuth><AppLayout><GuidesPage /></AppLayout></RequireAuth>} />
          <Route path="/guides/:id" element={<RequireAuth><AppLayout><GuideDetailPage /></AppLayout></RequireAuth>} />
          <Route path="/trips/:id" element={<RequireAuth><AppLayout><TripDetailPage /></AppLayout></RequireAuth>} />
          <Route path="/trips/:id/changelog" element={<RequireAuth><AppLayout><ChangelogPage /></AppLayout></RequireAuth>} />
          <Route path="/trips" element={<RequireAuth><AppLayout><TripsPage /></AppLayout></RequireAuth>} />
          <Route path="/trips/new" element={<RequireAuth><AppLayout><NewTripPage /></AppLayout></RequireAuth>} />
          <Route path="/routes" element={<RequireAuth><AppLayout><RouteMapPage /></AppLayout></RequireAuth>} />
          <Route path="/budget" element={<RequireAuth><AppLayout><BudgetPage /></AppLayout></RequireAuth>} />
          <Route path="/settlement" element={<RequireAuth><AppLayout><SettlementPage /></AppLayout></RequireAuth>} />
          <Route path="/groups" element={<RequireAuth><AppLayout><GroupsPage /></AppLayout></RequireAuth>} />
          <Route path="/groups/:id" element={<RequireAuth><AppLayout><GroupDetailPage /></AppLayout></RequireAuth>} />
          <Route path="/groups/:id/constraints/:memberId" element={<RequireAuth><AppLayout><ConstraintPage /></AppLayout></RequireAuth>} />
          <Route path="/friends" element={<RequireAuth><AppLayout><FriendsPage /></AppLayout></RequireAuth>} />
          <Route path="/events" element={<RequireAuth><AppLayout><EventsPage /></AppLayout></RequireAuth>} />
          <Route path="/impacts" element={<RequireAuth><AppLayout><ImpactsPage /></AppLayout></RequireAuth>} />
          <Route path="/plans" element={<RequireAuth><AppLayout><PlansPage /></AppLayout></RequireAuth>} />
          <Route path="/votes" element={<RequireAuth><AppLayout><VotesPage /></AppLayout></RequireAuth>} />
          <Route path="/discussions" element={<RequireAuth><AppLayout><DiscussionsPage /></AppLayout></RequireAuth>} />
          <Route path="/notifications" element={<RequireAuth><AppLayout><NotificationsPage /></AppLayout></RequireAuth>} />
          <Route path="/settings" element={<RequireAuth><AppLayout><SettingsPage /></AppLayout></RequireAuth>} />
          <Route path="/admin" element={<RequireAuth><AppLayout><AdminPage /></AppLayout></RequireAuth>} />
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
