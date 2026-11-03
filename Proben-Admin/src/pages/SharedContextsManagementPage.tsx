import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControlLabel,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import {
  getCompartimentoFilterOptions,
  listCompartimentos,
} from "../api/catalog";
import {
  createSharedContext,
  listSharedContexts,
  listSharedIntegrations,
  replaceContextRooms,
  type SharedContext,
  type SharedIntegration,
} from "../api/sharedContexts";
import { useAuth } from "../auth/AuthContext";
import type {
  Compartimento,
  CompartimentoFilterOptions,
} from "../types/catalog";

export function SharedContextsManagementPage({
  embedded = false,
}: {
  embedded?: boolean;
}) {
  const { session } = useAuth();
  const [items, setItems] = useState<SharedContext[]>([]);
  const [filterOptions, setFilterOptions] =
    useState<CompartimentoFilterOptions>();
  const [integrations, setIntegrations] = useState<
    Record<string, SharedIntegration[]>
  >({});
  const [name, setName] = useState("");
  const [error, setError] = useState("");

  const refresh = async () => {
    try {
      const contexts = await listSharedContexts(session);
      setItems(contexts);
      const pairs = await Promise.all(
        contexts.map(
          async (context) =>
            [
              context.id,
              await listSharedIntegrations(context.id, session),
            ] as const,
        ),
      );
      setIntegrations(Object.fromEntries(pairs));
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Falha ao carregar contextos",
      );
    }
  };

  useEffect(() => {
    void getCompartimentoFilterOptions(session)
      .then(setFilterOptions)
      .catch((caught) =>
        setError(
          caught instanceof Error
            ? caught.message
            : "Falha ao carregar filtros de salas",
        ),
      );
    void Promise.resolve().then(refresh);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session]);

  const create = async () => {
    try {
      await createSharedContext(
        { nome: name.trim(), ativo: true, timezone: "America/Sao_Paulo" },
        session,
      );
      setName("");
      await refresh();
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Falha ao criar contexto",
      );
    }
  };

  return (
    <Stack spacing={2}>
      {!embedded && (
        <Box>
          <Typography variant="h4">Contextos compartilhados</Typography>
          <Typography color="text.secondary">
            Agrupe salas que utilizam a mesma fonte externa. A localização de
            referência não vincula salas automaticamente.
          </Typography>
        </Box>
      )}
      {error && (
        <Alert severity="error" onClose={() => setError("")}>
          {error}
        </Alert>
      )}
      <Card>
        <CardContent>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <TextField
              label="Nome do contexto"
              value={name}
              onChange={(event) => setName(event.target.value)}
              fullWidth
            />
            <Button
              variant="contained"
              disabled={!name.trim()}
              onClick={() => void create()}
            >
              Cadastrar
            </Button>
          </Stack>
        </CardContent>
      </Card>
      {items.map((context) => (
        <ContextRoomsCard
          key={`${context.id}:${context.compartimentoIds.join(",")}`}
          context={context}
          filterOptions={filterOptions}
          integrations={integrations[context.id] ?? []}
          onSaved={refresh}
          onError={setError}
        />
      ))}
    </Stack>
  );
}

interface ContextRoomsCardProps {
  context: SharedContext;
  filterOptions?: CompartimentoFilterOptions;
  integrations: SharedIntegration[];
  onSaved: () => Promise<void>;
  onError: (message: string) => void;
}

