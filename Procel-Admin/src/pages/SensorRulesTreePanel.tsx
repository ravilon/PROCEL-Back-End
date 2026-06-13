import {
  AccountTreeOutlined,
  AddOutlined,
  DeleteOutlined,
  ExpandMoreOutlined,
  MeetingRoomOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputAdornment,
  InputLabel,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type {
  GrupoRegra,
  RegraParametro,
  Sensor,
  SensorGrupoRegra,
} from "../types";

const operatorLabels: Record<string, string> = {
  GT: ">",
  GTE: "≥",
  LT: "<",
  LTE: "≤",
  EQ: "=",
  NEQ: "≠",
  BETWEEN: "entre",
  OUTSIDE: "fora",
  CONTAINS: "contém",
};

function ruleExpression(rule: RegraParametro) {
  const firstValue = rule.valorNumeric1 ?? rule.valorText ?? rule.valorBoolean;
  return `${rule.parametroNome} ${operatorLabels[rule.operador] ?? rule.operador} ${
    firstValue ?? ""
  }${rule.valorNumeric2 != null ? ` e ${rule.valorNumeric2}` : ""}`;
}

function formatDate(value?: string | null) {
  return value ? new Date(value).toLocaleString("pt-BR") : null;
}

export function SensorRulesTreePanel() {
  const { session } = useAuth();
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [showHidden, setShowHidden] = useState(false);

  useEffect(() => {
    const timeout = window.setTimeout(
      () => setDebouncedSearch(search.trim()),
      300,
    );
    return () => window.clearTimeout(timeout);
  }, [search]);

  const sensors = useQuery({
    queryKey: ["sensor-rules-tree", "sensors", debouncedSearch, showHidden],
    queryFn: () => {
      const params = new URLSearchParams({
        includeHidden: String(showHidden),
      });
      if (debouncedSearch) params.set("q", debouncedSearch);
      return apiRequest<Sensor[]>(
        `/api/catalog/sensores?${params.toString()}`,
        {},
        session,
      );
    },
  });

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack
          direction={{ xs: "column", md: "row" }}
          spacing={2}
          alignItems={{ md: "center" }}
        >
          <TextField
            fullWidth
            label="Buscar sensor"
            placeholder="Nome, ID, tipo ou sala"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SensorsOutlined />
                  </InputAdornment>
                ),
              },
            }}
          />
          <FormControlLabel
            control={
              <Switch
                checked={showHidden}
                onChange={(event) => setShowHidden(event.target.checked)}
              />
            }
            label="Mostrar sensores ocultos"
            sx={{ flexShrink: 0 }}
          />
        </Stack>
      </Paper>

      {sensors.isLoading && (
        <Stack direction="row" spacing={1} alignItems="center">
          <CircularProgress size={20} />
          <Typography color="text.secondary">Carregando sensores...</Typography>
        </Stack>
      )}
      {sensors.isError && <Alert severity="error">{sensors.error.message}</Alert>}
      {sensors.data?.length === 0 && (
        <Alert severity="info">Nenhum sensor encontrado para a busca informada.</Alert>
      )}
      <Stack spacing={1}>
        {sensors.data?.map((sensor) => (
          <SensorTreeNode key={sensor.externalId} sensor={sensor} />
        ))}
      </Stack>
    </Stack>
  );
}

