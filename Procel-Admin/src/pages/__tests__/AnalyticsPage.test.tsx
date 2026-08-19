import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { listNumericBuckets, getNumericBucketSummary } from "../../api/analytics";
import { listCompartimentos } from "../../api/catalog";
import { searchCatalogSensors } from "../../api/sensorIntegrations";
import { listSensorTypes } from "../../api/sensors";
import { ProtectedRoute } from "../../auth/ProtectedRoute";
import { AppLayout } from "../../components/AppLayout";
import { AccessDeniedPage } from "../AccessDeniedPage";
import { AnalyticsPage } from "../AnalyticsPage";
import type { Session } from "../../types";

vi.mock("@mui/x-charts/LineChart", () => ({
  LineChart: ({ series }: { series: { label: string; data: Array<number | null> }[] }) => (
    <div role="img" aria-label="Serie temporal dos buckets">
      {series.map((item) => (
        <div key={item.label}>
          <span>{item.label}</span>
          <span>{item.data.filter((value) => value !== null).join(", ")}</span>
        </div>
      ))}
    </div>
  ),
}));

vi.mock("../../api/analytics", () => ({
  listNumericBuckets: vi.fn(),
  getNumericBucketSummary: vi.fn(),
}));
vi.mock("../../api/catalog", () => ({ listCompartimentos: vi.fn() }));
vi.mock("../../api/sensorIntegrations", () => ({ searchCatalogSensors: vi.fn() }));
vi.mock("../../api/sensors", () => ({ listSensorTypes: vi.fn() }));

let currentSession: Session | null;
const logout = vi.fn();

vi.mock("../../auth/AuthContext", () => ({
  useAuth: () => ({
    session: currentSession,
    logout,
    hasAnyRole: (...roles: string[]) =>
      Boolean(currentSession?.roles.some((role) => roles.includes(role))),
  }),
}));

const adminSession: Session = {
  accessToken: "admin-token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  userId: "admin",
  email: "admin@example.com",
  roles: ["ADMIN"],
};

const operadorSession: Session = { ...adminSession, userId: "operador", roles: ["OPERADOR"] };
const analistaSession: Session = { ...adminSession, userId: "analista", roles: ["ANALISTA"] };
const usuarioSession: Session = { ...adminSession, userId: "usuario", roles: ["USUARIO"] };
const ingestorSession: Session = { ...adminSession, userId: "ingestor", roles: ["INGESTOR"] };

const sensors = [
  {
    externalId: "sensor-active",
    nome: "Sensor ativo",
    tipoNome: "energia",
    compartimentoId: "room-1",
    compartimentoNome: "Sala 1",
    ativo: true,
  },
  {
    externalId: "sensor-2",
    nome: "Sensor secundario",
    tipoNome: "energia",
    compartimentoId: "room-2",
    compartimentoNome: "Sala 2",
    ativo: true,
  },
];

const sensorTypes = [
  {
    nome: "energia",
    parametros: [
      {
        id: "param-temp",
        tipoNome: "energia",
        nome: "Temperatura",
        dataType: "NUMERIC" as const,
        numericUnit: "C",
        ativo: true,
      },
      {
        id: "param-umid",
        tipoNome: "energia",
        nome: "Umidade",
        dataType: "NUMERIC" as const,
        numericUnit: "%",
        ativo: true,
      },
    ],
  },
];

const rooms = [
  {
    id: "room-1",
    nome: "Sala 1",
    tipo: "SALA",
    predioId: "predio-1",
    predioNome: "Predio 1",
    campusNome: "Campus",
    unidadeNome: "Unidade",
  },
];

