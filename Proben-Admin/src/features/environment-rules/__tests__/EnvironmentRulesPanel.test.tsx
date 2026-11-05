import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EnvironmentRulesPanel } from "../EnvironmentRulesPanel";
import { environmentRulesApi } from "../../../api/environmentRules";
import { listCompartimentos } from "../../../api/catalog";

let admin = true;
vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: { accessToken: "token" },
    hasAnyRole: () => admin,
  }),
}));
vi.mock("../../../api/catalog", () => ({
  listCompartimentos: vi.fn(),
  listCatalogSensors: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../../api/sensors", () => ({
  listSensorTypes: vi.fn().mockResolvedValue([]),
}));
vi.mock("../../../api/environmentRules", () => ({
  environmentRulesApi: {
    list: vi.fn(),
    resolve: vi.fn(),
    executions: vi.fn(),
    nextRuns: vi.fn(),
    setActive: vi.fn(),
    duplicate: vi.fn(),
    test: vi.fn(),
    execute: vi.fn(),
    deletePreview: vi.fn(),
    remove: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    copy: vi.fn(),
  },
}));

const room = {
  id: "ROOM-1",
  nome: "Sala 101",
  tipo: "Sala",
  predioId: "B1",
  predioNome: "Bloco A",
  campusNome: "Campus Centro",
  unidadeNome: "Unidade",
};
const rule = {
  id: "rule-1",
  nome: "Conforto 101",
  descricao: null,
  categoria: "AR_CONDICIONADO",
  ativo: true,
  padrao: false,
  escopo: "ROOM" as const,
  campusNome: null,
  compartimentoId: "ROOM-1",
  timezone: "America/Sao_Paulo",
  cronExpression: "0 */10 * * * *",
  diasSemana: [1, 2, 3, 4, 5, 6, 7],
  vigenciaInicio: null,
  vigenciaFim: null,
  politicaOcupacao: "IGNORE" as const,
  politicaBeneficiario: "NONE" as const,
  monitorarSemPontos: true,
  pontuacao: { satisfied: 10, notSatisfied: 0 },
  configuracao: { conditions: [] },
  versao: 2,
  createdAt: "2026-01-01T00:00:00Z",
  createdBy: "admin",
  updatedAt: "2026-01-01T00:00:00Z",
  updatedBy: "admin",
};

function renderPanel() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { retry: false, gcTime: Infinity },
    },
  });
  return render(
    <QueryClientProvider client={client}>
      <EnvironmentRulesPanel />
    </QueryClientProvider>,
  );
}

describe("EnvironmentRulesPanel", () => {
  beforeEach(() => {
    admin = true;
    vi.mocked(listCompartimentos).mockResolvedValue([room]);
    vi.mocked(environmentRulesApi.list).mockResolvedValue([rule]);
    vi.mocked(environmentRulesApi.resolve).mockResolvedValue([
      {
        compartimentoId: room.id,
        categoria: rule.categoria,
        specific: rule,
        campusDefault: {
          ...rule,
          id: "campus",
          nome: "Padrão campus",
          escopo: "CAMPUS",
          padrao: true,
          compartimentoId: null,
          campusNome: "Campus Centro",
        },
        globalDefault: {
          ...rule,
          id: "global",
          nome: "Padrão global",
          escopo: "GLOBAL",
          padrao: true,
          compartimentoId: null,
        },
        effective: rule,
        origem: "SPECIFIC",
      },
    ]);
    vi.mocked(environmentRulesApi.executions).mockResolvedValue([]);
    vi.mocked(environmentRulesApi.nextRuns).mockResolvedValue([]);
    vi.mocked(environmentRulesApi.test).mockResolvedValue({
      resultado: "SATISFIED",
      motivo: null,
      evidencias: {
        Ti: { value: 24, observedAt: "2026-11-04T00:00:00Z", valid: true },
        comfortBand: { lower: 20.5, upper: 27.5 },
      },
      contextoAcademico: { disciplina: "Conforto" },
      ocupacao: { occupied: true },
      beneficiarios: ["student-1"],
      pontosCalculados: 10,
    });
    vi.mocked(environmentRulesApi.execute).mockResolvedValue({} as never);
    vi.mocked(environmentRulesApi.deletePreview).mockResolvedValue({
      executionCount: 2,
      deletable: false,
      recommendation: "DEACTIVATE",
    });
  });

  it("mostra a cadeia de precedência e explica os fallbacks ignorados", async () => {
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Conforto 101");
    await user.click(
      screen
        .getAllByLabelText("Sala", { selector: "div", exact: true })
        .at(-1)!,
    );
    await user.click(
      await screen.findByRole("option", { name: "Sala 101 · Campus Centro" }),
    );
    expect(
      await screen.findByText("SPECIFIC: Conforto 101"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Campus: Padrão campus \(ignorada: existe regra específica\)/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /Global: Padrão global \(ignorada: nível mais específico disponível\)/,
      ),
    ).toBeInTheDocument();
  });

  it("exibe dry-run detalhado e avisa que nenhuma pontuação foi persistida", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(
      await screen.findByRole("button", { name: "Testar regra" }),
    );
    expect(
      await screen.findByText(/nenhuma execução ou pontuação foi persistida/),
    ).toBeInTheDocument();
    expect(screen.getByText(/Resultado: SATISFIED/)).toHaveTextContent("10");
    expect(
      screen.getByText("Medições, timestamps, validade e limites"),
    ).toBeInTheDocument();
    expect(environmentRulesApi.execute).not.toHaveBeenCalled();
  });

  it("distingue execução manual, abre histórico e bloqueia exclusão com alternativa de desativação", async () => {
    const user = userEvent.setup();
    renderPanel();
    await user.click(
      await screen.findByRole("button", { name: "Executar manualmente" }),
    );
    expect(
      await screen.findByText("Execução manual enfileirada."),
    ).toBeInTheDocument();
    expect(environmentRulesApi.execute).toHaveBeenCalledWith(
      "rule-1",
      "ROOM-1",
      expect.anything(),
    );

    fireEvent.click(screen.getByText("Conforto 101"));
    expect(
      await screen.findByText("Histórico · Conforto 101"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Fechar" }));
    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    await user.click(screen.getByRole("button", { name: "Excluir regra" }));
    expect(
      await screen.findByText(/Exclusão bloqueada: 2 execução/),
    ).toHaveTextContent("Desative a regra");
    expect(environmentRulesApi.remove).not.toHaveBeenCalled();
  });

  it("mantém consulta para roles permitidas e oculta mutações administrativas", async () => {
    admin = false;
    renderPanel();
    await screen.findByText("Conforto 101");
    expect(
      screen.queryByRole("button", { name: "Nova regra" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Executar manualmente" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Editar regra" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Testar regra" }),
    ).toBeInTheDocument();
  });

  it("renderiza estados de loading, vazio e erro", async () => {
    let release!: (value: (typeof rule)[]) => void;
    vi.mocked(environmentRulesApi.list).mockReturnValueOnce(
      new Promise((resolve) => {
        release = resolve;
      }),
    );
    const view = renderPanel();
    expect(screen.getByText("Carregando regras…")).toBeInTheDocument();
    release([]);
    expect(
      await screen.findByText("Nenhuma regra encontrada."),
    ).toBeInTheDocument();
    view.unmount();

    vi.mocked(environmentRulesApi.list).mockRejectedValueOnce(
      new Error("offline"),
    );
    renderPanel();
    expect(
      await screen.findByText("Não foi possível carregar as regras."),
    ).toBeInTheDocument();
  });
});
