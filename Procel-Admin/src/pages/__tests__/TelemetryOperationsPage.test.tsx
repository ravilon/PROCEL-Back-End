import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getTelemetryEvent,
  listTelemetryEvents,
  reprocessTelemetryEvent,
} from "../../api/telemetry";
import { TelemetryOperationsPage } from "../TelemetryOperationsPage";
import { adminSession } from "../../test/fixtures/sensorIntegrations";
import type { RawTelemetryEventPage, ReprocessTelemetryResponse } from "../../types/telemetry";

vi.mock("../../api/telemetry", () => ({
  getTelemetryEvent: vi.fn(),
  listTelemetryEvents: vi.fn(),
  reprocessTelemetryEvent: vi.fn(),
}));

vi.mock("../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: adminSession,
    logout: vi.fn(),
    hasAnyRole: (...roles: string[]) => adminSession.roles.some((role) => roles.includes(role)),
  }),
}));

const event = {
  id: "raw-1",
  producerId: "producer-a",
  source: "MQTT" as const,
  messageId: "msg-1",
  sensorId: "sensor-1",
  sourceTimestamp: "2026-08-19T12:00:00Z",
  receivedAt: "2026-08-19T12:01:00Z",
  payload: { value: 1 },
  payloadHash: "hash-1",
  status: "CANONICAL_FAILED" as const,
  processing: {
    attempts: 2,
    lastError: "PROFILE_NOT_FOUND",
    canonicalMeasurementId: "measurement-1",
    profileId: "profile-1",
    parserVersionId: "parser-1",
  },
  reprocessing: { count: 0 },
  reprocessAudit: [],
};

function page(totalPages = 2): RawTelemetryEventPage {
  return {
    content: [event],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <TelemetryOperationsPage />
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

describe("TelemetryOperationsPage", () => {
  beforeEach(() => {
    vi.mocked(listTelemetryEvents).mockResolvedValue(page());
    vi.mocked(getTelemetryEvent).mockResolvedValue(event);
    vi.mocked(reprocessTelemetryEvent).mockResolvedValue({
      id: "raw-1",
      status: "RECEIVED",
      previousStatus: "CANONICAL_FAILED",
      reprocessCount: 1,
      requestedBy: "admin",
      requestedAt: "2026-08-19T12:02:00Z",
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads a server-side page and requests the next page without client-side bulk loading", async () => {
    const { queryClient } = renderPage();

    expect(await screen.findByText("msg-1")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Proxima" }));

    await waitFor(() => {
      expect(listTelemetryEvents).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 20 }),
        adminSession,
      );
    });
    queryClient.clear();
  });

  it("shows readonly payload details and does not log the payload", async () => {
    const consoleSpy = vi.spyOn(console, "log").mockImplementation(() => undefined);
    const { queryClient } = renderPage();

    fireEvent.click(await screen.findByRole("button", { name: "Detalhes" }));

    expect(await screen.findByLabelText("Payload bruto readonly")).toHaveTextContent('"value": 1');
    expect(screen.getByText("PROFILE_NOT_FOUND")).toBeInTheDocument();
    expect(consoleSpy).not.toHaveBeenCalled();
    consoleSpy.mockRestore();
    queryClient.clear();
  });

  it("requires confirmation reason and prevents duplicate reprocess submission", async () => {
    const user = userEvent.setup();
    let resolveReprocess: ((value: ReprocessTelemetryResponse) => void) | undefined;
    vi.mocked(reprocessTelemetryEvent).mockImplementation(
      () => new Promise((resolve) => {
        resolveReprocess = resolve;
      }),
    );
    const { queryClient } = renderPage();

    await user.click(await screen.findByRole("button", { name: "Reprocessar" }));
    const dialog = await screen.findByRole("dialog", { name: "Reprocessar evento" });
    const dialogQueries = within(dialog);
    const confirm = dialogQueries.getByRole("button", { name: "Confirmar reprocessamento" });
    expect(confirm).toBeDisabled();

    await user.type(dialogQueries.getByRole("textbox", { name: /motivo/i }), "corrigir parser");
    expect(confirm).toBeEnabled();
    await user.dblClick(confirm);

    await waitFor(() => {
      expect(reprocessTelemetryEvent).toHaveBeenCalledTimes(1);
    });
    expect(reprocessTelemetryEvent).toHaveBeenCalledWith("raw-1", "corrigir parser", adminSession);
    resolveReprocess?.({
      id: "raw-1",
      status: "RECEIVED",
      previousStatus: "CANONICAL_FAILED",
      reprocessCount: 1,
      requestedBy: "admin",
      requestedAt: "2026-08-19T12:02:00Z",
    });
    queryClient.clear();
  });
});
