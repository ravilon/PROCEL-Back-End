import { AddOutlined, DeleteOutlined } from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { listCatalogSensors } from "../../api/catalog";
import {
  collectSharedIntegration,
  listSharedContexts,
  listSharedIntegrations,
  removeSharedIntegrationCredential,
  saveSharedIntegration,
  setSharedIntegrationActive,
  testSharedIntegrationConfiguration,
  type CollectionTestResult,
  type HttpMapping,
  type IntegrationPayload,
  type SharedContext,
  type SharedIntegration,
} from "../../api/sharedContexts";
import { listSensorTypes } from "../../api/sensors";
import { useAuth } from "../../auth/AuthContext";
import type { ParametroDef, Sensor } from "../../types";

type Pair = { key: string; value: string };
const emptyMapping: HttpMapping = {
  parametroDefId: "",
  parameterName: "",
  jsonPointer: "",
  expectedType: "NUMERIC",
  sourceUnit: "",
  targetUnit: "",
  required: true,
  conversion: "NONE",
};
const emptyForm: IntegrationPayload = {
  sensorExternalId: "",
  nome: "",
  descricao: "",
  providerName: "",
  attribution: "",
  ativo: false,
  endpointUrl: "",
  metodo: "GET",
  contentType: "application/json",
  requestBody: "",
  authMode: "NONE",
  credential: "",
  credentialHeader: "X-API-Key",
  credentialQueryParam: "key",
  queryParamsJson: "{}",
  headersJson: "[]",
  mappings: [emptyMapping],
  observedAtJsonPointer: "",
  observedAtFormat: "ISO_8601",
  observedAtTimezone: "",
  sourceIdJsonPointer: "",
  intervalSeconds: 1800,
  timeoutMillis: 5000,
  maxAttempts: 2,
  retryDelayMillis: 500,
  validitySeconds: 3600,
  automaticCollection: false,
};

