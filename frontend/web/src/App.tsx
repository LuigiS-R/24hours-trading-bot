import { BrowserRouter, Route, Routes } from "react-router-dom";
import { RequireAuth } from "./auth/RequireAuth";
import {
  ConnectionsPage,
  DashboardPage,
  LoginPage,
  NotFoundPage,
  OAuthFailurePage,
  OAuthSuccessPage,
  ProfilePage,
  WatchlistDetailPage,
} from "./pages/Pages";
const protectedPage = (page: React.ReactNode) => (
  <RequireAuth>{page}</RequireAuth>
);
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={protectedPage(<DashboardPage />)} />
        <Route
          path="/connections"
          element={protectedPage(<ConnectionsPage />)}
        />
        <Route
          path="/settings/connections/success"
          element={protectedPage(<OAuthSuccessPage />)}
        />
        <Route
          path="/settings/connections/failure"
          element={<OAuthFailurePage />}
        />
        <Route
          path="/watchlists/:watchlistId"
          element={protectedPage(<WatchlistDetailPage />)}
        />
        <Route path="/profile" element={protectedPage(<ProfilePage />)} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
