import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { CampusCoordinatesPage } from "../CampusCoordinatesPage";

const updateCampus=vi.fn();
vi.mock("../../auth/AuthContext",()=>({useAuth:()=>({session:null})}));
vi.mock("../../api/sharedContexts",()=>({
  listCampuses:vi.fn(async()=>[{nome:"Campus Anglo",contextoNomes:["Tempo Anglo"]}]),
  updateCampus:(...args:unknown[])=>updateCampus(...args),
}));

describe("Coordenadas dos campi",()=>{
  it("lista, pesquisa, valida o par e salva coordenadas válidas",async()=>{updateCampus.mockResolvedValue({});const user=userEvent.setup();render(<CampusCoordinatesPage/>);expect(await screen.findByText("Campus Anglo")).toBeInTheDocument();expect(screen.getByText("Sem coordenadas")).toBeInTheDocument();expect(screen.getByText("Tempo Anglo")).toBeInTheDocument();await user.type(screen.getByLabelText("Pesquisar campus"),"inexistente");expect(screen.queryByText("Campus Anglo")).not.toBeInTheDocument();await user.clear(screen.getByLabelText("Pesquisar campus"));const latitude=screen.getByLabelText("Latitude");const longitude=screen.getByLabelText("Longitude");await user.type(latitude,"-31.77");expect(screen.getByText("Informe latitude e longitude em conjunto.")).toBeInTheDocument();expect(screen.getByRole("button",{name:"Salvar coordenadas"})).toBeDisabled();await user.type(longitude,"-52.34");expect(screen.getByRole("button",{name:"Salvar coordenadas"})).toBeEnabled();await user.click(screen.getByRole("button",{name:"Salvar coordenadas"}));expect(updateCampus).toHaveBeenCalledWith("Campus Anglo",{latitude:-31.77,longitude:-52.34},null);});

  it("rejeita latitude e longitude fora dos limites",async()=>{const user=userEvent.setup();render(<CampusCoordinatesPage/>);await screen.findByText("Campus Anglo");await user.type(screen.getByLabelText("Latitude"),"91");await user.type(screen.getByLabelText("Longitude"),"181");expect(screen.getByRole("button",{name:"Salvar coordenadas"})).toBeDisabled();});
});
