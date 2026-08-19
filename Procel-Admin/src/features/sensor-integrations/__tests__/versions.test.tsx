import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { activateParserVersion, createParserVersion, updateParserVersion } from "../../../api/sensorIntegrations";
import { ApiError } from "../../../lib/api";
import { adminSession, activeVersion, draftVersion, inactiveVersion } from "../../../test/fixtures/sensorIntegrations";
import { ParserVersionEditor } from "../ParserVersionEditor";
import { VersionsPanel } from "../VersionsPanel";
import { validateParserRequest } from "../validation";

vi.mock("../../../api/sensorIntegrations", () => ({
  createParserVersion: vi.fn(),
  updateParserVersion: vi.fn(),
  activateParserVersion: vi.fn(),
}));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: adminSession }),
}));

function renderWithQuery(ui: React.ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("versions", () => {
  beforeEach(() => {
    vi.mocked(createParserVersion).mockResolvedValue(draftVersion);
    vi.mocked(updateParserVersion).mockResolvedValue(draftVersion);
    vi.mocked(activateParserVersion).mockResolvedValue(activeVersion);
  });

  it("sends optional empty fields as null", async () => {
    const user = userEvent.setup();
    const submit = vi.fn();
    render(<ParserVersionEditor pending={false} onSubmit={submit} />);
    const textboxes = screen.getAllByRole("textbox");
    await user.type(textboxes[textboxes.length - 2], "temp");
    await user.type(textboxes[textboxes.length - 1], "/value");
    await user.click(screen.getByText("Salvar versao"));

    expect(submit).toHaveBeenCalledWith(expect.objectContaining({
      sensorExternalIdPointer: null,
      sourceReceivedAtPointer: null,
    }));
  });

  it("accepts exactly 100 mappings and rejects 101", () => {
    const baseRequest = {
      sensorResolutionMode: "ROUTE_SENSOR" as const,
      messageIdPointer: "/messageId",
      sensorExternalIdPointer: null,
      timestampPointer: "/timestamp",
      sourceReceivedAtPointer: null,
      timestampFormat: "ISO_INSTANT" as const,
      valueMappings: Array.from({ length: 100 }, (_, index) => ({
        parameterName: `p${index}`,
        valuePointer: `/v${index}`,
        required: true,
      })),
    };

    expect(validateParserRequest(baseRequest)).toEqual([]);
    expect(validateParserRequest({
      ...baseRequest,
      valueMappings: [
        ...baseRequest.valueMappings,
        { parameterName: "p100", valuePointer: "/v100", required: true },
      ],
    })).toContain("Limite de 100 mappings excedido");
  });

  it("keeps ACTIVE and INACTIVE versions readonly", () => {
    render(<ParserVersionEditor version={activeVersion} pending={false} onSubmit={vi.fn()} />);
    expect(screen.queryByText("Salvar versao")).not.toBeInTheDocument();
    expect(screen.getByText("Versoes publicadas sao imutaveis.")).toBeInTheDocument();

    render(<ParserVersionEditor version={inactiveVersion} pending={false} onSubmit={vi.fn()} />);
    expect(screen.getAllByText("Versoes publicadas sao imutaveis.")).toHaveLength(2);
  });

  it("sends expectedActiveVersionId captured when confirmation opens", async () => {
    const user = userEvent.setup();
    renderWithQuery(<VersionsPanel profileId="profile-1" versions={[draftVersion, activeVersion]} />);
    await user.click(screen.getByLabelText("Ativar versao"));
    await user.click(within(screen.getByRole("dialog")).getByText("Ativar"));

    await waitFor(() => {
      expect(activateParserVersion).toHaveBeenCalledWith(
        "profile-1",
        "version-draft",
        { expectedActiveVersionId: "version-active" },
        adminSession,
      );
    });
  });

  it("sends null expectedActiveVersionId when there is no active version", async () => {
    const user = userEvent.setup();
    renderWithQuery(<VersionsPanel profileId="profile-1" versions={[draftVersion]} />);
    await user.click(screen.getByLabelText("Ativar versao"));
    await user.click(within(screen.getByRole("dialog")).getByText("Ativar"));

    await waitFor(() => {
      expect(activateParserVersion).toHaveBeenCalledWith(
        "profile-1",
        "version-draft",
        { expectedActiveVersionId: null },
        adminSession,
      );
    });
  });

  it("does not retry activation conflict and requires a new confirmation", async () => {
    const user = userEvent.setup();
    vi.mocked(activateParserVersion).mockRejectedValueOnce(
      new ApiError("Active parser version changed.", 409, "PARSER_ACTIVATION_CONFLICT"),
    );
    renderWithQuery(<VersionsPanel profileId="profile-1" versions={[draftVersion, activeVersion]} />);
    await user.click(screen.getByLabelText("Ativar versao"));
    await user.click(within(screen.getByRole("dialog")).getByText("Ativar"));

    await screen.findByText(/A versao ativa mudou/);
    expect(activateParserVersion).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
});