export function GenericHttpCollectionsPage() {
  const { session } = useAuth();
  const [contexts, setContexts] = useState<SharedContext[]>([]);
  const [sensors, setSensors] = useState<Sensor[]>([]);
  const [parameters, setParameters] = useState<ParametroDef[]>([]);
  const [items, setItems] = useState<SharedIntegration[]>([]);
  const [contextId, setContextId] = useState("");
  const [form, setForm] = useState<IntegrationPayload>(emptyForm);
  const [queryRows, setQueryRows] = useState<Pair[]>([]);
  const [headerRows, setHeaderRows] = useState<Pair[]>([]);
  const [editing, setEditing] = useState<SharedIntegration>();
  const [section, setSection] = useState(0);
  const [message, setMessage] = useState("");
  const [testResult, setTestResult] = useState<CollectionTestResult>();
  const [testError, setTestError] = useState("");
  const reload = async () => {
    const result = await listSharedContexts(session);
    setContexts(result);
    setItems(
      (
        await Promise.all(
          result.map((context) => listSharedIntegrations(context.id, session)),
        )
      ).flat(),
    );
  };
  useEffect(() => {
    void Promise.all([
      listSharedContexts(session),
      listCatalogSensors(session),
      listSensorTypes(session),
    ])
      .then(async ([result, allSensors, types]) => {
        setContexts(result);
        setSensors(
          allSensors.filter((sensor) =>
            Boolean(sensor.contextoCompartilhadoId),
          ),
        );
        setParameters(
          types
            .flatMap((type) => type.parametros)
            .filter((parameter) => parameter.ativo),
        );
        setItems(
          (
            await Promise.all(
              result.map((context) =>
                listSharedIntegrations(context.id, session),
              ),
            )
          ).flat(),
        );
      })
      .catch((caught) =>
        setMessage(
          caught instanceof Error
            ? caught.message
            : "Falha ao carregar coletas",
        ),
      );
  }, [session]);
  const availableSensors = useMemo(
    () =>
      sensors.filter((sensor) => sensor.contextoCompartilhadoId === contextId),
    [sensors, contextId],
  );
  const selectedSensor = sensors.find(
    (sensor) => sensor.externalId === form.sensorExternalId,
  );
  const availableParameters = parameters.filter(
    (parameter) => parameter.tipoNome === selectedSensor?.tipoNome,
  );
  const applyPreset = () => {
    const suggested = availableParameters.find(
      (parameter) => parameter.nome === "temperatura_externa",
    );
    setForm({
      ...emptyForm,
      nome: "WeatherAPI — Temperatura atual",
      providerName: "WeatherAPI",
      attribution: "WeatherAPI.com",
      endpointUrl: "https://api.weatherapi.com/v1/current.json",
      authMode: "QUERY",
      credentialQueryParam: "key",
      observedAtJsonPointer: "/current/last_updated_epoch",
      observedAtFormat: "UNIX_SECONDS",
      intervalSeconds: 1800,
      timeoutMillis: 5000,
      maxAttempts: 2,
      validitySeconds: 3600,
      mappings: [
        {
          ...emptyMapping,
          parametroDefId: suggested?.id ?? "",
          parameterName: suggested?.nome ?? "temperatura_externa",
          jsonPointer: "/current/temp_c",
          sourceUnit: "°C",
          targetUnit: "°C",
        },
      ],
    });
    setQueryRows([
      { key: "q", value: "{{context.coordinates}}" },
      { key: "aqi", value: "no" },
    ]);
    setHeaderRows([]);
    setEditing(undefined);
    setTestResult(undefined);
    setTestError("");
  };
  const payload = (): IntegrationPayload => ({
    ...form,
    credential: form.credential || undefined,
    queryParamsJson: JSON.stringify(
      Object.fromEntries(
        queryRows.filter((row) => row.key).map((row) => [row.key, row.value]),
      ),
    ),
    headersJson: JSON.stringify(headerRows.filter((row) => row.key)),
  });
  const save = async () => {
    try {
      if (!contextId) throw new Error("Selecione o contexto");
      await saveSharedIntegration(contextId, payload(), session);
      setForm((current) => ({ ...current, credential: "" }));
      setMessage("Coleta salva.");
      await reload();
    } catch (caught) {
      setMessage(caught instanceof Error ? caught.message : "Falha ao salvar");
    }
  };
  const test = async () => {
    try {
      if (!contextId) throw new Error("Selecione o contexto");
      setTestError("");
      setTestResult(
        await testSharedIntegrationConfiguration(contextId, payload(), session),
      );
    } catch (caught) {
      setTestResult(undefined);
      setTestError(caught instanceof Error ? caught.message : "Falha no teste");
    } finally {
      setForm((current) => ({ ...current, credential: "" }));
    }
  };
  const edit = (item: SharedIntegration) => {
    setEditing(item);
    setContextId(item.contextoId);
    setForm({
      ...item,
      credential: "",
      descricao: item.descricao ?? "",
      providerName: item.providerName ?? "",
      attribution: item.attribution ?? "",
      requestBody: item.requestBody ?? "",
      headersJson: item.headersJson ?? "[]",
      queryParamsJson: item.queryParamsJson ?? "{}",
      mappings: item.mappings.length ? item.mappings : [emptyMapping],
    });
    try {
      setQueryRows(
        Object.entries(JSON.parse(item.queryParamsJson ?? "{}")).map(
          ([key, value]) => ({ key, value: String(value) }),
        ),
      );
      setHeaderRows(JSON.parse(item.headersJson ?? "[]"));
    } catch {
      setQueryRows([]);
      setHeaderRows([]);
    }
    setSection(0);
  };
  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Typography variant="h4">Perfis de integração HTTP</Typography>
          <Chip label="Saída" color="primary" variant="outlined" />
        </Stack>
        <Typography color="text.secondary">
          Configure perfis de saída que consultam provedores externos e
          alimentam o pipeline canônico. Eles não expõem endpoints de entrada.
        </Typography>
      </Box>
      {message && <Alert onClose={() => setMessage("")}>{message}</Alert>}
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
        <Button
          variant="contained"
          onClick={() => {
            setEditing(undefined);
            setForm(emptyForm);
            setContextId("");
            setQueryRows([]);
            setHeaderRows([]);
            setTestResult(undefined);
            setTestError("");
          }}
        >
          Novo perfil de saída
        </Button>
        <Button variant="outlined" onClick={applyPreset}>
          Configurar WeatherAPI para um contexto
        </Button>
      </Stack>
      <Card>
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
              <TextField
                select
                label="Preset opcional"
                value=""
                onChange={(event) => {
                  if (event.target.value === "WEATHERAPI_CURRENT_V1")
                    applyPreset();
                }}
                fullWidth
              >
                <MenuItem value="">Configuração manual</MenuItem>
                <MenuItem value="WEATHERAPI_CURRENT_V1">
                  WeatherAPI — Temperatura atual
                </MenuItem>
              </TextField>
              <TextField
                select
                label="Contexto"
                value={contextId}
                onChange={(event) => {
                  setContextId(event.target.value);
                  setForm({ ...form, sensorExternalId: "" });
                }}
                fullWidth
              >
                {contexts.map((context) => (
                  <MenuItem key={context.id} value={context.id}>
                    {context.nome}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label="Sensor compartilhado"
                value={form.sensorExternalId}
                onChange={(event) =>
                  setForm({
                    ...form,
                    sensorExternalId: event.target.value,
                    mappings: [emptyMapping],
                  })
                }
                fullWidth
              >
                {availableSensors.map((sensor) => (
                  <MenuItem key={sensor.externalId} value={sensor.externalId}>
                    {sensor.nome} · {sensor.tipoNome}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <Tabs
              value={section}
              onChange={(_, value) => setSection(value)}
              variant="scrollable"
              scrollButtons="auto"
            >
              <Tab label="Geral" />
              <Tab label="Requisição" />
              <Tab label="Autenticação" />
              <Tab label="Mapeamento da resposta" />
              <Tab label="Agendamento e resiliência" />
              <Tab label="Teste e prévia" />
            </Tabs>
            {section === 0 && (
              <Stack spacing={2}>
                <TextField
                  label="Nome"
                  value={form.nome}
                  onChange={(event) =>
                    setForm({ ...form, nome: event.target.value })
                  }
                />
                <TextField
                  label="Descrição"
                  value={form.descricao}
                  onChange={(event) =>
                    setForm({ ...form, descricao: event.target.value })
                  }
                />
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <TextField
                    label="Provedor"
                    value={form.providerName}
                    onChange={(event) =>
                      setForm({ ...form, providerName: event.target.value })
                    }
                    fullWidth
                  />
                  <TextField
                    label="Atribuição"
                    value={form.attribution}
                    onChange={(event) =>
                      setForm({ ...form, attribution: event.target.value })
                    }
                    fullWidth
                  />
                </Stack>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={form.ativo}
                      onChange={(_, checked) =>
                        setForm({ ...form, ativo: checked })
                      }
                    />
                  }
                  label="Perfil de saída ativo"
                />
              </Stack>
            )}
            {section === 1 && (
              <Stack spacing={2}>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <TextField
                    label="Endpoint"
                    value={form.endpointUrl}
                    onChange={(event) =>
                      setForm({ ...form, endpointUrl: event.target.value })
                    }
                    fullWidth
                  />
                  <TextField
                    select
                    label="Método"
                    value={form.metodo}
                    onChange={(event) =>
                      setForm({ ...form, metodo: event.target.value })
                    }
                    sx={{ minWidth: 130 }}
                  >
                    <MenuItem value="GET">GET</MenuItem>
                    <MenuItem value="POST">POST</MenuItem>
                  </TextField>
                  <TextField
                    label="Content-Type"
                    value={form.contentType}
                    onChange={(event) =>
                      setForm({ ...form, contentType: event.target.value })
                    }
                  />
                </Stack>
                <PairEditor
                  title="Query parameters"
                  rows={queryRows}
                  setRows={setQueryRows}
                />
                <PairEditor
                  title="Headers"
                  rows={headerRows}
                  setRows={setHeaderRows}
                />
                {form.metodo === "POST" && (
                  <TextField
                    label="Corpo JSON"
                    value={form.requestBody}
                    onChange={(event) =>
                      setForm({ ...form, requestBody: event.target.value })
                    }
                    multiline
                    minRows={4}
                  />
                )}
                <Alert severity="info">
                  Templates permitidos:{" "}
                  {
                    "{{context.latitude}}, {{context.longitude}}, {{context.coordinates}}, {{execution.timestamp}}"
                  }
                </Alert>
              </Stack>
            )}
            {section === 2 && (
              <Stack spacing={2}>
                <TextField
                  select
                  label="Autenticação"
                  value={form.authMode}
                  onChange={(event) =>
                    setForm({ ...form, authMode: event.target.value })
                  }
                >
                  <MenuItem value="NONE">Nenhuma</MenuItem>
                  <MenuItem value="QUERY">API key em query parameter</MenuItem>
                  <MenuItem value="HEADER">API key em header</MenuItem>
                  <MenuItem value="BEARER">Bearer token</MenuItem>
                </TextField>
                {form.authMode === "QUERY" && (
                  <TextField
                    label="Nome do parâmetro"
                    value={form.credentialQueryParam}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        credentialQueryParam: event.target.value,
                      })
                    }
                  />
                )}{" "}
                {form.authMode === "HEADER" && (
                  <TextField
                    label="Nome do header"
                    value={form.credentialHeader}
                    onChange={(event) =>
                      setForm({ ...form, credentialHeader: event.target.value })
                    }
                  />
                )}{" "}
                {form.authMode !== "NONE" && (
                  <TextField
                    type="password"
                    label={
                      editing?.credentialConfigured
                        ? "Nova credencial (vazio preserva a atual)"
                        : "Credencial"
                    }
                    value={form.credential ?? ""}
                    onChange={(event) =>
                      setForm({ ...form, credential: event.target.value })
                    }
                    autoComplete="new-password"
                  />
                )}
                {editing?.credentialConfigured && (
                  <Button
                    color="error"
                    onClick={() =>
                      void removeSharedIntegrationCredential(
                        editing.id,
                        session,
                      ).then(reload)
                    }
                  >
                    Remover credencial armazenada
                  </Button>
                )}
              </Stack>
            )}
            {section === 3 && (
              <Stack spacing={2}>
                {form.mappings.map((mapping, index) => (
                  <Card key={index} variant="outlined">
                    <CardContent>
                      <Stack spacing={1}>
                        <TextField
                          select
                          label="Parâmetro"
                          value={mapping.parametroDefId}
                          onChange={(event) => {
                            const parameter = availableParameters.find(
                              (item) => item.id === event.target.value,
                            );
                            setForm({
                              ...form,
                              mappings: form.mappings.map((item, i) =>
                                i === index
                                  ? {
                                      ...item,
                                      parametroDefId: event.target.value,
                                      parameterName: parameter?.nome ?? "",
                                      expectedType:
                                        parameter?.dataType ?? "NUMERIC",
                                      targetUnit: parameter?.numericUnit ?? "",
                                    }
                                  : item,
                              ),
                            });
                          }}
                        >
                          {availableParameters.map((parameter) => (
                            <MenuItem key={parameter.id} value={parameter.id}>
                              {parameter.nome} · {parameter.dataType}
                            </MenuItem>
                          ))}
                        </TextField>
                        <TextField
                          label="JSON Pointer"
                          value={mapping.jsonPointer}
                          onChange={(event) =>
                            setForm({
                              ...form,
                              mappings: form.mappings.map((item, i) =>
                                i === index
                                  ? { ...item, jsonPointer: event.target.value }
                                  : item,
                              ),
                            })
                          }
                        />
                        <Stack
                          direction={{ xs: "column", sm: "row" }}
                          spacing={1}
                        >
                          <TextField
                            label="Unidade de origem"
                            value={mapping.sourceUnit}
                            onChange={(event) =>
                              setForm({
                                ...form,
                                mappings: form.mappings.map((item, i) =>
                                  i === index
                                    ? {
                                        ...item,
                                        sourceUnit: event.target.value,
                                      }
                                    : item,
                                ),
                              })
                            }
                          />
                          <TextField
                            label="Unidade de destino"
                            value={mapping.targetUnit}
                            disabled
                          />
                          <TextField
                            select
                            label="Conversão"
                            value={mapping.conversion}
                            onChange={(event) =>
                              setForm({
                                ...form,
                                mappings: form.mappings.map((item, i) =>
                                  i === index
                                    ? {
                                        ...item,
                                        conversion: event.target
                                          .value as HttpMapping["conversion"],
                                      }
                                    : item,
                                ),
                              })
                            }
                          >
                            <MenuItem value="NONE">Nenhuma</MenuItem>
                            <MenuItem value="C_TO_F">°C → °F</MenuItem>
                            <MenuItem value="F_TO_C">°F → °C</MenuItem>
                          </TextField>
                        </Stack>
                        <Stack direction="row">
                          <FormControlLabel
                            control={
                              <Checkbox
                                checked={mapping.required}
                                onChange={(_, checked) =>
                                  setForm({
                                    ...form,
                                    mappings: form.mappings.map((item, i) =>
                                      i === index
                                        ? { ...item, required: checked }
                                        : item,
                                    ),
                                  })
                                }
                              />
                            }
                            label="Obrigatório"
                          />
                          <Button
                            color="error"
                            startIcon={<DeleteOutlined />}
                            onClick={() =>
                              setForm({
                                ...form,
                                mappings: form.mappings.filter(
                                  (_, i) => i !== index,
                                ),
                              })
                            }
                          >
                            Remover
                          </Button>
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
                <Button
                  startIcon={<AddOutlined />}
                  onClick={() =>
                    setForm({
                      ...form,
                      mappings: [...form.mappings, { ...emptyMapping }],
                    })
                  }
                >
                  Adicionar mapeamento
                </Button>
                <TextField
                  label="JSON Pointer do horário"
                  value={form.observedAtJsonPointer}
                  onChange={(event) =>
                    setForm({
                      ...form,
                      observedAtJsonPointer: event.target.value,
                    })
                  }
                />
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <TextField
                    select
                    label="Formato"
                    value={form.observedAtFormat}
                    onChange={(event) =>
                      setForm({ ...form, observedAtFormat: event.target.value })
                    }
                    fullWidth
                  >
                    {[
                      "ISO_8601",
                      "UNIX_SECONDS",
                      "UNIX_MILLISECONDS",
                      "COLLECTION_TIME",
                    ].map((value) => (
                      <MenuItem key={value} value={value}>
                        {value}
                      </MenuItem>
                    ))}
                  </TextField>
                  <TextField
                    label="Timezone sem offset"
                    value={form.observedAtTimezone}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        observedAtTimezone: event.target.value,
                      })
                    }
                    fullWidth
                  />
                  <TextField
                    label="Pointer do identificador da fonte"
                    value={form.sourceIdJsonPointer}
                    onChange={(event) =>
                      setForm({
                        ...form,
                        sourceIdJsonPointer: event.target.value,
                      })
                    }
                    fullWidth
                  />
                </Stack>
              </Stack>
            )}
            {section === 4 && (
              <Stack spacing={2}>
                <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
                  {(
                    [
                      ["Intervalo (segundos)", "intervalSeconds"],
                      ["Timeout (ms)", "timeoutMillis"],
                      ["Tentativas", "maxAttempts"],
                      ["Atraso entre tentativas (ms)", "retryDelayMillis"],
                      ["Validade (segundos)", "validitySeconds"],
                    ] as const
                  ).map(([label, key]) => (
                    <TextField
                      key={key}
                      type="number"
                      label={label}
                      value={form[key]}
                      onChange={(event) =>
                        setForm({ ...form, [key]: Number(event.target.value) })
                      }
                      fullWidth
                    />
                  ))}
                </Stack>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={form.automaticCollection}
                      onChange={(_, checked) =>
                        setForm({ ...form, automaticCollection: checked })
                      }
                    />
                  }
                  label="Execução automática ativa"
                />
              </Stack>
            )}
            {section === 5 && (
              <Stack spacing={2}>
                <Alert severity="info">
                  Prévia sanitizada: {form.metodo}{" "}
                  {safeEndpoint(form.endpointUrl)}. Credenciais são omitidas.
                </Alert>
                <Button variant="outlined" onClick={() => void test()}>
                  Testar sem persistir
                </Button>
                <TestPreview result={testResult} error={testError} />
              </Stack>
            )}
            <Button variant="contained" onClick={() => void save()}>
              Salvar como {form.ativo ? "ativa" : "inativa"}
            </Button>
          </Stack>
        </CardContent>
      </Card>
      {items.map((item) => (
        <Card key={item.id}>
          <CardContent>
            <Stack spacing={1}>
              <Typography variant="h6">{item.nome}</Typography>
              <Typography>
                {item.metodo} · {item.ativo ? "Ativa" : "Inativa"} · automática:{" "}
                {item.automaticCollection ? "sim" : "não"}
              </Typography>
              <Typography>
                Último resultado: {item.lastOutcome ?? "—"} · próxima execução:{" "}
                {item.nextRunAt
                  ? new Date(item.nextRunAt).toLocaleString()
                  : "—"}
              </Typography>
              {item.lastError && (
                <Alert severity="warning">{item.lastError}</Alert>
              )}
              <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                <Button onClick={() => edit(item)}>Editar</Button>
                <Button
                  onClick={() =>
                    void collectSharedIntegration(item.id, session).then(reload)
                  }
                >
                  Coletar manualmente
                </Button>
                <Button
                  onClick={() =>
                    void setSharedIntegrationActive(
                      item.id,
                      !item.ativo,
                      session,
                    ).then(reload)
                  }
                >
                  {item.ativo ? "Desativar" : "Ativar"}
                </Button>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}

function PairEditor({
  title,
  rows,
  setRows,
}: {
  title: string;
  rows: Pair[];
  setRows: (rows: Pair[]) => void;
}) {
  return (
    <Stack spacing={1}>
      <Typography fontWeight={600}>{title}</Typography>
      {rows.map((row, index) => (
        <Stack key={index} direction="row" spacing={1}>
          <TextField
            label="Chave"
            value={row.key}
            onChange={(event) =>
              setRows(
                rows.map((item, i) =>
                  i === index ? { ...item, key: event.target.value } : item,
                ),
              )
            }
            fullWidth
          />
          <TextField
            label="Valor ou template"
            value={row.value}
            onChange={(event) =>
              setRows(
                rows.map((item, i) =>
                  i === index ? { ...item, value: event.target.value } : item,
                ),
              )
            }
            fullWidth
          />
          <Button
            color="error"
            onClick={() => setRows(rows.filter((_, i) => i !== index))}
          >
            Remover
          </Button>
        </Stack>
      ))}
      <Button
        startIcon={<AddOutlined />}
        onClick={() => setRows([...rows, { key: "", value: "" }])}
      >
        Adicionar
      </Button>
    </Stack>
  );
}
function TestPreview({
  result,
  error,
}: {
  result?: CollectionTestResult;
  error: string;
}) {
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!result) return null;
  const success = result.outcome === "TEST_SUCCESS";
  return (
    <Stack spacing={2} data-testid="collection-test-preview">
      <Alert severity={success ? "success" : "error"}>
        {success
          ? "Teste concluído sem persistência."
          : `${result.errorType ?? "Falha"}: ${result.errorMessage ?? "Não foi possível interpretar a resposta."}`}{" "}
        Nenhuma medição foi persistida.
      </Alert>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
        <Typography>
          <strong>Status HTTP:</strong> {result.httpStatus ?? "—"}
        </Typography>
        <Typography>
          <strong>Duração:</strong> {result.durationMillis ?? "—"} ms
        </Typography>
        <Typography>
          <strong>Requisição:</strong> {result.method ?? "—"}{" "}
          {result.sanitizedUrl ?? "—"}
        </Typography>
      </Stack>
      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <PreviewCode title="Headers sanitizados" value={result.headers} />
        <PreviewCode
          title="Query parameters sanitizados"
          value={result.queryParameters}
        />
      </Stack>
      <Box>
        <Typography fontWeight={600}>Horário observado</Typography>
        <Typography>Original: {result.observedAtOriginal ?? "—"}</Typography>
        <Typography>
          Convertido:{" "}
          {result.observationAt
            ? new Date(result.observationAt).toISOString()
            : "—"}
        </Typography>
        <Typography>Formato: {result.observedAtFormat ?? "—"}</Typography>
      </Box>
      <Box>
        <Typography fontWeight={600}>JSON bruto retornado</Typography>
        <Paper
          variant="outlined"
          sx={{ p: 1, maxHeight: 320, overflow: "auto" }}
        >
          <Box
            component="pre"
            data-testid="raw-json-preview"
            sx={{
              m: 0,
              whiteSpace: "pre-wrap",
              overflowWrap: "anywhere",
              fontSize: 12,
            }}
          >
            {result.rawJson ?? "—"}
          </Box>
        </Paper>
      </Box>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>ParametroDef</TableCell>
              <TableCell>JSON Pointer</TableCell>
              <TableCell>Valor encontrado</TableCell>
              <TableCell>Tipo detectado</TableCell>
              <TableCell>Valor convertido</TableCell>
              <TableCell>Unidade</TableCell>
              <TableCell>Resultado</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {result.mappings.map((mapping, index) => (
              <TableRow
                key={`${mapping.parametroDefId ?? mapping.parameterName}-${index}`}
              >
                <TableCell>
                  {mapping.parameterName}
                  {mapping.parametroDefId && (
                    <Typography variant="caption" display="block">
                      {mapping.parametroDefId}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{mapping.jsonPointer}</TableCell>
                <TableCell>{mapping.foundValue ?? "—"}</TableCell>
                <TableCell>{mapping.detectedType ?? "—"}</TableCell>
                <TableCell>{mapping.convertedValue ?? "—"}</TableCell>
                <TableCell>{mapping.unit ?? "—"}</TableCell>
                <TableCell>
                  {mapping.success ? "Sucesso" : (mapping.error ?? "Erro")}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}
function PreviewCode({
  title,
  value,
}: {
  title: string;
  value: Record<string, string>;
}) {
  return (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography fontWeight={600}>{title}</Typography>
      <Paper variant="outlined" sx={{ p: 1 }}>
        <Box
          component="pre"
          sx={{
            m: 0,
            whiteSpace: "pre-wrap",
            overflowWrap: "anywhere",
            fontSize: 12,
          }}
        >
          {JSON.stringify(value ?? {}, null, 2)}
        </Box>
      </Paper>
    </Box>
  );
}
function safeEndpoint(value: string) {
  try {
    const parsed = new URL(value);
    return `${parsed.host}${parsed.pathname}`;
  } catch {
    return "endpoint ainda inválido";
  }
}
