import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import {
  createSharedContext,
  listSharedContexts,
  listSharedIntegrations,
  replaceContextRooms,
  type SharedContext,
  type SharedIntegration,
} from "../api/sharedContexts";

export function SharedContextsPage() {
  const { session } = useAuth();
  const [items, setItems] = useState<SharedContext[]>([]);
  const [integrations, setIntegrations] = useState<
    Record<string, SharedIntegration[]>
  >({});
  const [name, setName] = useState("");
  const [rooms, setRooms] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const refresh = async () => {
    try {
      const contexts = await listSharedContexts(session);
      setItems(contexts);
      const pairs = await Promise.all(
        contexts.map(
          async (c) =>
            [c.id, await listSharedIntegrations(c.id, session)] as const,
        ),
      );
      setIntegrations(Object.fromEntries(pairs));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao carregar");
    }
  };
  useEffect(() => {
    void listSharedContexts(session)
      .then(async (contexts) => {
        setItems(contexts);
        const pairs = await Promise.all(
          contexts.map(
            async (c) =>
              [c.id, await listSharedIntegrations(c.id, session)] as const,
          ),
        );
        setIntegrations(Object.fromEntries(pairs));
      })
      .catch((e) =>
        setError(e instanceof Error ? e.message : "Falha ao carregar"),
      );
  }, [session]);
  const create = async () => {
    try {
      await createSharedContext(
        { nome: name, ativo: true, timezone: "America/Sao_Paulo" },
        session,
      );
      setName("");
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao criar");
    }
  };
  const saveRooms = async (c: SharedContext) => {
    try {
      await replaceContextRooms(
        c.id,
        (rooms[c.id] ?? c.compartimentoIds.join(","))
          .split(",")
          .map((x) => x.trim())
          .filter(Boolean),
        session,
      );
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha ao vincular");
    }
  };
  return (
    <Stack spacing={2}>
      <Typography variant="h4">Contextos compartilhados</Typography>
      <Typography color="text.secondary">
        Cobertura de salas e estado da coleta externa. Credenciais e
        configuração detalhada ficam protegidas na API.
      </Typography>
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
              onChange={(e) => setName(e.target.value)}
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
      {items.map((c) => (
        <Card key={c.id}>
          <CardContent>
            <Stack spacing={1}>
              <Box>
                <Typography variant="h6">{c.nome}</Typography>
                <Typography variant="caption">
                  {c.timezone} · {c.ativo ? "ativo" : "inativo"}
                </Typography>
              </Box>
              <TextField
                label="IDs dos compartimentos (separados por vírgula)"
                value={rooms[c.id] ?? c.compartimentoIds.join(", ")}
                onChange={(e) =>
                  setRooms((v) => ({ ...v, [c.id]: e.target.value }))
                }
              />
              <Button
                sx={{ alignSelf: "flex-start" }}
                onClick={() => void saveRooms(c)}
              >
                Salvar vínculos
              </Button>
              {(integrations[c.id] ?? []).map((i) => (
                <Alert key={i.id} severity={i.lastError ? "warning" : "info"}>
                  {i.nome} · sensor {i.sensorExternalId} · última observação:{" "}
                  {i.lastSuccessAt
                    ? new Date(i.lastSuccessAt).toLocaleString()
                    : "ainda não coletada"}
                  {i.lastError ? ` · ${i.lastError}` : ""}
                </Alert>
              ))}
              {(integrations[c.id] ?? []).length === 0 && (
                <Typography color="text.secondary">
                  Fonte ainda não configurada.
                </Typography>
              )}
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}
