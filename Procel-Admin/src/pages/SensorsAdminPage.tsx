import {
  AddOutlined,
  EditOutlined,
  RuleOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  Tab,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type {
  GrupoRegra,
  ParametroDef,
  RegraParametro,
  SensorDataType,
  TipoSensor,
} from "../types";

function ErrorMessage({ error }: { error: Error | null }) {
  return error ? <Alert severity="error">{error.message}</Alert> : null;
}

const operatorLabels: Record<string, string> = {
  GT: "> maior que",
  GTE: "≥ maior ou igual",
  LT: "< menor que",
  LTE: "≤ menor ou igual",
  EQ: "= igual a",
  NEQ: "≠ diferente de",
  BETWEEN: "entre",
  OUTSIDE: "fora do intervalo",
  CONTAINS: "contém",
};

function ruleExpression(rule: RegraParametro) {
  const firstValue = rule.valorNumeric1 ?? rule.valorText ?? rule.valorBoolean;
  return `${rule.parametroNome} ${operatorLabels[rule.operador] ?? rule.operador} ${
    firstValue ?? ""
  }${rule.valorNumeric2 != null ? ` e ${rule.valorNumeric2}` : ""}`;
}

export function SensorsAdminPage() {
  const [tab, setTab] = useState(0);

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Sensores e regras</Typography>
        <Typography color="text.secondary">
          Configure o catálogo de sensores e monte grupos de qualificação a partir dos parâmetros.
        </Typography>
      </Box>
      <Paper>
        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          <Tab icon={<SensorsOutlined />} iconPosition="start" label="Tipos e parâmetros" />
          <Tab icon={<RuleOutlined />} iconPosition="start" label="Grupos de regras" />
        </Tabs>
      </Paper>
      {tab === 0 ? <SensorTypesPanel /> : <RuleGroupsPanel />}
    </Stack>
  );
}

