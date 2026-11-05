import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { EnvironmentRuleDialog } from "../EnvironmentRuleDialog";
import { environmentRulesApi } from "../../../api/environmentRules";
import { listCatalogSensors } from "../../../api/catalog";
import { listSensorTypes } from "../../../api/sensors";

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: { accessToken: "token" } }),
}));
vi.mock("../../../api/catalog", () => ({
  listCatalogSensors: vi.fn(),
}));
vi.mock("../../../api/sensors", () => ({
  listSensorTypes: vi.fn(),
}));
vi.mock("../../../api/environmentRules", () => ({
  environmentRulesApi: {
    create: vi.fn(),
    update: vi.fn(),
    copy: vi.fn(),
  },
}));

const rooms = [
  {
    id: "ROOM-1",
    nome: "Sala 101",
    tipo: "Sala",
    predioId: "B1",
    predioNome: "Bloco A",
    campusNome: "Campus Centro",
    unidadeNome: "Unidade",
  },
  {
    id: "ROOM-2",
    nome: "Sala 202",
    tipo: "Sala",
    predioId: "B2",
    predioNome: "Bloco B",
    campusNome: "Campus Norte",
    unidadeNome: "Unidade",
  },
];

function renderDialog() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: Infinity },
      mutations: { retry: false, gcTime: Infinity },
    },
  });
  const rendered = render(
    <QueryClientProvider client={client}>
      <EnvironmentRuleDialog
        open
        rule={null}
        rooms={rooms}
        campuses={["Campus Centro", "Campus Norte"]}
        existingRules={[]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />
    </QueryClientProvider>,
  );
  return { ...rendered, client };
}

async function choose(
  user: ReturnType<typeof userEvent.setup>,
  label: string,
  option: string,
) {
  await user.click(screen.getByLabelText(label));
  await user.click(await screen.findByRole("option", { name: option }));
}

describe("EnvironmentRuleDialog", () => {
  beforeEach(() => {
    vi.mocked(listCatalogSensors).mockResolvedValue([
      {
        externalId: "TEMP-1",
        nome: "Temperatura interna",
        tipoNome: "TERMOMETRO",
        compartimentoId: "ROOM-1",
        ativo: true,
      },
      {
        externalId: "WEATHER-1",
        nome: "Temperatura externa",
        tipoNome: "METEOROLOGICO",
        contextoCompartilhadoId: "CTX-1",
        contextoCompartilhadoNome: "Clima do campus",
        ativo: true,
      },
    ]);
    vi.mocked(listSensorTypes).mockResolvedValue([
      {
        nome: "TERMOMETRO",
        parametros: [
          {
            id: "11111111-1111-1111-1111-111111111111",
            tipoNome: "TERMOMETRO",
            nome: "Temperatura",
            dataType: "NUMERIC",
            numericUnit: "°C",
            ativo: true,
          },
        ],
      },
      {
        nome: "METEOROLOGICO",
        parametros: [
          {
            id: "22222222-2222-2222-2222-222222222222",
            tipoNome: "METEOROLOGICO",
            nome: "Temperatura externa",
            dataType: "NUMERIC",
            numericUnit: "°C",
            ativo: true,
          },
        ],
      },
    ]);
    vi.mocked(environmentRulesApi.create).mockResolvedValue({} as never);
  });

  it("cria regra específica por controles visuais e gera o JSON declarativo", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.type(screen.getByLabelText(/Nome/), "Conforto sala 101");
    await choose(user, "Campus", "Campus Centro");
    await choose(user, "Prédio", "Bloco A");
    await choose(user, "Sala", "Sala 101");
    await user.click(
      screen.getByRole("button", { name: "Adicionar condição" }),
    );
    await choose(user, "Sensor", "Temperatura interna · Local");
    await choose(user, "Parâmetro", "Temperatura (°C)");
    await user.type(screen.getByLabelText("Valor ou referência"), "24");

    const json = screen.getByLabelText("JSON declarativo resultante");
    expect(json).toHaveTextContent('"sensorExternalId": "TEMP-1"');
    expect(json).toHaveTextContent(
      '"parametroDefId": "11111111-1111-1111-1111-111111111111"',
    );
    expect(json).toHaveTextContent('"maxAgeSeconds": 600');
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() =>
      expect(environmentRulesApi.create).toHaveBeenCalledTimes(1),
    );
    const input = vi.mocked(environmentRulesApi.create).mock.calls[0][0];
    expect(input).toMatchObject({
      escopo: "ROOM",
      compartimentoId: "ROOM-1",
      padrao: false,
    });
    expect(input.configuracao).toMatchObject({ combination: "AND" });
  });

  it.each([
    [
      "CAMPUS",
      "Campus Centro",
      {
        escopo: "CAMPUS",
        campusNome: "Campus Centro",
        compartimentoId: null,
        padrao: true,
      },
    ],
    [
      "GLOBAL",
      null,
      {
        escopo: "GLOBAL",
        campusNome: null,
        compartimentoId: null,
        padrao: true,
      },
    ],
  ] as const)(
    "cria padrão %s e limpa campos incompatíveis",
    async (scope, campus, expected) => {
      const user = userEvent.setup();
      renderDialog();
      await user.type(screen.getByLabelText(/Nome/), `Padrão ${scope}`);
      await choose(user, "Campus", "Campus Centro");
      await choose(user, "Prédio", "Bloco A");
      await choose(user, "Sala", "Sala 101");
      await choose(user, "Escopo", scope);
      if (campus) await choose(user, "Campus", campus);
      expect(screen.queryByLabelText("Sala")).not.toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "Salvar" }));
      await waitFor(() =>
        expect(environmentRulesApi.create).toHaveBeenCalled(),
      );
      expect(
        vi.mocked(environmentRulesApi.create).mock.calls.at(-1)?.[0],
      ).toMatchObject(expected);
    },
  );

  it("adiciona e remove condições, identifica fonte compartilhada e valida validade", async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(
      screen.getByRole("button", { name: "Adicionar condição" }),
    );
    await choose(user, "Sensor", "Temperatura externa · Compartilhada");
    expect(screen.getByDisplayValue("Compartilhada")).toHaveAttribute(
      "readonly",
    );
    expect(screen.getByText("Média 7 dias")).toBeInTheDocument();
    await user.clear(screen.getByLabelText("Validade (s)"));
    await user.type(screen.getByLabelText("Validade (s)"), "0");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(
      await screen.findByText(/informe uma validade de medição maior que zero/),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "Remover condição 1" }),
    );
    expect(screen.queryByLabelText("Validade (s)")).not.toBeInTheDocument();
  });

  it("exibe a fórmula inclusiva de conforto adaptativo", async () => {
    const user = userEvent.setup();
    renderDialog();
    const comfort = screen.getByRole("switch", { name: "Conforto adaptativo" });
    await user.click(comfort);
    expect(screen.getByText(/Tn = 17,8 \+ 0,31 × Temed/)).toHaveTextContent(
      "Os limites são inclusivos",
    );
  });

  it("desmonta sem deixar requests, timers ou mocks ativos", async () => {
    vi.useFakeTimers();
    const { unmount, client } = renderDialog();
    unmount();
    client.clear();
    vi.runOnlyPendingTimers();
    expect(vi.getTimerCount()).toBe(0);
  });
});
