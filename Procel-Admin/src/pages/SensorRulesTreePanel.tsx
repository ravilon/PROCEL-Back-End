import {
  AccountTreeOutlined,
  ExpandMoreOutlined,
  MeetingRoomOutlined,
  SensorsOutlined,
} from "@mui/icons-material";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Chip,
  CircularProgress,
  FormControlLabel,
  InputAdornment,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type {
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
  const [expanded, setExpanded] = useState(false);
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

  return (
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

          {links.isLoading && <CircularProgress size={20} />}
          {links.isError && <Alert severity="error">{links.error.message}</Alert>}
          {links.data?.length === 0 && (
            <Typography variant="body2" color="text.secondary">
              Nenhum grupo de regras vinculado.
            </Typography>
          )}
          {links.data?.map((link) => (
            <RuleGroupTreeNode key={link.id} link={link} />
          ))}
        </Stack>
      </AccordionDetails>
    </Accordion>
  );
}

function RuleGroupTreeNode({ link }: { link: SensorGrupoRegra }) {
  const { session } = useAuth();
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

  return (
    <Accordion
      variant="outlined"
      expanded={expanded}
      onChange={(_, value) => setExpanded(value)}
      disableGutters
    >
      <AccordionSummary expandIcon={<ExpandMoreOutlined />}>
        <Stack spacing={0.5}>
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
      </AccordionSummary>
      <AccordionDetails>
        <Stack spacing={1} sx={{ pl: 2, borderLeft: 2, borderColor: "divider" }}>
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
