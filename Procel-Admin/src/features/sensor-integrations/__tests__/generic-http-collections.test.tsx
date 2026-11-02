import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { GenericHttpCollectionsPage } from "../GenericHttpCollectionsPage";

const testConfiguration = vi.fn();
const context = { id:"ctx-1", nome:"Campus Anglo", ativo:true, timezone:"America/Sao_Paulo", locationMode:"CAMPUS" as const, compartimentoIds:[] };
const sensor = { externalId:"weather-anglo", nome:"Tempo Anglo", tipoNome:"weather", contextoCompartilhadoId:"ctx-1", ativo:true };
const parameter = { id:"param-1", tipoNome:"weather", nome:"temperatura_externa", dataType:"NUMERIC" as const, numericUnit:"°C", ativo:true };

vi.mock("../../../auth/AuthContext",()=>({useAuth:()=>({session:null})}));
vi.mock("../../../api/catalog",()=>({listCatalogSensors:vi.fn(async()=>[sensor])}));
vi.mock("../../../api/sensors",()=>({listSensorTypes:vi.fn(async()=>[{nome:"weather",parametros:[parameter]}])}));
vi.mock("../../../api/sharedContexts",()=>({
  listSharedContexts:vi.fn(async()=>[context]),listSharedIntegrations:vi.fn(async()=>[]),
  testSharedIntegrationConfiguration:(...args:unknown[])=>testConfiguration(...args),
  saveSharedIntegration:vi.fn(),collectSharedIntegration:vi.fn(),removeSharedIntegrationCredential:vi.fn(),setSharedIntegrationActive:vi.fn(),
}));

async function setupPreset(){const user=userEvent.setup();render(<MemoryRouter><GenericHttpCollectionsPage/></MemoryRouter>);const contextSelect=await screen.findByLabelText("Contexto");await user.click(contextSelect);await user.click(await screen.findByRole("option",{name:"Campus Anglo"}));await user.click(screen.getByLabelText("Sensor compartilhado"));await user.click(await screen.findByRole("option",{name:/Tempo Anglo/}));await user.click(screen.getByRole("button",{name:"Configurar WeatherAPI para um contexto"}));return user;}

describe("configurador HTTP genérico",()=>{
  beforeEach(()=>testConfiguration.mockReset());

  it("aplica o preset de 30 minutos e mantém os campos editáveis",async()=>{const user=await setupPreset();await user.click(screen.getByRole("tab",{name:"Agendamento e resiliência"}));expect(screen.getByLabelText("Intervalo (segundos)")).toHaveValue(1800);await user.clear(screen.getByLabelText("Intervalo (segundos)"));await user.type(screen.getByLabelText("Intervalo (segundos)"),"2400");expect(screen.getByLabelText("Intervalo (segundos)")).toHaveValue(2400);await user.click(screen.getByRole("tab",{name:"Requisição"}));expect(screen.getByLabelText("Endpoint")).toHaveValue("https://api.weatherapi.com/v1/current.json");await user.clear(screen.getByLabelText("Endpoint"));await user.type(screen.getByLabelText("Endpoint"),"https://weather.example/current");expect(screen.getByLabelText("Endpoint")).toHaveValue("https://weather.example/current");});

  it("edita query parameters e headers dinamicamente",async()=>{const user=await setupPreset();await user.click(screen.getByRole("tab",{name:"Requisição"}));const querySection=screen.getByText("Query parameters").parentElement!;expect(within(querySection).getAllByLabelText("Chave")).toHaveLength(2);await user.click(within(querySection).getByRole("button",{name:"Adicionar"}));expect(within(querySection).getAllByLabelText("Chave")).toHaveLength(3);const headerSection=screen.getByText("Headers").parentElement!;await user.click(within(headerSection).getByRole("button",{name:"Adicionar"}));await user.type(within(headerSection).getByLabelText("Chave"),"X-Tenant");await user.type(within(headerSection).getByLabelText("Valor ou template"),"anglo");expect(within(headerSection).getByLabelText("Chave")).toHaveValue("X-Tenant");});

  it("renderiza status, duração, JSON, horário e mappings sem expor a credencial",async()=>{testConfiguration.mockResolvedValue({outcome:"TEST_SUCCESS",observationAt:"2026-11-02T12:00:00Z",value:21.5,persisted:false,httpStatus:200,durationMillis:87,method:"GET",sanitizedUrl:"https://api.weatherapi.com/v1/current.json",headers:{Accept:"application/json"},queryParameters:{q:"-31.7,-52.3",key:"[REDACTED]"},rawJson:'{\n  "current" : { "temp_c" : 21.5 }\n}',mappings:[{parametroDefId:"param-1",parameterName:"temperatura_externa",jsonPointer:"/current/temp_c",foundValue:"21.5",detectedType:"NUMERIC",convertedValue:"21.5",unit:"°C",success:true}],observedAtOriginal:"1793620800",observedAtFormat:"UNIX_SECONDS"});const user=await setupPreset();await user.click(screen.getByRole("tab",{name:"Autenticação"}));await user.type(screen.getByLabelText("Credencial"),"segredo-super-secreto");await user.click(screen.getByRole("tab",{name:"Teste e prévia"}));await user.click(screen.getByRole("button",{name:"Testar sem persistir"}));expect(await screen.findByText(/Teste concluído sem persistência/)).toBeInTheDocument();expect(screen.getByText(/Status HTTP:/).parentElement).toHaveTextContent("200");expect(screen.getByText(/Duração:/).parentElement).toHaveTextContent("87 ms");expect(screen.getByTestId("raw-json-preview")).toHaveTextContent("temp_c");expect(screen.getByRole("cell",{name:"/current/temp_c"})).toBeInTheDocument();expect(screen.getByText(/UNIX_SECONDS/)).toBeInTheDocument();expect(screen.getByText(/Nenhuma medição foi persistida/)).toBeInTheDocument();expect(document.body.textContent).not.toContain("segredo-super-secreto");});

  it("mostra erros de pointer e conversão na tabela",async()=>{testConfiguration.mockResolvedValue({outcome:"TEST_FAILED",persisted:false,httpStatus:200,durationMillis:12,method:"GET",sanitizedUrl:"https://weather.example/current",headers:{},queryParameters:{},rawJson:"{}",mappings:[{parameterName:"temperatura_externa",jsonPointer:"/missing",detectedType:"MISSING",success:false,error:"required JSON Pointer is absent: /missing"},{parameterName:"umidade",jsonPointer:"/humidity",foundValue:"abc",detectedType:"TEXT",success:false,error:"unsupported conversion"}],observedAtFormat:"UNIX_SECONDS",errorType:"POINTER_ERROR",errorMessage:"required JSON Pointer is absent: /missing"});const user=await setupPreset();await user.click(screen.getByRole("tab",{name:"Teste e prévia"}));await user.click(screen.getByRole("button",{name:"Testar sem persistir"}));expect(await screen.findByText(/POINTER_ERROR/)).toBeInTheDocument();expect(screen.getAllByText(/required JSON Pointer is absent/).length).toBeGreaterThan(0);expect(screen.getByText("unsupported conversion")).toBeInTheDocument();});
});