function SensorTreeNode({ sensor }: { sensor: Sensor }) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [groupId, setGroupId] = useState("");
  const [status, setStatus] = useState("ATIVO");
  const [validFrom, setValidFrom] = useState("");
  const [validUntil, setValidUntil] = useState("");
  const links = useQuery({
    queryKey: ["sensor-rules-tree", "links", sensor.externalId],
    queryFn: () =>
      apiRequest<SensorGrupoRegra[]>(
        `/api/rules/sensors/${encodeURIComponent(sensor.externalId)}/groups`,
        {},
        session,
      ),
    enabled: expanded,
  });
  const groups = useQuery({
    queryKey: ["rules", "groups"],
    queryFn: () => apiRequest<GrupoRegra[]>("/api/rules/groups", {}, session),
    enabled: linkDialogOpen,
  });
  const linkGroup = useMutation({
    mutationFn: () =>
      apiRequest<SensorGrupoRegra>(
        `/api/rules/sensors/${encodeURIComponent(sensor.externalId)}/groups`,
        {
          method: "POST",
          body: JSON.stringify({
            grupoRegraId: groupId,
            status,
            validoDe: validFrom ? new Date(validFrom).toISOString() : null,
            validoAte: validUntil ? new Date(validUntil).toISOString() : null,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setLinkDialogOpen(false);
      setGroupId("");
      setStatus("ATIVO");
      setValidFrom("");
      setValidUntil("");
      await queryClient.invalidateQueries({
        queryKey: ["sensor-rules-tree", "links", sensor.externalId],
      });
    },
  });
  const availableGroups =
    groups.data?.filter(
      (group) =>
        group.ativo &&
        !links.data?.some((link) => link.grupoRegraId === group.id),
    ) ?? [];
  const selectedGroup =
    availableGroups.find((group) => group.id === groupId) ?? null;

  return (
    <>
      <Accordion
        expanded={expanded}
        onChange={(_, value) => setExpanded(value)}
        disableGutters
      >
        <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            spacing={1}
            alignItems={{ sm: "center" }}
            useFlexGap
            flexWrap="wrap"
          >
            <SensorsOutlined color={sensor.ativo ? "primary" : "disabled"} />
            <Typography fontWeight={600}>{sensor.nome}</Typography>
            <Chip size="small" label={sensor.externalId} />
            <Chip size="small" variant="outlined" label={sensor.tipoNome} />
            {!sensor.ativo && <Chip size="small" color="warning" label="Oculto" />}
          </Stack>
        </AccordionSummary>
        <AccordionDetails>
          <Stack
            spacing={1.5}
            sx={{ ml: { xs: 0, sm: 2 }, pl: 2, borderLeft: 2, borderColor: "divider" }}
          >
            <Stack
              direction={{ xs: "column", sm: "row" }}
              spacing={1}
              alignItems={{ sm: "center" }}
              justifyContent="space-between"
            >
              <Stack direction="row" spacing={1} alignItems="center">
                <MeetingRoomOutlined color="action" />
                <Box>
                  <Typography variant="body2" fontWeight={600}>
                    Sala: {sensor.compartimentoNome}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    ID {sensor.compartimentoId}
                  </Typography>
                </Box>
              </Stack>
              <Button
                size="small"
                variant="outlined"
                startIcon={<AddOutlined />}
                disabled={!sensor.ativo}
                onClick={() => setLinkDialogOpen(true)}
              >
                Vincular grupo de regras
              </Button>
            </Stack>

            {!sensor.ativo && (
              <Alert severity="info">
                Reative o sensor para inserir novos vínculos de regras.
              </Alert>
            )}
            {links.isLoading && <CircularProgress size={20} />}
            {links.isError && <Alert severity="error">{links.error.message}</Alert>}
            {links.data?.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                Nenhum grupo de regras vinculado.
              </Typography>
            )}
            {links.data?.map((link) => (
              <RuleGroupTreeNode
                key={link.id}
                link={link}
                sensorExternalId={sensor.externalId}
              />
            ))}
          </Stack>
        </AccordionDetails>
      </Accordion>

      <Dialog
        open={linkDialogOpen}
        onClose={() => setLinkDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Vincular regras ao sensor {sensor.nome}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Alert severity="info">
              Somente grupos compatíveis com o tipo {sensor.tipoNome} podem ser
              ativados. Conflitos com regras já vinculadas serão recusados pela API.
            </Alert>
            <Autocomplete
              options={availableGroups}
              value={selectedGroup}
              loading={groups.isLoading}
              getOptionLabel={(group) => group.nome}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              onChange={(_, group) => setGroupId(group?.id ?? "")}
              noOptionsText="Nenhum grupo disponível"
              loadingText="Carregando grupos..."
              renderOption={(props, group) => (
                <Box component="li" {...props} key={group.id}>
                  <Box>
                    <Typography variant="body2" fontWeight={600}>
                      {group.nome}
                    </Typography>
                    {group.descricao && (
                      <Typography variant="caption" color="text.secondary">
                        {group.descricao}
                      </Typography>
                    )}
                  </Box>
                </Box>
              )}
              renderInput={(params) => (
                <TextField
                  {...params}
                  required
                  label="Grupo de regras"
                  placeholder="Digite para buscar um grupo"
                  error={groups.isError}
                  helperText={groups.isError ? groups.error.message : undefined}
                />
              )}
            />
            <FormControl>
              <InputLabel>Status do vínculo</InputLabel>
              <Select
                label="Status do vínculo"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <MenuItem value="ATIVO">Ativo</MenuItem>
                <MenuItem value="AGENDADO">Agendado</MenuItem>
                <MenuItem value="RASCUNHO">Rascunho</MenuItem>
              </Select>
            </FormControl>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                fullWidth
                label="Válido a partir de"
                type="datetime-local"
                value={validFrom}
                onChange={(event) => setValidFrom(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
              <TextField
                fullWidth
                label="Válido até"
                type="datetime-local"
                value={validUntil}
                onChange={(event) => setValidUntil(event.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>
            {linkGroup.isError && (
              <Alert severity="error">{linkGroup.error.message}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setLinkDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => linkGroup.mutate()}
            disabled={linkGroup.isPending || !groupId}
          >
            {linkGroup.isPending ? "Vinculando..." : "Vincular regras"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function RuleGroupTreeNode({
  link,
  sensorExternalId,
}: {
  link: SensorGrupoRegra;
  sensorExternalId: string;
}) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const rules = useQuery({
    queryKey: ["rules", "groups", link.grupoRegraId],
    queryFn: () =>
      apiRequest<RegraParametro[]>(
        `/api/rules/groups/${link.grupoRegraId}/rules`,
        {},
        session,
      ),
    enabled: expanded,
  });
  const unlinkGroup = useMutation({
    mutationFn: () =>
      apiRequest<void>(
        `/api/rules/sensors/${encodeURIComponent(sensorExternalId)}/groups/${link.id}`,
        { method: "DELETE" },
        session,
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["sensor-rules-tree", "links", sensorExternalId],
      });
    },
  });
  const handleUnlink = () => {
    if (
      window.confirm(
        `Remover o vínculo do grupo "${link.grupoRegraNome}" deste sensor?`,
      )
    ) {
      unlinkGroup.mutate();
    }
  };

  return (
    <Accordion
      variant="outlined"
      expanded={expanded}
      onChange={(_, value) => setExpanded(value)}
      disableGutters
    >
      <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
        <Stack spacing={0.5} sx={{ flexGrow: 1 }}>
          <Stack direction="row" spacing={1} alignItems="center" useFlexGap flexWrap="wrap">
            <AccountTreeOutlined color="action" />
            <Typography variant="body2" fontWeight={600}>
              {link.grupoRegraNome}
            </Typography>
            <Chip size="small" label={link.status} />
          </Stack>
          {(link.validoDe || link.validoAte) && (
            <Typography variant="caption" color="text.secondary">
              Vigência: {formatDate(link.validoDe) ?? "imediata"} até{" "}
              {formatDate(link.validoAte) ?? "sem término"}
            </Typography>
          )}
        </Stack>
        <IconButton
          size="small"
          color="error"
          aria-label={`Remover vínculo com ${link.grupoRegraNome}`}
          title="Remover vínculo"
          disabled={unlinkGroup.isPending}
          onClick={(event) => {
            event.stopPropagation();
            handleUnlink();
          }}
          onFocus={(event) => event.stopPropagation()}
          sx={{ mr: 1 }}
        >
          {unlinkGroup.isPending ? (
            <CircularProgress size={18} />
          ) : (
            <DeleteOutlined fontSize="small" />
          )}
        </IconButton>
      </AccordionSummary>
      <AccordionDetails>
        <Stack spacing={1} sx={{ pl: 2, borderLeft: 2, borderColor: "divider" }}>
          {unlinkGroup.isError && (
            <Alert severity="error">{unlinkGroup.error.message}</Alert>
          )}
          {rules.isLoading && <CircularProgress size={20} />}
          {rules.isError && <Alert severity="error">{rules.error.message}</Alert>}
          {rules.data?.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              Nenhuma regra cadastrada neste grupo.
            </Typography>
          )}
          {rules.data?.map((rule) => (
            <Box key={rule.id} sx={{ p: 1.25, bgcolor: "grey.50", borderRadius: 1 }}>
              <Stack
                direction={{ xs: "column", sm: "row" }}
                spacing={1}
                justifyContent="space-between"
              >
                <Box>
                  <Typography variant="body2" fontWeight={600}>
                    {rule.nome}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {ruleExpression(rule)}
                  </Typography>
                </Box>
                <Stack direction="row" spacing={0.75} alignItems="center">
                  <Chip size="small" label={rule.resultado} />
                  {!rule.ativo && <Chip size="small" label="Inativa" />}
                </Stack>
              </Stack>
            </Box>
          ))}
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}
