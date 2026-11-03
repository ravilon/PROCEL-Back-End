import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SharedContextsPanel } from "../SharedContextsPanel";

const api = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  preview: vi.fn(),
  createSensor: vi.fn(),
}));
const context = {
  id: "ctx-1",
  nome: "Tempo Anglo",
  descricao: "Contexto externo",
  ativo: true,
  latitude: -31.77,
  longitude: -52.34,
  timezone: "America/Sao_Paulo",
  locationMode: "OWN" as const,
  compartimentoIds: [],
};
vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: null }),
}));
vi.mock("../../../api/catalog", () => ({
  getCompartimentoFilterOptions: vi.fn(async () => ({
    campi: ["Campus Anglo"],
    predios: ["Prédio 1"],
  })),
  listCompartimentos: vi.fn(async () => []),
  listCatalogSensors: vi.fn(async () => []),
}));
vi.mock("../../../api/sensors", () => ({
  listSensorTypes: vi.fn(async () => [
    {
      nome: "weather",
      parametros: [
        {
          id: "p1",
          tipoNome: "weather",
          nome: "temperatura_externa",
          dataType: "NUMERIC",
          numericUnit: "°C",
          ativo: true,
        },
      ],
    },
  ]),
  createSensor: (...args: unknown[]) => api.createSensor(...args),
}));
vi.mock("../../../api/sharedContexts", () => ({
  listSharedContexts: vi.fn(async () => [context]),
  listSharedIntegrations: vi.fn(async () => []),
  createSharedContext: (...args: unknown[]) => api.create(...args),
  updateSharedContext: (...args: unknown[]) => api.update(...args),
  deleteSharedContext: (...args: unknown[]) => api.remove(...args),
  getSharedContextDeletePreview: (...args: unknown[]) => api.preview(...args),
  replaceContextRooms: vi.fn(),
  selectSharedSourceBatch: vi.fn(),
}));

function renderPanel() {
  return render(
    <MemoryRouter>
      <SharedContextsPanel />
    </MemoryRouter>,
  );
}

describe("Contextos compartilhados", () => {
  beforeEach(() => {
    api.create.mockReset().mockResolvedValue(context);
    api.update.mockReset().mockResolvedValue(context);
    api.remove.mockReset().mockResolvedValue(undefined);
    api.preview.mockReset();
    api.createSensor.mockReset().mockResolvedValue({
      externalId: "weather-anglo",
      nome: "Tempo Anglo",
      tipoNome: "weather",
      contextoCompartilhadoId: "ctx-1",
      ativo: true,
    });
  });

  it("cria e edita um contexto", async () => {
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Tempo Anglo");
    await user.type(
      screen.getByLabelText("Nome do novo contexto"),
      "Novo contexto",
    );
    await user.click(screen.getByRole("button", { name: "Criar" }));
    expect(api.create).toHaveBeenCalledWith(
      expect.objectContaining({ nome: "Novo contexto", ativo: true }),
      null,
    );
    await user.click(screen.getByRole("button", { name: "Editar" }));
    const name = screen
      .getByRole("dialog")
      .querySelector('input[value="Tempo Anglo"]') as HTMLInputElement;
    await user.clear(name);
    await user.type(name, "Tempo Anglo atualizado");
    await user.click(screen.getByRole("button", { name: "Salvar" }));
    expect(api.update).toHaveBeenCalledWith(
      "ctx-1",
      expect.objectContaining({ nome: "Tempo Anglo atualizado" }),
      null,
    );
  });

  it("exclui contexto vazio somente depois da confirmação pelo nome", async () => {
    api.preview.mockResolvedValue({
      id: "ctx-1",
      nome: "Tempo Anglo",
      compartimentos: 0,
      sensores: 0,
      integracoes: 0,
      fontesSelecionadas: 0,
      medicoes: 0,
      auditorias: 0,
      deletable: true,
      dependencies: [],
    });
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Tempo Anglo");
    await user.click(screen.getByRole("button", { name: "Excluir" }));
    const finalButton = await screen.findByRole("button", {
      name: "Excluir definitivamente",
    });
    expect(finalButton).toBeDisabled();
    await user.type(
      screen.getByLabelText("Digite Tempo Anglo para confirmar"),
      "Tempo Anglo",
    );
    expect(finalButton).toBeEnabled();
    await user.click(finalButton);
    expect(api.remove).toHaveBeenCalledWith("ctx-1", null);
  });

  it("bloqueia exclusão com dependências e oferece desativação", async () => {
    api.preview.mockResolvedValue({
      id: "ctx-1",
      nome: "Tempo Anglo",
      compartimentos: 2,
      sensores: 1,
      integracoes: 1,
      fontesSelecionadas: 2,
      medicoes: 8,
      auditorias: 3,
      deletable: false,
      dependencies: ["sensores", "integrações", "medições"],
    });
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Tempo Anglo");
    await user.click(screen.getByRole("button", { name: "Excluir" }));
    expect(await screen.findByText(/Exclusão bloqueada/)).toHaveTextContent(
      "sensores",
    );
    expect(
      screen.getByRole("button", { name: "Excluir definitivamente" }),
    ).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Desativar" }));
    expect(api.update).toHaveBeenCalledWith(
      "ctx-1",
      expect.objectContaining({ ativo: false }),
      null,
    );
    expect(api.remove).not.toHaveBeenCalled();
  });

  it("cria sensor compartilhado diretamente no contexto", async () => {
    const user = userEvent.setup();
    renderPanel();
    await screen.findByText("Tempo Anglo");
    await user.click(
      screen.getByRole("button", { name: "Criar sensor compartilhado" }),
    );
    await user.type(screen.getByLabelText("ID externo"), "weather-anglo");
    await user.type(screen.getByLabelText("Nome"), "Sensor externo Anglo");
    await user.click(screen.getByLabelText("Tipo de sensor"));
    await user.click(await screen.findByRole("option", { name: /weather/ }));
    await user.click(screen.getByRole("button", { name: "Criar sensor" }));
    expect(api.createSensor).toHaveBeenCalledWith(
      expect.objectContaining({
        externalId: "weather-anglo",
        contextoCompartilhadoId: "ctx-1",
        compartimentoId: null,
      }),
      null,
    );
  });
});