function ContextRoomsCard({
  context,
  filterOptions,
  integrations,
  onSaved,
  onError,
}: ContextRoomsCardProps) {
  const { session } = useAuth();
  const [selected, setSelected] = useState(
    () => new Set(context.compartimentoIds),
  );
  const [campus, setCampus] = useState("");
  const [building, setBuilding] = useState("");
  const [query, setQuery] = useState("");
  const [saving, setSaving] = useState(false);
  const [rooms, setRooms] = useState<Compartimento[]>([]);

  useEffect(() => {
    let cancelled = false;
    void listCompartimentos({ campus, predio: building, q: query }, session)
      .then((result) => {
        if (!cancelled) setRooms(result);
      })
      .catch((caught) => {
        if (!cancelled)
          onError(
            caught instanceof Error
              ? caught.message
              : "Falha ao carregar salas",
          );
      });
    return () => {
      cancelled = true;
    };
  }, [session, campus, building, query, onError]);

  const allFilteredSelected =
    rooms.length > 0 && rooms.every((room) => selected.has(room.id));
  const someFilteredSelected = rooms.some((room) => selected.has(room.id));
  const setFilteredSelection = (checked: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      rooms.forEach((room) => {
        if (checked) next.add(room.id);
        else next.delete(room.id);
      });
      return next;
    });
  const save = async () => {
    setSaving(true);
    try {
      await replaceContextRooms(context.id, [...selected], session);
      await onSaved();
    } catch (caught) {
      onError(
        caught instanceof Error ? caught.message : "Falha ao salvar vínculos",
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            justifyContent="space-between"
            spacing={1}
          >
            <Box>
              <Typography variant="h6">{context.nome}</Typography>
              <Typography variant="caption" color="text.secondary">
                {context.timezone} · {context.ativo ? "ativo" : "inativo"}
              </Typography>
            </Box>
            <Chip
              label={`${selected.size} sala${selected.size === 1 ? "" : "s"} selecionada${selected.size === 1 ? "" : "s"}`}
            />
          </Stack>
          <Divider />
          <Typography fontWeight={600}>
            Salas atendidas pelo contexto
          </Typography>
          <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
            <TextField
              select
              label="Campus"
              value={campus}
              onChange={(event) => {
                setCampus(event.target.value);
                setBuilding("");
              }}
              sx={{ minWidth: 220 }}
            >
              <MenuItem value="">Todos os campi</MenuItem>
              {filterOptions?.campi.map((item) => (
                <MenuItem key={item} value={item}>
                  {item}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Prédio"
              value={building}
              onChange={(event) => setBuilding(event.target.value)}
              sx={{ minWidth: 220 }}
            >
              <MenuItem value="">Todos os prédios</MenuItem>
              {filterOptions?.predios.map((item) => (
                <MenuItem key={item} value={item}>
                  {item}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Buscar sala"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Nome ou identificador"
              fullWidth
            />
          </Stack>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            alignItems={{ sm: "center" }}
            justifyContent="space-between"
            spacing={1}
          >
            <FormControlLabel
              control={
                <Checkbox
                  checked={allFilteredSelected}
                  indeterminate={!allFilteredSelected && someFilteredSelected}
                  onChange={(_, checked) => setFilteredSelection(checked)}
                />
              }
              label={`Selecionar as ${rooms.length} salas filtradas`}
            />
            <Button
              size="small"
              disabled={!someFilteredSelected}
              onClick={() => setFilteredSelection(false)}
            >
              Remover filtradas
            </Button>
          </Stack>
          <Box
            sx={{
              maxHeight: 360,
              overflowY: "auto",
              border: 1,
              borderColor: "divider",
              borderRadius: 1,
            }}
          >
            {rooms.map((room, index) => (
              <Box
                key={room.id}
                sx={{
                  px: 1.5,
                  py: 0.75,
                  borderTop: index ? 1 : 0,
                  borderColor: "divider",
                }}
              >
                <FormControlLabel
                  sx={{ width: "100%", m: 0 }}
                  control={
                    <Checkbox
                      checked={selected.has(room.id)}
                      onChange={(_, checked) =>
                        setSelected((current) => {
                          const next = new Set(current);
                          if (checked) next.add(room.id);
                          else next.delete(room.id);
                          return next;
                        })
                      }
                    />
                  }
                  label={
                    <Stack
                      direction={{ xs: "column", sm: "row" }}
                      spacing={{ sm: 1 }}
                      alignItems={{ sm: "center" }}
                    >
                      <Typography fontWeight={500}>{room.nome}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {room.predioNome} · {room.id}
                      </Typography>
                      <Chip
                        size="small"
                        variant="outlined"
                        label={room.campusNome}
                      />
                    </Stack>
                  }
                />
              </Box>
            ))}
            {rooms.length === 0 && (
              <Typography color="text.secondary" sx={{ p: 2 }}>
                Nenhuma sala encontrada para os filtros.
              </Typography>
            )}
          </Box>
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button
              onClick={() => setSelected(new Set(context.compartimentoIds))}
              disabled={saving}
            >
              Descartar alterações
            </Button>
            <Button
              variant="contained"
              onClick={() => void save()}
              disabled={saving}
            >
              {saving ? "Salvando..." : "Salvar vínculos"}
            </Button>
          </Stack>
          <Divider />
          {integrations.map((integration) => (
            <Alert
              key={integration.id}
              severity={integration.lastError ? "warning" : "info"}
            >
              {integration.nome} · sensor {integration.sensorExternalId} ·
              última observação:{" "}
              {integration.lastSuccessAt
                ? new Date(integration.lastSuccessAt).toLocaleString()
                : "ainda não coletada"}
              {integration.lastError ? ` · ${integration.lastError}` : ""}
            </Alert>
          ))}
          {integrations.length === 0 && (
            <Typography color="text.secondary">
              Fonte ainda não configurada.
            </Typography>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
