import { SearchOutlined } from "@mui/icons-material";
import {
  Alert,
  AlertTitle,
  Autocomplete,
  Button,
  Chip,
  Paper,
  Stack,
  TextField,
  Typography,
  createFilterOptions,
} from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { listCompartimentos } from "../../api/catalog";
import {
  getSharedObservation,
  type SharedObservation,
} from "../../api/sharedContexts";
import { useAuth } from "../../auth/AuthContext";
import type { Compartimento } from "../../types/catalog";
import type { ParametroDef } from "../../types/sensors";

interface SharedObservationQueryProps {
  rooms: Compartimento[];
  parameters: ParametroDef[];
  loadingCatalogs: boolean;
}

const statusColor = {
  AVAILABLE: "success",
  STALE: "warning",
  MISSING: "default",
} as const;

const roomFilter = createFilterOptions<Compartimento>({
  stringify: (room) =>
    [
      room.id,
      room.nome,
      room.predioId,
      room.predioNome,
      room.campusNome,
      room.unidadeNome,
      room.tipo,
    ].join(" "),
});

const parameterFilter = createFilterOptions<ParametroDef>({
  stringify: (parameter) =>
    [
      parameter.nome,
      parameter.descricao,
      parameter.tipoNome,
      parameter.numericUnit,
      parameter.dataType,
    ].join(" "),
});

