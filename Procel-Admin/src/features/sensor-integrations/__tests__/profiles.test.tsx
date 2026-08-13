import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  activateIntegrationProfile,
  createIntegrationProfile,
  deactivateIntegrationProfile,
  listIntegrationProfiles,
  listParserVersions,
} from "../../../api/sensorIntegrations";
import { IntegrationsAdminPage } from "../IntegrationsAdminPage";
import { ProfileForm } from "../ProfileForm";
import { activeVersion, adminSession, inactiveProfile, profile } from "../../../test/fixtures/sensorIntegrations";

vi.mock("../../../api/sensorIntegrations", () => ({
  listIntegrationProfiles: vi.fn(),
  createIntegrationProfile: vi.fn(),
  activateIntegrationProfile: vi.fn(),
  deactivateIntegrationProfile: vi.fn(),
  listParserVersions: vi.fn(),
}));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: adminSession }),
}));

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <IntegrationsAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("profiles", () => {
  beforeEach(() => {
    vi.mocked(listIntegrationProfiles).mockResolvedValue([inactiveProfile, profile]);
    vi.mocked(createIntegrationProfile).mockResolvedValue(profile);
    vi.mocked(activateIntegrationProfile).mockResolvedValue(profile);
    vi.mocked(deactivateIntegrationProfile).mockResolvedValue(undefined);
  });

  it("lists profiles without loading versions per row", async () => {
    renderPage();
    expect(await screen.findByText("REST Energia")).toBeInTheDocument();
    expect(screen.getByText("MQTT legado")).toBeInTheDocument();
    expect(listIntegrationProfiles).toHaveBeenCalledWith(true, adminSession);
    expect(listParserVersions).not.toHaveBeenCalled();
  });

  it("filters and searches profiles client-side", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("REST Energia");

    await user.type(screen.getByLabelText("Buscar"), "mqtt");
    expect(screen.queryByText("REST Energia")).not.toBeInTheDocument();
    expect(screen.getByText("MQTT legado")).toBeInTheDocument();

    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(screen.getByText("Ativos"));
    expect(screen.getByText("Nenhum perfil encontrado.")).toBeInTheDocument();
  });

  it("creates a profile", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText("Novo perfil"));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: /Nome/ }), "Novo");
    await user.click(within(dialog).getByText("Salvar"));

    await waitFor(() => {
      expect(createIntegrationProfile).toHaveBeenCalledWith(
        { nome: "Novo", descricao: null, source: "REST" },
        adminSession,
      );
    });
  });

  it("renders source readonly when a published version exists", () => {
    const submit = vi.fn();
    render(
      <ProfileForm
        profile={profile}
        sourceReadonly={Boolean(activeVersion)}
        pending={false}
        onSubmit={submit}
      />,
    );
    expect(screen.getByRole("combobox", { name: "Source" })).toHaveAttribute("aria-disabled", "true");
  });
});
