import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  activateIntegrationBinding,
  createIntegrationBinding,
  deactivateIntegrationBinding,
  searchCatalogSensors,
} from "../../../api/sensorIntegrations";
import { activeBinding, activeSensor, adminSession, inactiveBinding, inactiveSensor } from "../../../test/fixtures/sensorIntegrations";
import { BindingsPanel } from "../BindingsPanel";

vi.mock("../../../api/sensorIntegrations", () => ({
  searchCatalogSensors: vi.fn(),
  createIntegrationBinding: vi.fn(),
  activateIntegrationBinding: vi.fn(),
  deactivateIntegrationBinding: vi.fn(),
}));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: adminSession }),
}));

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <BindingsPanel profileId="profile-1" bindings={[activeBinding, inactiveBinding]} />
    </QueryClientProvider>,
  );
}

describe("bindings", () => {
  beforeEach(() => {
    vi.mocked(searchCatalogSensors).mockResolvedValue([activeSensor, inactiveSensor]);
    vi.mocked(createIntegrationBinding).mockResolvedValue(activeBinding);
    vi.mocked(activateIntegrationBinding).mockResolvedValue(activeBinding);
    vi.mocked(deactivateIntegrationBinding).mockResolvedValue(undefined);
  });

  it("shows inactive historical bindings", async () => {
    renderPanel();
    expect(await screen.findByText("Sensor historico")).toBeInTheDocument();
    expect(screen.getByText("sensor-old")).toBeInTheDocument();
  });

  it("offers only active sensors for new binding", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(screen.getByLabelText("Sensor ativo"));
    expect(await screen.findByText("Sensor ativo (sensor-active)")).toBeInTheDocument();
    expect(screen.queryByText("Sensor inativo (sensor-inactive)")).not.toBeInTheDocument();
  });

  it("creates and deactivates bindings without hiding history", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(screen.getByLabelText("Sensor ativo"));
    await user.click(await screen.findByText("Sensor ativo (sensor-active)"));
    await user.click(screen.getByText("Vincular"));
    await waitFor(() => {
      expect(createIntegrationBinding).toHaveBeenCalledWith(
        "profile-1",
        { sensorExternalId: "sensor-active" },
        adminSession,
      );
    });

    await user.click(screen.getAllByLabelText("Alterar binding")[0]);
    await user.click(within(screen.getByRole("dialog")).getByText("Desativar"));
    await waitFor(() => expect(deactivateIntegrationBinding).toHaveBeenCalledWith("binding-1", adminSession));
    expect(screen.getByText("Sensor historico")).toBeInTheDocument();
  });
});