export function SharedObservationQuery({
  rooms,
  parameters,
  loadingCatalogs,
}: SharedObservationQueryProps) {
  const { session } = useAuth();
  const [selectedRoom, setSelectedRoom] = useState<Compartimento | null>(null);
  const [selectedParameter, setSelectedParameter] =
    useState<ParametroDef | null>(null);
  const [roomInput, setRoomInput] = useState("");
  const [remoteRooms, setRemoteRooms] = useState<Compartimento[]>([]);
  const [searchingRooms, setSearchingRooms] = useState(false);
  const [roomSearchError, setRoomSearchError] = useState("");
  const [referenceAt, setReferenceAt] = useState("");
  const [result, setResult] = useState<SharedObservation>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const query = roomInput.trim();
    if (!query || (selectedRoom && roomLabel(selectedRoom) === query)) {
      return;
    }

    let active = true;
    const timer = window.setTimeout(() => {
      setSearchingRooms(true);
      setRoomSearchError("");
      void listCompartimentos({ q: query }, session)
        .then((found) => {
          if (active) setRemoteRooms(found);
        })
        .catch(() => {
          if (active) {
            setRemoteRooms([]);
            setRoomSearchError("Não foi possível pesquisar as salas.");
          }
        })
        .finally(() => {
          if (active) setSearchingRooms(false);
        });
    }, 300);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [roomInput, selectedRoom, session]);

  const roomOptions = useMemo(() => {
    const hasActiveSearch =
      roomInput.trim() &&
      (!selectedRoom || roomLabel(selectedRoom) !== roomInput.trim());
    const available =
      hasActiveSearch && remoteRooms.length ? remoteRooms : rooms;
    const unique = new Map<string, Compartimento>();
    if (selectedRoom) unique.set(selectedRoom.id, selectedRoom);
    for (const room of available) unique.set(room.id, room);
    return [...unique.values()];
  }, [remoteRooms, roomInput, rooms, selectedRoom]);

  const submit = async () => {
    if (!selectedRoom || !selectedParameter) return;
    setLoading(true);
    setError("");
    setResult(undefined);
    try {
      const at = referenceAt ? new Date(referenceAt).toISOString() : undefined;
      setResult(
        await getSharedObservation(
          selectedRoom.id,
          selectedParameter.id,
          at,
          session,
        ),
      );
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : "Falha ao consultar fonte",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          justifyContent="space-between"
          gap={1}
        >
          <div>
            <Typography variant="h6">
              Disponibilidade da fonte compartilhada
            </Typography>
            <Typography color="text.secondary" variant="body2">
              Pesquise por ID, nome ou localização e simule outro horário de
              referência para validar a janela de validade.
            </Typography>
          </div>
          {result && (
            <Chip
              label={result.status}
              color={statusColor[result.status]}
              variant={result.status === "MISSING" ? "outlined" : "filled"}
            />
          )}
        </Stack>
        <Stack direction={{ xs: "column", md: "row" }} spacing={1}>
          <Autocomplete
            fullWidth
            options={roomOptions}
            value={selectedRoom}
            inputValue={roomInput}
            loading={searchingRooms}
            disabled={loading}
            filterOptions={roomFilter}
            getOptionLabel={roomLabel}
            isOptionEqualToValue={(option, selected) =>
              option.id === selected.id
            }
            onInputChange={(_, value) => {
              setRoomInput(value);
              setRoomSearchError("");
              if (!value) setSearchingRooms(false);
            }}
            onChange={(_, room) => {
              setSelectedRoom(room);
              setSearchingRooms(false);
              setResult(undefined);
              setError("");
            }}
            noOptionsText="Nenhuma sala encontrada"
            loadingText="Pesquisando salas..."
            renderOption={(props, room) => (
              <li {...props} key={room.id}>
                <Stack>
                  <Typography variant="body2">
                    {room.id} — {room.nome}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {room.predioNome} · {room.campusNome} · {room.unidadeNome}
                  </Typography>
                </Stack>
              </li>
            )}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Sala"
                placeholder="Digite o ID, nome, prédio ou campus"
                error={Boolean(roomSearchError)}
                helperText={
                  roomSearchError ||
                  "A busca consulta o catálogo completo enquanto você digita."
                }
              />
            )}
          />
          <Autocomplete
            fullWidth
            options={parameters}
            value={selectedParameter}
            loading={loadingCatalogs}
            disabled={loadingCatalogs || loading}
            filterOptions={parameterFilter}
            getOptionLabel={parameterLabel}
            isOptionEqualToValue={(option, selected) =>
              option.id === selected.id
            }
            onChange={(_, parameter) => {
              setSelectedParameter(parameter);
              setResult(undefined);
              setError("");
            }}
            noOptionsText="Nenhum parâmetro encontrado"
            renderOption={(props, parameter) => (
              <li {...props} key={parameter.id}>
                <Stack>
                  <Typography variant="body2">{parameter.nome}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {parameter.tipoNome} · {parameter.dataType}
                    {parameter.numericUnit ? ` · ${parameter.numericUnit}` : ""}
                  </Typography>
                </Stack>
              </li>
            )}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Parâmetro"
                placeholder="Digite o nome, tipo ou unidade"
                helperText="A pesquisa considera nome, tipo, descrição e unidade."
              />
            )}
          />
          <TextField
            type="datetime-local"
            label="Horário de referência (opcional)"
            value={referenceAt}
            onChange={(event) => setReferenceAt(event.target.value)}
            disabled={loading}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
          <Button
            variant="contained"
            startIcon={<SearchOutlined />}
            disabled={!selectedRoom || !selectedParameter || loading}
            onClick={() => void submit()}
            sx={{ minWidth: 130 }}
          >
            Consultar
          </Button>
        </Stack>
        {error && <Alert severity="error">{error}</Alert>}
        {result && (
          <Alert
            severity={
              result.status === "AVAILABLE"
                ? "success"
                : result.status === "STALE"
                  ? "warning"
                  : "info"
            }
          >
            <AlertTitle>{result.status}</AlertTitle>
            {result.status === "MISSING" ? (
              "Nenhuma medição elegível foi encontrada para esta sala e parâmetro."
            ) : (
              <Stack spacing={0.5}>
                <Typography variant="body2">
                  Valor: {result.value ?? "—"} {result.unit ?? ""}
                </Typography>
                <Typography variant="body2">
                  Observada em: {formatDate(result.observedAt)}
                </Typography>
                <Typography variant="body2">
                  Válida até: {formatDate(result.validUntil)}
                </Typography>
                <Typography variant="body2">
                  Sensor: {result.sensorExternalId ?? "—"} · origem:{" "}
                  {result.source ?? "—"}
                </Typography>
              </Stack>
            )}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleString() : "—";
}

function roomLabel(room: Compartimento) {
  return `${room.id} — ${room.nome}`;
}

function parameterLabel(parameter: ParametroDef) {
  return parameter.nome;
}
