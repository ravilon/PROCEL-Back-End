import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SharedObservationQuery } from "../SharedObservationQuery";

const api = vi.hoisted(() => ({ get: vi.fn(), searchRooms: vi.fn() }));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: null }),
}));
vi.mock("../../../api/sharedContexts", () => ({
  getSharedObservation: (...args: unknown[]) => api.get(...args),
}));
vi.mock("../../../api/catalog", () => ({
  listCompartimentos: (...args: unknown[]) => api.searchRooms(...args),
}));

const rooms = [
  {
    id: "room-1",
    nome: "Sala 101",
    tipo: "Sala",
    predioId: "building-1",
    predioNome: "Anglo",
    campusNome: "Campus Anglo",
    unidadeNome: "Engenharia",
  },
];
const parameters = [
  {
    id: "parameter-1",
    tipoNome: "weather",
    nome: "temperatura_externa",
    descricao: "Temperatura externa",
    dataType: "NUMERIC" as const,
    numericUnit: "°C",
    ativo: true,
  },
];

describe("Consulta de disponibilidade da fonte compartilhada", () => {
  beforeEach(() => {
    api.searchRooms.mockReset().mockResolvedValue(rooms);
    api.get.mockReset().mockResolvedValue({
      status: "AVAILABLE",
      value: 21.5,
      unit: "°C",
      observedAt: "2026-11-09T12:00:00Z",
      validUntil: "2026-11-09T13:00:00Z",
      sensorExternalId: "weather-anglo",
      source: "API",
    });
  });

  it("consulta e apresenta o estado AVAILABLE", async () => {
    const user = userEvent.setup();
    render(
      <SharedObservationQuery
        rooms={rooms}
        parameters={parameters}
        loadingCatalogs={false}
      />,
    );

    await user.click(screen.getByLabelText("Sala"));
    await user.click(
      await screen.findByRole("option", { name: /room-1.*Sala 101/ }),
    );
    await user.click(screen.getByLabelText("Parâmetro"));
    await user.click(
      await screen.findByRole("option", {
        name: /parameter-1.*temperatura_externa/,
      }),
    );
    await user.click(screen.getByRole("button", { name: "Consultar" }));

    expect(api.get).toHaveBeenCalledWith(
      "room-1",
      "parameter-1",
      undefined,
      null,
    );
    expect((await screen.findAllByText("AVAILABLE")).length).toBeGreaterThan(0);
    expect(screen.getByText(/21.5/)).toBeInTheDocument();
    expect(screen.getByText(/weather-anglo/)).toBeInTheDocument();
  });

  it("pesquisa salas dinamicamente pelo identificador digitado", async () => {
    const user = userEvent.setup();
    render(
      <SharedObservationQuery
        rooms={[]}
        parameters={parameters}
        loadingCatalogs={false}
      />,
    );

    await user.type(screen.getByLabelText("Sala"), "room-1");

    await waitFor(() =>
      expect(api.searchRooms).toHaveBeenCalledWith({ q: "room-1" }, null),
    );
    expect(
      await screen.findByRole("option", { name: /room-1.*Sala 101/ }),
    ).toBeInTheDocument();
  });
});
