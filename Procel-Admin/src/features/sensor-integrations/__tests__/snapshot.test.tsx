import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getIntegrationSnapshot } from "../../../api/sensorIntegrations";
import { adminSession, snapshot } from "../../../test/fixtures/sensorIntegrations";
import { SnapshotPage } from "../SnapshotPage";

vi.mock("../../../api/sensorIntegrations", () => ({
  getIntegrationSnapshot: vi.fn(),
}));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: adminSession }),
}));

describe("snapshot", () => {
  beforeEach(() => {
    vi.mocked(getIntegrationSnapshot).mockResolvedValue(snapshot);
  });

  it("renders readonly snapshot contract fields", async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SnapshotPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );
    expect(await screen.findByText("Snapshot de integracoes")).toBeInTheDocument();
    expect(screen.getByText("REST Energia")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE v1")).toBeInTheDocument();
    expect(screen.getByText("/messageId")).toBeInTheDocument();
    expect(screen.getByText("sensor-active")).toBeInTheDocument();
  });
});
