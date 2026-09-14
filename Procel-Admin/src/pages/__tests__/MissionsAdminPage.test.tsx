import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import {
  getMissionEvent,
  getMissionOccurrence,
  getMissionWindow,
  getMissionWorkerStatus,
  listEvaluationRequests,
  listMissionEvents,
  listMissionOccurrences,
  listMissionWindows,
  listOccurrenceEvidence,
  listWindowEvidence,
  operateMissionWindow,
  runMissionWorker,
} from "../../api/missions";
import { apiRequest } from "../../lib/api";
import { adminSession } from "../../test/fixtures/sensorIntegrations";
import type {
  EventoDefinicao,
  EventDefinitionSummary,
  MissionOccurrence,
  MissionWindow,
  Missao,
  PageResponse,
  Role,
} from "../../types";
import { MissionsAdminPage } from "../MissionsAdminPage";

vi.mock("../../api/missions", () => ({
  getMissionEvent: vi.fn(),
  getMissionOccurrence: vi.fn(),
  getMissionWindow: vi.fn(),
  getMissionWorkerStatus: vi.fn(),
  listEvaluationRequests: vi.fn(),
  listMissionEvents: vi.fn(),
  listMissionOccurrences: vi.fn(),
  listMissionWindows: vi.fn(),
  listOccurrenceEvidence: vi.fn(),
  listWindowEvidence: vi.fn(),
  operateMissionWindow: vi.fn(),
  runMissionWorker: vi.fn(),
  updateOccurrenceStatus: vi.fn(),
}));

vi.mock("../../lib/api", () => {
  class ApiError extends Error {
    constructor(
      message: string,
      public readonly status: number,
      public readonly code?: string,
    ) {
      super(message);
    }
  }
  return {
    ApiError,
    apiBaseUrl: "http://localhost:8080",
    apiRequest: vi.fn(),
  };
});

let roles: Role[] = ["ADMIN"];

vi.mock("../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: { ...adminSession, roles },
    logout: vi.fn(),
    hasAnyRole: (...requestedRoles: string[]) => roles.some((role) => requestedRoles.includes(role)),
  }),
}));

const mission: Missao = {
  id: "mission-1",
  titulo: "Economia de energia",
  descricao: "Reduzir consumo",
  tipo: "Individual",
  value: 10,
  ativo: true,
  createdAt: "2026-09-14T10:00:00Z",
};

const event: EventDefinitionSummary = {
  id: "event-1",
  missaoId: "mission-1",
  missaoTitulo: "Economia de energia",
  nome: "AC inteligente",
  tipoDisparo: "MEDICAO_RECEBIDA",
  modoAvaliacao: "DURACAO",
  ativo: true,
  ordem: 1,
  createdAt: "2026-09-14T10:00:00Z",
};

const eventDetail: EventoDefinicao = {
  ...event,
  descricao: "Temperatura controlada",
  operadorLogico: "ALL",
  politicaAtribuicao: "ALUNOS_VINCULADOS",
  janelaSegundos: 1800,
  duracaoMinimaSegundos: 1800,
  quantidadeNecessaria: 1,
  cooldownSegundos: null,
  updatedAt: "2026-09-14T10:00:00Z",
  condicoes: [{
    id: "condition-1",
    eventoDefinicaoId: "event-1",
    parametroDefId: "param-1",
    parametroNome: "presence",
    parametroTipoSensor: "ac",
    parametroDataType: "BOOLEAN",
    operador: "EQ",
    valorBoolean: true,
    agregacao: "ULTIMO_VALOR",
    obrigatoria: true,
    ordem: 1,
    ativo: true,
    createdAt: "2026-09-14T10:00:00Z",
  }],
};

const windowRow: MissionWindow = {
  id: "window-1",
  eventoDefinicaoId: "event-1",
  eventoNome: "AC inteligente",
  compartimentoId: "room-1",
  status: "ABERTA",
  inicioEm: "2026-09-14T10:00:00Z",
  fimPrevistoEm: "2026-09-14T10:30:00Z",
  attempts: 0,
  chaveIdempotencia: "window-key",
  contextoSnapshot: "{\"aula\":\"teste\"}",
  createdAt: "2026-09-14T10:00:00Z",
};

const occurrence: MissionOccurrence = {
  id: "occurrence-1",
  eventoDefinicaoId: "event-1",
  eventoNome: "AC inteligente",
  compartimentoId: "room-1",
  sensorExternalId: "sensor-1",
  status: "CONFIRMADO",
  detectadoEm: "2026-09-14T10:30:00Z",
  chaveIdempotencia: "occurrence-key",
  conteudoFingerprint: "abc",
  contextoSnapshot: "{\"aula\":\"teste\"}",
  createdAt: "2026-09-14T10:30:00Z",
};

