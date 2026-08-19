import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ProtectedRoute } from "../../../auth/ProtectedRoute";
import { AppLayout } from "../../../components/AppLayout";
import { AccessDeniedPage } from "../../../pages/AccessDeniedPage";
import { adminSession, userSession } from "../../../test/fixtures/sensorIntegrations";

let currentSession: typeof adminSession | typeof userSession | null = adminSession;
const logout = vi.fn();

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: currentSession,
    logout,
    hasAnyRole: (...roles: string[]) =>
      Boolean(currentSession?.roles.some((role) => roles.includes(role))),
  }),
}));

function renderRoutes(initialPath = "/integracoes", allowedRoles?: ["ADMIN"]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route element={<ProtectedRoute allowedRoles={allowedRoles} />}>
            <Route element={<AppLayout />}>
              <Route path="/integracoes" element={<div>Integracoes page</div>} />
              <Route path="/telemetria" element={<div>Telemetria page</div>} />
              <Route path="/catalogo" element={<div>Catalogo page</div>} />
            </Route>
          </Route>
          <Route path="/negado" element={<AccessDeniedPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("integration routes", () => {
  it("shows integration drawer item only for ADMIN", () => {
    currentSession = adminSession;
    renderRoutes("/integracoes", ["ADMIN"]);
    expect(screen.getAllByText("Integracoes").length).toBeGreaterThan(0);

    cleanup();
    currentSession = userSession;
    renderRoutes("/integracoes", ["ADMIN"]);
    expect(screen.queryAllByText("Integracoes")).toHaveLength(0);
  });

  it("shows telemetry drawer item only for ADMIN", () => {
    currentSession = adminSession;
    renderRoutes("/telemetria", ["ADMIN"]);
    expect(screen.getAllByText("Telemetria").length).toBeGreaterThan(0);

    cleanup();
    currentSession = userSession;
    renderRoutes("/telemetria", ["ADMIN"]);
    expect(screen.queryAllByText("Telemetria")).toHaveLength(0);
  });

  it("redirects anonymous users to login", () => {
    currentSession = null;
    renderRoutes("/integracoes", ["ADMIN"]);
    expect(screen.getByText("Login page")).toBeInTheDocument();
  });

  it("blocks direct URL access for authenticated non-admin users", () => {
    currentSession = userSession;
    renderRoutes("/integracoes", ["ADMIN"]);
    expect(screen.getByText("Acesso negado")).toBeInTheDocument();
    expect(logout).not.toHaveBeenCalled();
  });

  it("keeps existing routes without allowedRoles session-only", () => {
    currentSession = userSession;
    renderRoutes("/catalogo");
    expect(screen.getByText("Catalogo page")).toBeInTheDocument();
  });
});