const bucketPage = {
  content: [
    {
      sensorExternalId: "sensor-active",
      sensorNome: "Sensor ativo",
      parametroDefId: "param-temp",
      parametroNome: "Temperatura",
      unidade: "C",
      compartimentoId: "room-1",
      bucketStart: "2026-08-19T08:00:00Z",
      bucketEnd: "2026-08-19T09:00:00Z",
      averageValue: 30,
      minimumValue: 20,
      maximumValue: 40,
      sampleCount: 10,
      aggregationVersion: 1,
    },
    {
      sensorExternalId: "sensor-2",
      sensorNome: "Sensor secundario",
      parametroDefId: "param-umid",
      parametroNome: "Umidade",
      unidade: "%",
      compartimentoId: "room-2",
      bucketStart: "2026-08-19T08:00:00Z",
      bucketEnd: "2026-08-19T09:00:00Z",
      averageValue: 70,
      minimumValue: 60,
      maximumValue: 80,
      sampleCount: 6,
      aggregationVersion: 1,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 22,
  totalPages: 2,
};

const summary = [
  {
    sensorExternalId: "sensor-active",
    sensorNome: "Sensor ativo",
    parametroDefId: "param-temp",
    parametroNome: "Temperatura",
    unidade: "C",
    compartimentoId: "room-1",
    from: "2026-08-19T00:00:00Z",
    to: "2026-08-19T12:00:00Z",
    averageValue: 30,
    minimumValue: 20,
    maximumValue: 40,
    sampleCount: 10,
    aggregationVersion: 1,
    bucketCount: 2,
  },
];

function mockSuccessfulApis() {
  vi.mocked(searchCatalogSensors).mockResolvedValue(sensors);
  vi.mocked(listSensorTypes).mockResolvedValue(sensorTypes);
  vi.mocked(listCompartimentos).mockResolvedValue(rooms);
  vi.mocked(listNumericBuckets).mockResolvedValue(bucketPage);
  vi.mocked(getNumericBucketSummary).mockResolvedValue(summary);
}

function renderAnalytics(session: Session | null = adminSession, initialPath = "/analiticos") {
  currentSession = session;
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    queryClient,
    ...render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route element={<ProtectedRoute allowedRoles={["ADMIN", "OPERADOR", "ANALISTA"]} />}>
            <Route element={<AppLayout />}>
              <Route path="/analiticos" element={<AnalyticsPage />} />
            </Route>
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/catalogo" element={<div>Catalogo page</div>} />
            </Route>
          </Route>
          <Route path="/negado" element={<AccessDeniedPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
    ),
  };
}

async function selectAutocomplete(label: string, option: RegExp) {
  const user = userEvent.setup();
  await user.click(screen.getByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}

describe("AnalyticsPage", () => {
  beforeEach(() => {
    mockSuccessfulApis();
  });

  it.each([
    ["ADMIN", adminSession],
    ["OPERADOR", operadorSession],
    ["ANALISTA", analistaSession],
  ])("permite a rota /analiticos para %s", async (_, session) => {
    renderAnalytics(session);
    expect(await screen.findByRole("heading", { name: "Análises" })).toBeInTheDocument();
    expect(screen.getAllByText("Análises").length).toBeGreaterThan(0);
  });

  it.each([
    ["USUARIO", usuarioSession],
    ["INGESTOR", ingestorSession],
  ])("bloqueia rota e menu para %s", async (_, session) => {
    renderAnalytics(session);
    expect(await screen.findByText("Acesso negado")).toBeInTheDocument();
    expect(screen.queryAllByText("Análises")).toHaveLength(0);
  });

  it("mantem rotas existentes sem regressao", async () => {
    renderAnalytics(usuarioSession, "/catalogo");
    expect(await screen.findByText("Catalogo page")).toBeInTheDocument();
  });

  it("preenche periodo padrao das ultimas 24 horas", async () => {
    renderAnalytics();
    const from = screen.getByLabelText("Inicio") as HTMLInputElement;
    const to = screen.getByLabelText("Fim") as HTMLInputElement;
    await waitFor(() => expect(listNumericBuckets).toHaveBeenCalled());
    const diff = new Date(to.value).getTime() - new Date(from.value).getTime();
    expect(diff).toBe(24 * 60 * 60 * 1000);
  });

  it("valida from anterior a to antes de consultar", async () => {
    const user = userEvent.setup();
    renderAnalytics();
    await screen.findByRole("heading", { name: "Análises" });
    vi.mocked(listNumericBuckets).mockClear();
    await user.clear(screen.getByLabelText("Inicio"));
    await user.type(screen.getByLabelText("Inicio"), "2026-08-19T13:00");
    await user.clear(screen.getByLabelText("Fim"));
    await user.type(screen.getByLabelText("Fim"), "2026-08-19T12:00");
    expect(screen.getByRole("button", { name: /Aplicar filtros/i })).toBeDisabled();
    expect(screen.getAllByText("A data inicial deve ser anterior a data final.").length).toBe(2);
    expect(listNumericBuckets).not.toHaveBeenCalled();
  });

  it("aplica filtros, nao envia vazios e retorna para pagina zero", async () => {
    const user = userEvent.setup();
    renderAnalytics();
    await screen.findAllByText("Sensor ativo");
    await selectAutocomplete("Sensor", /Sensor ativo/);
    await selectAutocomplete("Parametro", /Temperatura/);
    await selectAutocomplete("Compartimento", /Sala 1/);
    await user.type(screen.getByLabelText("Versao de agregacao"), "1");
    await user.click(screen.getByRole("button", { name: /Aplicar filtros/i }));

    await waitFor(() =>
      expect(listNumericBuckets).toHaveBeenLastCalledWith(
        expect.objectContaining({
          sensorExternalId: "sensor-active",
          parametroDefId: "param-temp",
          compartimentoId: "room-1",
          aggregationVersion: 1,
          page: 0,
        }),
        adminSession,
      ),
    );
    expect(getNumericBucketSummary).toHaveBeenLastCalledWith(
      expect.not.objectContaining({ page: expect.anything(), size: expect.anything() }),
      adminSession,
    );
  }, 10_000);

  it("limpa filtros opcionais", async () => {
    const user = userEvent.setup();
    renderAnalytics();
    await screen.findAllByText("Sensor ativo");
    await selectAutocomplete("Sensor", /Sensor ativo/);
    await user.click(screen.getByRole("button", { name: /Limpar filtros opcionais/i }));
    await waitFor(() =>
      expect(listNumericBuckets).toHaveBeenLastCalledWith(
        expect.not.objectContaining({ sensorExternalId: "sensor-active" }),
        adminSession,
      ),
    );
  });

  it("mostra loading, erro e vazio", async () => {
    vi.mocked(listNumericBuckets).mockImplementation(() => new Promise(() => undefined));
    const { unmount } = renderAnalytics();
    expect(await screen.findByText("Carregando analise...")).toBeInTheDocument();
    unmount();

    vi.mocked(listNumericBuckets).mockRejectedValue(new Error("falha"));
    renderAnalytics();
    expect(await screen.findByText("falha")).toBeInTheDocument();
  });

  it("mostra resultado vazio para filtros incompatíveis existentes", async () => {
    vi.mocked(listNumericBuckets).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    vi.mocked(getNumericBucketSummary).mockResolvedValue([]);
    renderAnalytics();
    expect(await screen.findByText("Nenhum bucket encontrado")).toBeInTheDocument();
  });

  it("renderiza cards usando a media enviada pelo backend", async () => {
    renderAnalytics();
    expect(await screen.findByText("2 buckets")).toBeInTheDocument();
    expect(screen.getAllByText((content) => content.includes("30 C")).length).toBeGreaterThan(0);
    expect(screen.getByText("10 amostras")).toBeInTheDocument();
  });

  it("renderiza tabela, unidade, representacao acessivel do grafico e multiplas series", async () => {
    renderAnalytics();
    expect(await screen.findByRole("img", { name: "Serie temporal dos buckets" })).toBeInTheDocument();
    expect(screen.getByText("Sensor ativo / Temperatura / v1")).toBeInTheDocument();
    expect(screen.getByText("Sensor secundario / Umidade / v1")).toBeInTheDocument();
    const table = screen.getByRole("table", { name: "Tabela de buckets analiticos" });
    expect(within(table).getByText("Temperatura")).toBeInTheDocument();
    expect(within(table).getByText("Umidade")).toBeInTheDocument();
    expect(within(table).getByText("C")).toBeInTheDocument();
    expect(screen.getByText(/Tooltip: inicio/)).toBeInTheDocument();
  });

  it("usa paginacao server-side e preserva filtros ao trocar de pagina", async () => {
    const user = userEvent.setup();
    renderAnalytics();
    await screen.findByText(/Pagina 1 de 2/);
    await user.click(screen.getByRole("button", { name: /Proxima/i }));
    await waitFor(() =>
      expect(listNumericBuckets).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1, size: 20 }),
        adminSession,
      ),
    );
  });

  it("indica pagina alem do total", async () => {
    vi.mocked(listNumericBuckets).mockResolvedValue({
      content: [],
      page: 5,
      size: 20,
      totalElements: 22,
      totalPages: 2,
    });
    renderAnalytics();
    expect(await screen.findByText(/Esta pagina nao possui buckets/)).toBeInTheDocument();
  });

  it("usa query keys diferentes para filtros diferentes", async () => {
    const user = userEvent.setup();
    const { queryClient } = renderAnalytics();
    await screen.findAllByText("Sensor ativo");
    const before = queryClient.getQueryCache().findAll({ queryKey: ["analytics", "numeric-buckets"] }).length;
    await selectAutocomplete("Parametro", /Temperatura/);
    await user.click(screen.getByRole("button", { name: /Aplicar filtros/i }));
    await waitFor(() => expect(listNumericBuckets).toHaveBeenCalledTimes(2));
    const after = queryClient.getQueryCache().findAll({ queryKey: ["analytics", "numeric-buckets"] }).length;
    expect(after).toBeGreaterThan(before);
  });
});
