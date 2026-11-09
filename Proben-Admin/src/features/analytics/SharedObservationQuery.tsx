import { SearchOutlined } from "@mui/icons-material";
import {
  Alert,
  AlertTitle,
  Button,
  Chip,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useState } from "react";
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

export function SharedObservationQuery({
  rooms,
  parameters,
  loadingCatalogs,
}: SharedObservationQueryProps) {
  const { session } = useAuth();
  const [roomId, setRoomId] = useState("");
  const [parameterId, setParameterId] = useState("");
  const [referenceAt, setReferenceAt] = useState("");
  const [result, setResult] = useState<SharedObservation>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    setLoading(true);
    setError("");
    setResult(undefined);
    try {
      const at = referenceAt ? new Date(referenceAt).toISOString() : undefined;
      setResult(await getSharedObservation(roomId, parameterId, at, session));
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
              Consulte a medição efetiva de uma sala e simule outro horário de
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
          <TextField
            select
            label="Sala"
            value={roomId}
            onChange={(event) => setRoomId(event.target.value)}
            disabled={loadingCatalogs || loading}
            fullWidth
            SelectProps={{ native: true }}
          >
            <option value="" />
            {rooms.map((room) => (
              <option key={room.id} value={room.id}>
                {room.nome} · {room.predioNome} · {room.campusNome}
              </option>
            ))}
          </TextField>
          <TextField
            select
            label="Parâmetro"
            value={parameterId}
            onChange={(event) => setParameterId(event.target.value)}
            disabled={loadingCatalogs || loading}
            fullWidth
            SelectProps={{ native: true }}
          >
            <option value="" />
            {parameters.map((parameter) => (
              <option key={parameter.id} value={parameter.id}>
                {parameter.nome} · {parameter.tipoNome}
              </option>
            ))}
          </TextField>
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
            disabled={!roomId || !parameterId || loading}
            onClick={() => void submit()}
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
