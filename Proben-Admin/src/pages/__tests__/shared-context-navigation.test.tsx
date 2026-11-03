import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Navigate, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { IntegrationsAdminPage } from "../../features/sensor-integrations/IntegrationsAdminPage";
import { SensorsAdminPage } from "../SensorsAdminPage";

vi.mock("../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: { accessToken: "token", roles: ["ADMIN"] },
    logout: vi.fn(),
    hasAnyRole: () => true,
  }),
}));
vi.mock("../../api/sensorIntegrations", () => ({
  listIntegrationProfiles: vi.fn(async () => []),
  createIntegrationProfile: vi.fn(),
  activateIntegrationProfile: vi.fn(),
  deactivateIntegrationProfile: vi.fn(),
}));
vi.mock("../../features/shared-contexts/SharedContextsPanel", () => ({
  SharedContextsPanel: () => <div>Painel de contextos compartilhados</div>,
}));

describe("navegação administrativa compartilhada", () => {
  it("navega de Integrações até Coordenadas dos campi", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={["/integracoes"]}>
          <Routes>
            <Route path="/integracoes" element={<IntegrationsAdminPage />} />
            <Route
              path="/integracoes/coordenadas-campi"
              element={<div>Tela de coordenadas</div>}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
    await user.click(
      screen.getByRole("link", { name: "Coordenadas dos campi" }),
    );
    expect(screen.getByText("Tela de coordenadas")).toBeInTheDocument();
  });

  it("mantém Contextos compartilhados como aba ativa refletida na URL", () => {
    render(
      <MemoryRouter initialEntries={["/sensores/contextos-compartilhados"]}>
        <Routes>
          <Route
            path="/sensores/contextos-compartilhados"
            element={<SensorsAdminPage />}
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("tab", { name: "Contextos compartilhados" }),
    ).toHaveAttribute("aria-selected", "true");
    expect(
      screen.getByText("Painel de contextos compartilhados"),
    ).toBeInTheDocument();
  });

  it("redireciona a rota antiga sem duplicar a tela", () => {
    render(
      <MemoryRouter initialEntries={["/contextos-compartilhados"]}>
        <Routes>
          <Route
            path="/contextos-compartilhados"
            element={<LegacyRedirect />}
          />
          <Route
            path="/sensores/contextos-compartilhados"
            element={<div>Destino novo de contextos</div>}
          />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText("Destino novo de contextos")).toBeInTheDocument();
  });
});

function LegacyRedirect() {
  return <Navigate to="/sensores/contextos-compartilhados" replace />;
}