function SensorTypesPanel() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [selectedType, setSelectedType] = useState("");
  const [typeName, setTypeName] = useState("");
  const [editingTypeName, setEditingTypeName] = useState("");
  const [editingParameter, setEditingParameter] = useState<ParametroDef | null>(null);
  const [parameterEdit, setParameterEdit] = useState({
    nome: "",
    descricao: "",
    dataType: "NUMERIC" as SensorDataType,
    numericUnit: "",
  });
  const [parameter, setParameter] = useState({
    nome: "",
    descricao: "",
    dataType: "NUMERIC" as SensorDataType,
    numericUnit: "",
  });
  const types = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => apiRequest<TipoSensor[]>("/api/sensor-admin/types", {}, session),
  });
  const selected = types.data?.find((item) => item.nome === selectedType);

  const createType = useMutation({
    mutationFn: () =>
      apiRequest<TipoSensor>(
        "/api/sensor-admin/types",
        { method: "POST", body: JSON.stringify({ nome: typeName }) },
        session,
      ),
    onSuccess: async (created) => {
      setTypeName("");
      setSelectedType(created.nome);
      await queryClient.invalidateQueries({ queryKey: ["sensor-admin", "types"] });
    },
  });
  const createParameter = useMutation({
    mutationFn: () =>
      apiRequest<ParametroDef>(
        `/api/sensor-admin/types/${encodeURIComponent(selectedType)}/parameters`,
        { method: "POST", body: JSON.stringify(parameter) },
        session,
      ),
    onSuccess: async () => {
      setParameter({ nome: "", descricao: "", dataType: "NUMERIC", numericUnit: "" });
      await queryClient.invalidateQueries({ queryKey: ["sensor-admin", "types"] });
    },
  });
  const updateType = useMutation({
    mutationFn: () =>
      apiRequest<TipoSensor>(
        `/api/sensor-admin/types/${encodeURIComponent(selectedType)}`,
        { method: "PUT", body: JSON.stringify({ nome: editingTypeName }) },
        session,
      ),
    onSuccess: async (updated) => {
      setEditingTypeName("");
      setSelectedType(updated.nome);
      await queryClient.invalidateQueries({ queryKey: ["sensor-admin", "types"] });
    },
  });
  const updateParameter = useMutation({
    mutationFn: () =>
      apiRequest<ParametroDef>(
        `/api/sensor-admin/parameters/${editingParameter!.id}`,
        { method: "PUT", body: JSON.stringify(parameterEdit) },
        session,
      ),
    onSuccess: async () => {
      setEditingParameter(null);
      await queryClient.invalidateQueries({ queryKey: ["sensor-admin", "types"] });
    },
  });

  const openParameterEdit = (item: ParametroDef) => {
    setEditingParameter(item);
    setParameterEdit({
      nome: item.nome,
      descricao: item.descricao ?? "",
      dataType: item.dataType,
      numericUnit: item.numericUnit ?? "",
    });
  };

  return (
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "320px 1fr" }, gap: 2 }}>
      <Stack spacing={2}>
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Typography variant="h6">Novo tipo</Typography>
          <Stack
            component="form"
            direction="row"
            spacing={1}
            sx={{ mt: 2 }}
            onSubmit={(event: FormEvent) => {
              event.preventDefault();
              createType.mutate();
            }}
          >
            <TextField
              label="Nome do tipo"
              value={typeName}
              onChange={(event) => setTypeName(event.target.value)}
              required
              fullWidth
              size="small"
            />
            <Button type="submit" variant="contained" disabled={createType.isPending}>
              Criar
            </Button>
          </Stack>
          <ErrorMessage error={createType.error} />
        </Paper>
        <Paper variant="outlined" sx={{ p: 1 }}>
          <Stack spacing={0.5}>
            {types.data?.map((item) => (
              <Button
                key={item.nome}
                onClick={() => setSelectedType(item.nome)}
                variant={selectedType === item.nome ? "contained" : "text"}
                sx={{ justifyContent: "space-between" }}
              >
                {item.nome}
                <Typography component="span" variant="caption">
                  {item.parametros.length} parâmetros
                </Typography>
              </Button>
            ))}
          </Stack>
          <ErrorMessage error={types.error} />
        </Paper>
      </Stack>

      <Stack spacing={2}>
        {!selected && (
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography color="text.secondary">Selecione ou crie um tipo de sensor.</Typography>
          </Paper>
        )}
        {selected && (
          <>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                <Typography variant="h6">{selected.nome}</Typography>
                <Button
                  size="small"
                  startIcon={<EditOutlined />}
                  onClick={() => setEditingTypeName(selected.nome)}
                >
                  Editar tipo
                </Button>
              </Stack>
              <Stack spacing={1} sx={{ mt: 2 }}>
                {selected.parametros.map((item) => (
                  <Box
                    key={item.id}
                    sx={{
                      p: 1.5,
                      bgcolor: "grey.50",
                      borderRadius: 1,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: 2,
                    }}
                  >
                    <Box>
                    <Typography fontWeight={600}>{item.nome}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {item.dataType}
                      {item.numericUnit ? ` · ${item.numericUnit}` : ""}
                      {item.descricao ? ` · ${item.descricao}` : ""}
                    </Typography>
                    </Box>
                    <Button
                      size="small"
                      startIcon={<EditOutlined />}
                      onClick={() => openParameterEdit(item)}
                    >
                      Editar
                    </Button>
                  </Box>
                ))}
                {selected.parametros.length === 0 && (
                  <Typography color="text.secondary">Nenhum parâmetro cadastrado.</Typography>
                )}
              </Stack>
            </Paper>
            <Paper
              component="form"
              variant="outlined"
              sx={{ p: 2 }}
              onSubmit={(event: FormEvent) => {
                event.preventDefault();
                createParameter.mutate();
              }}
            >
              <Typography variant="h6">Adicionar parâmetro</Typography>
              <Stack spacing={2} sx={{ mt: 2 }}>
                <TextField
                  label="Chave no payload"
                  value={parameter.nome}
                  onChange={(event) => setParameter({ ...parameter, nome: event.target.value })}
                  required
                />
                <TextField
                  label="Descrição"
                  value={parameter.descricao}
                  onChange={(event) => setParameter({ ...parameter, descricao: event.target.value })}
                />
                <FormControl>
                  <InputLabel>Tipo de dado</InputLabel>
                  <Select
                    label="Tipo de dado"
                    value={parameter.dataType}
                    onChange={(event) =>
                      setParameter({ ...parameter, dataType: event.target.value as SensorDataType })
                    }
                  >
                    <MenuItem value="NUMERIC">Numérico</MenuItem>
                    <MenuItem value="BOOLEAN">Booleano</MenuItem>
                    <MenuItem value="TEXT">Texto</MenuItem>
                  </Select>
                </FormControl>
                {parameter.dataType === "NUMERIC" && (
                  <TextField
                    label="Unidade"
                    value={parameter.numericUnit}
                    onChange={(event) =>
                      setParameter({ ...parameter, numericUnit: event.target.value })
                    }
                    placeholder="°C, %, ppm..."
                  />
                )}
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={<AddOutlined />}
                  disabled={createParameter.isPending}
                >
                  Adicionar parâmetro
                </Button>
                <ErrorMessage error={createParameter.error} />
              </Stack>
            </Paper>
          </>
        )}
      </Stack>
      <Dialog
        open={Boolean(editingTypeName)}
        onClose={() => !updateType.isPending && setEditingTypeName("")}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Editar tipo de sensor</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            required
            label="Nome do tipo"
            value={editingTypeName}
            onChange={(event) => setEditingTypeName(event.target.value)}
            sx={{ mt: 1 }}
          />
          <ErrorMessage error={updateType.error} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingTypeName("")} disabled={updateType.isPending}>
            Cancelar
          </Button>
          <Button
            variant="contained"
            onClick={() => updateType.mutate()}
            disabled={updateType.isPending || !editingTypeName.trim()}
          >
            Salvar alteracoes
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={Boolean(editingParameter)}
        onClose={() => !updateParameter.isPending && setEditingParameter(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Editar parametro</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              autoFocus
              required
              label="Chave no payload"
              value={parameterEdit.nome}
              onChange={(event) =>
                setParameterEdit({ ...parameterEdit, nome: event.target.value })
              }
            />
            <TextField
              label="Descricao"
              value={parameterEdit.descricao}
              onChange={(event) =>
                setParameterEdit({ ...parameterEdit, descricao: event.target.value })
              }
            />
            <FormControl>
              <InputLabel>Tipo de dado</InputLabel>
              <Select
                label="Tipo de dado"
                value={parameterEdit.dataType}
                onChange={(event) =>
                  setParameterEdit({
                    ...parameterEdit,
                    dataType: event.target.value as SensorDataType,
                  })
                }
              >
                <MenuItem value="NUMERIC">Numerico</MenuItem>
                <MenuItem value="BOOLEAN">Booleano</MenuItem>
                <MenuItem value="TEXT">Texto</MenuItem>
              </Select>
            </FormControl>
            {parameterEdit.dataType === "NUMERIC" && (
              <TextField
                label="Unidade"
                value={parameterEdit.numericUnit}
                onChange={(event) =>
                  setParameterEdit({ ...parameterEdit, numericUnit: event.target.value })
                }
                placeholder="C, %, ppm..."
              />
            )}
            <ErrorMessage error={updateParameter.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setEditingParameter(null)}
            disabled={updateParameter.isPending}
          >
            Cancelar
          </Button>
          <Button
            variant="contained"
            onClick={() => updateParameter.mutate()}
            disabled={updateParameter.isPending || !parameterEdit.nome.trim()}
          >
            Salvar alteracoes
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function RuleGroupsPanel() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [selectedGroupId, setSelectedGroupId] = useState("");
  const [group, setGroup] = useState({ nome: "", descricao: "" });
  const [typeName, setTypeName] = useState("");
  const [rule, setRule] = useState({
    parametroDefId: "",
    nome: "",
    descricao: "",
    operador: "GT",
    valor1: "",
    valor2: "",
    resultado: "ALERTA",
    severidade: "1",
    prioridade: "0",
  });
  const groups = useQuery({
    queryKey: ["rules", "groups"],
    queryFn: () => apiRequest<GrupoRegra[]>("/api/rules/groups", {}, session),
  });
  const types = useQuery({
    queryKey: ["sensor-admin", "types"],
    queryFn: () => apiRequest<TipoSensor[]>("/api/sensor-admin/types", {}, session),
  });
  const rules = useQuery({
    queryKey: ["rules", "groups", selectedGroupId],
    queryFn: () =>
      apiRequest<RegraParametro[]>(`/api/rules/groups/${selectedGroupId}/rules`, {}, session),
    enabled: Boolean(selectedGroupId),
  });
  const parameters = useMemo(
    () => types.data?.find((item) => item.nome === typeName)?.parametros ?? [],
    [types.data, typeName],
  );
  const selectedParameter = parameters.find((item) => item.id === rule.parametroDefId);

  const createGroup = useMutation({
    mutationFn: () =>
      apiRequest<GrupoRegra>(
        "/api/rules/groups",
        { method: "POST", body: JSON.stringify({ ...group, ativo: true }) },
        session,
      ),
    onSuccess: async (created) => {
      setGroup({ nome: "", descricao: "" });
      setSelectedGroupId(created.id);
      await queryClient.invalidateQueries({ queryKey: ["rules", "groups"] });
    },
  });
  const createRule = useMutation({
    mutationFn: () => {
      const numeric = selectedParameter?.dataType === "NUMERIC";
      const bool = selectedParameter?.dataType === "BOOLEAN";
      return apiRequest<RegraParametro>(
        `/api/rules/groups/${selectedGroupId}/rules`,
        {
          method: "POST",
          body: JSON.stringify({
            parametroDefId: rule.parametroDefId,
            nome: rule.nome,
            descricao: rule.descricao,
            operador: rule.operador,
            valorNumeric1: numeric && rule.valor1 !== "" ? Number(rule.valor1) : null,
            valorNumeric2: numeric && rule.valor2 !== "" ? Number(rule.valor2) : null,
            valorText: selectedParameter?.dataType === "TEXT" ? rule.valor1 : null,
            valorBoolean: bool ? rule.valor1 === "true" : null,
            resultado: rule.resultado,
            severidade: Number(rule.severidade),
            prioridade: Number(rule.prioridade),
            ativo: true,
          }),
        },
        session,
      );
    },
    onSuccess: async () => {
      setRule({
        parametroDefId: "",
        nome: "",
        descricao: "",
        operador: "GT",
        valor1: "",
        valor2: "",
        resultado: "ALERTA",
        severidade: "1",
        prioridade: "0",
      });
      await queryClient.invalidateQueries({ queryKey: ["rules", "groups", selectedGroupId] });
    },
  });

  const operators = selectedParameter?.dataType === "BOOLEAN"
    ? ["EQ", "NEQ"]
    : selectedParameter?.dataType === "TEXT"
      ? ["EQ", "NEQ", "CONTAINS"]
      : ["GT", "GTE", "LT", "LTE", "EQ", "NEQ", "BETWEEN", "OUTSIDE"];

  return (
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "320px 1fr" }, gap: 2 }}>
      <Stack spacing={2}>
        <Paper
          component="form"
          variant="outlined"
          sx={{ p: 2 }}
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            createGroup.mutate();
          }}
        >
          <Typography variant="h6">Novo grupo</Typography>
          <Stack spacing={1.5} sx={{ mt: 2 }}>
            <TextField
              label="Nome"
              value={group.nome}
              onChange={(event) => setGroup({ ...group, nome: event.target.value })}
              required
              size="small"
            />
            <TextField
              label="Descrição"
              value={group.descricao}
              onChange={(event) => setGroup({ ...group, descricao: event.target.value })}
              size="small"
            />
            <Button type="submit" variant="contained" disabled={createGroup.isPending}>
              Criar grupo
            </Button>
            <ErrorMessage error={createGroup.error} />
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 1 }}>
          {groups.data?.map((item) => (
            <Button
              key={item.id}
              fullWidth
              onClick={() => setSelectedGroupId(item.id)}
              variant={selectedGroupId === item.id ? "contained" : "text"}
              sx={{ justifyContent: "flex-start", mb: 0.5 }}
            >
              {item.nome}
            </Button>
          ))}
          <ErrorMessage error={groups.error} />
        </Paper>
      </Stack>

      {!selectedGroupId ? (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography color="text.secondary">Selecione ou crie um grupo de regras.</Typography>
        </Paper>
      ) : (
        <Stack spacing={2}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="h6">Regras do grupo</Typography>
            <Stack spacing={1} sx={{ mt: 2 }}>
              {rules.data?.map((item) => (
                <Box key={item.id} sx={{ p: 1.5, bgcolor: "grey.50", borderRadius: 1 }}>
                  <Typography fontWeight={600}>{item.nome}</Typography>
                  <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
                    <Typography variant="body2">{ruleExpression(item)}</Typography>
                    <Typography color="text.secondary">→</Typography>
                    <Chip
                      label={item.resultado}
                      size="small"
                      color={
                        ["ALERTA", "CRITICO", "INVALIDO"].includes(item.resultado)
                          ? "error"
                          : "success"
                      }
                    />
                  </Stack>
                </Box>
              ))}
              {rules.data?.length === 0 && (
                <Typography color="text.secondary">Nenhuma regra cadastrada.</Typography>
              )}
            </Stack>
            <ErrorMessage error={rules.error} />
          </Paper>
          <Paper
            component="form"
            variant="outlined"
            sx={{ p: 2 }}
            onSubmit={(event: FormEvent) => {
              event.preventDefault();
              createRule.mutate();
            }}
          >
            <Typography variant="h6">Adicionar regra</Typography>
            <Stack spacing={2} sx={{ mt: 2 }}>
              <Stack direction={{ xs: "column", lg: "row" }} spacing={1.5}>
              <FormControl required sx={{ minWidth: 180 }}>
                <InputLabel>Tipo de sensor</InputLabel>
                <Select
                  label="Tipo de sensor"
                  value={typeName}
                  onChange={(event) => {
                    setTypeName(event.target.value);
                    setRule({ ...rule, parametroDefId: "" });
                  }}
                >
                  {types.data?.map((item) => (
                    <MenuItem key={item.nome} value={item.nome}>{item.nome}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl required sx={{ minWidth: 220, flexGrow: 1 }}>
                <InputLabel>Parâmetro</InputLabel>
                <Select
                  label="Parâmetro"
                  value={rule.parametroDefId}
                  onChange={(event) => setRule({ ...rule, parametroDefId: event.target.value })}
                >
                  {parameters.map((item) => (
                    <MenuItem key={item.id} value={item.id}>
                      {item.nome} ({item.dataType})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              </Stack>
              <TextField
                label="Nome da regra"
                value={rule.nome}
                onChange={(event) => setRule({ ...rule, nome: event.target.value })}
                required
              />
              <TextField
                label="Descrição"
                value={rule.descricao}
                onChange={(event) => setRule({ ...rule, descricao: event.target.value })}
              />
              <Stack
                direction={{ xs: "column", md: "row" }}
                spacing={1.5}
                alignItems={{ md: "center" }}
                sx={{ p: 2, bgcolor: "grey.50", borderRadius: 1 }}
              >
              <Typography fontWeight={600} sx={{ minWidth: 140 }}>
                {selectedParameter?.nome || "Parâmetro"}
              </Typography>
              <FormControl required sx={{ minWidth: 190 }}>
                <InputLabel>Operador</InputLabel>
                <Select
                  label="Operador"
                  value={rule.operador}
                  onChange={(event) => setRule({ ...rule, operador: event.target.value })}
                >
                  {operators.map((operator) => (
                    <MenuItem key={operator} value={operator}>
                      {operatorLabels[operator] ?? operator}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              {selectedParameter?.dataType === "BOOLEAN" ? (
                <Stack direction="row" alignItems="center">
                  <Typography>Falso</Typography>
                  <Switch
                    checked={rule.valor1 === "true"}
                    onChange={(event) =>
                      setRule({ ...rule, valor1: String(event.target.checked) })
                    }
                  />
                  <Typography>Verdadeiro</Typography>
                </Stack>
              ) : (
                <TextField
                  label="Valor"
                  type={selectedParameter?.dataType === "NUMERIC" ? "number" : "text"}
                  value={rule.valor1}
                  onChange={(event) => setRule({ ...rule, valor1: event.target.value })}
                  required
                />
              )}
              {["BETWEEN", "OUTSIDE"].includes(rule.operador) && (
                <TextField
                  label="Segundo valor"
                  type="number"
                  value={rule.valor2}
                  onChange={(event) => setRule({ ...rule, valor2: event.target.value })}
                  required
                />
              )}
              <Typography color="text.secondary">→</Typography>
              <FormControl sx={{ minWidth: 150 }}>
                <InputLabel>Resultado</InputLabel>
                <Select
                  label="Resultado"
                  value={rule.resultado}
                  onChange={(event) => setRule({ ...rule, resultado: event.target.value })}
                >
                  {["IDEAL", "NORMAL", "ALERTA", "CRITICO", "INVALIDO"].map((item) => (
                    <MenuItem key={item} value={item}>{item}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              </Stack>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <TextField
                  label="Severidade"
                  type="number"
                  value={rule.severidade}
                  onChange={(event) => setRule({ ...rule, severidade: event.target.value })}
                  fullWidth
                />
                <TextField
                  label="Prioridade"
                  type="number"
                  value={rule.prioridade}
                  onChange={(event) => setRule({ ...rule, prioridade: event.target.value })}
                  fullWidth
                />
              </Stack>
              <Button type="submit" variant="contained" disabled={createRule.isPending}>
                Adicionar regra
              </Button>
              <ErrorMessage error={createRule.error} />
            </Stack>
          </Paper>
        </Stack>
      )}
    </Box>
  );
}
