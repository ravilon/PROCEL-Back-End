import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SharedObservationQuery } from "../SharedObservationQuery";

const api = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("../../../auth/AuthContext", () => ({
  useAuth: () => ({ session: null }),
}));
vi.mock("../../../api/sharedContexts", () => ({
  getSharedObservation: (...args: unknown[]) => api.get(...args),
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

    await user.selectOptions(screen.getByLabelText("Sala"), "room-1");
    await user.selectOptions(screen.getByLabelText("Parâmetro"), "parameter-1");
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
});