function page<T>(content: T[]): PageResponse<T> {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <MissionsAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

describe("MissionsAdminPage", () => {
  beforeEach(() => {
    roles = ["ADMIN"];
    vi.mocked(apiRequest).mockResolvedValue([mission]);
    vi.mocked(listMissionEvents).mockResolvedValue(page([event]));
    vi.mocked(getMissionEvent).mockResolvedValue(eventDetail);
    vi.mocked(listEvaluationRequests).mockResolvedValue(page([{
      id: "request-1",
      medicaoId: "measurement-1",
      status: "PENDING",
      attempts: 0,
      createdAt: "2026-09-14T10:00:00Z",
    }]));
    vi.mocked(listMissionWindows).mockResolvedValue(page([windowRow]));
    vi.mocked(getMissionWindow).mockResolvedValue(windowRow);
    vi.mocked(listWindowEvidence).mockResolvedValue(page([{
      id: "evidence-1",
      janelaId: "window-1",
      medicaoId: "measurement-1",
      parametroValorId: "value-1",
      papel: "INICIO",
      createdAt: "2026-09-14T10:00:00Z",
    }]));
    vi.mocked(listMissionOccurrences).mockResolvedValue(page([occurrence]));
    vi.mocked(getMissionOccurrence).mockResolvedValue(occurrence);
    vi.mocked(listOccurrenceEvidence).mockResolvedValue(page([{
      id: "occurrence-evidence-1",
      ocorrenciaId: "occurrence-1",
      medicaoId: "measurement-1",
      parametroValorId: "value-1",
      papel: "CONDICAO",
      createdAt: "2026-09-14T10:00:00Z",
    }]));
    vi.mocked(getMissionWorkerStatus).mockResolvedValue({
      evaluation: {
        enabled: false,
        fixedDelay: "PT30S",
        batchSize: 20,
        leaseDuration: "PT1M",
        maxAttempts: 3,
        backlog: 2,
      },
      temporalWindows: {
        enabled: false,
        fixedDelay: "PT30S",
        batchSize: 10,
        leaseDuration: "PT1M",
        maxAttempts: 3,
        backlog: 1,
      },
      drools: {
        selectedRuleEngine: "simple",
        temporalDroolsEnabled: false,
        temporalActivitiesEnabled: false,
        maxFactsPerEvaluation: 5000,
        maxCacheEntries: 200,
        cacheExpiration: "PT30M",
        evaluationTimeout: "PT5S",
        maximumSampleGap: "PT5M",
      },
    });
    vi.mocked(operateMissionWindow).mockResolvedValue({ ...windowRow, status: "INVALIDADA" });
    vi.mocked(runMissionWorker).mockResolvedValue({ worker: "evaluation", processed: 0 });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("preserva o catalogo de missoes existente", async () => {
    renderPage();

    expect(await screen.findByText("Economia de energia")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Criar missao" })).toBeInTheDocument();
  });

  it("lista eventos e abre condicoes do endpoint existente", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Eventos e condicoes" }));
    expect(await screen.findByText("AC inteligente")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ver condicoes de AC inteligente" }));

    expect(await screen.findByText("presence")).toBeInTheDocument();
    expect(getMissionEvent).toHaveBeenCalledWith("event-1", expect.objectContaining({ accessToken: "admin-token" }));
  });

  it("lista requests com filtros server-side", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Requests" }));
    await user.click(screen.getByRole("combobox", { name: "Status" }));
    await user.click(await screen.findByRole("option", { name: "PENDING" }));

    await waitFor(() => {
      expect(listEvaluationRequests).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: "PENDING", page: 0, size: 20 }),
        expect.anything(),
      );
    });
  });

  it("mostra janelas e evidencias", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Janelas" }));
    expect(await screen.findByText("AC inteligente")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ver evidencias da janela window-1" }));
    expect(await screen.findByText("INICIO")).toBeInTheDocument();
  });

  it("executa operacao manual de janela para admin", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Janelas" }));
    expect(await screen.findByText("AC inteligente")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Invalidar" }));
    const dialog = await screen.findByRole("dialog", { name: /invalidate/i });
    await user.type(within(dialog).getByRole("textbox", { name: "Motivo" }), "ajuste operacional");
    await user.click(within(dialog).getByRole("button", { name: "Confirmar" }));

    await waitFor(() => {
      expect(operateMissionWindow).toHaveBeenCalledWith(
        "window-1",
        "invalidate",
        { reason: "ajuste operacional" },
        expect.anything(),
      );
    });
  });

  it("lista ocorrencias e evidencias", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: "Ocorrencias" }));
    expect(await screen.findByText("sensor-1")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ver evidencias da ocorrencia occurrence-1" }));

    expect(await screen.findByText("CONDICAO")).toBeInTheDocument();
  });

  it("mostra status dos workers e bloqueia execucao para nao admin", async () => {
    roles = ["ANALISTA"];
    renderPage();
    fireEvent.click(screen.getByRole("tab", { name: "Workers" }));

    expect(await screen.findByText("Worker instantaneo")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Executar lote" })[0]).toBeDisabled();
  });

  it("bloqueia usuarios sem perfil administrativo de missoes", () => {
    roles = ["USUARIO"];
    renderPage();

    expect(screen.getByText("Acesso negado")).toBeInTheDocument();
    expect(listMissionEvents).not.toHaveBeenCalled();
  });
});
