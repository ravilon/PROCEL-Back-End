import { AddOutlined, AccountTreeOutlined, EditOutlined } from "@mui/icons-material";
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
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type { Missao } from "../types";

export function MissionsAdminPage() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    titulo: "",
    descricao: "",
    tipo: "Individual",
    value: "0",
    ativo: true,
    parentId: "",
  });
  const missions = useQuery({
    queryKey: ["missions"],
    queryFn: () => apiRequest<Missao[]>("/api/missoes", {}, session),
  });
  const roots = useMemo(
    () => missions.data?.filter((mission) => !mission.parentId) ?? [],
    [missions.data],
  );
  const createMission = useMutation({
    mutationFn: () =>
      apiRequest<Missao>(
        "/api/missoes",
        {
          method: "POST",
          body: JSON.stringify({
            ...form,
            value: Number(form.value),
            parentId: form.parentId || null,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setForm({
        titulo: "",
        descricao: "",
        tipo: "Individual",
        value: "0",
        ativo: true,
        parentId: "",
      });
      await queryClient.invalidateQueries({ queryKey: ["missions"] });
    },
  });

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Missões</Typography>
        <Typography color="text.secondary">
          Cadastre objetivos e organize etapas filhas em uma árvore de missões.
        </Typography>
      </Box>
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "380px 1fr" }, gap: 2 }}>
        <Paper
          component="form"
          variant="outlined"
          sx={{ p: 2, alignSelf: "start" }}
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            createMission.mutate();
          }}
        >
          <Typography variant="h6">Nova missão</Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <TextField
              label="Título"
              value={form.titulo}
              onChange={(event) => setForm({ ...form, titulo: event.target.value })}
              required
            />
            <TextField
              label="Descrição"
              value={form.descricao}
              onChange={(event) => setForm({ ...form, descricao: event.target.value })}
              multiline
              minRows={3}
            />
            <FormControl>
              <InputLabel>Missão pai</InputLabel>
              <Select
                label="Missão pai"
                value={form.parentId}
                onChange={(event) => setForm({ ...form, parentId: event.target.value })}
              >
                <MenuItem value="">Nenhuma, missão raiz</MenuItem>
                {missions.data?.map((mission) => (
                  <MenuItem key={mission.id} value={mission.id}>{mission.titulo}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Tipo"
              value={form.tipo}
              onChange={(event) => setForm({ ...form, tipo: event.target.value })}
            />
            <TextField
              label="XP"
              type="number"
              value={form.value}
              onChange={(event) => setForm({ ...form, value: event.target.value })}
              inputProps={{ min: 0 }}
            />
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography>Disponível para atribuição</Typography>
              <Switch
                checked={form.ativo}
                onChange={(event) => setForm({ ...form, ativo: event.target.checked })}
              />
            </Stack>
            <Button
              type="submit"
              variant="contained"
              startIcon={<AddOutlined />}
              disabled={createMission.isPending}
            >
              Criar missão
            </Button>
            {createMission.error && <Alert severity="error">{createMission.error.message}</Alert>}
          </Stack>
        </Paper>

        <Stack spacing={2}>
          <Stack direction="row" spacing={1} alignItems="center">
            <AccountTreeOutlined />
            <Typography variant="h6">Árvore de missões</Typography>
          </Stack>
          {roots.map((mission) => (
            <MissionNode
              key={mission.id}
              mission={mission}
              missions={missions.data ?? []}
              onUpdated={() => queryClient.invalidateQueries({ queryKey: ["missions"] })}
            />
          ))}
          {!missions.isLoading && roots.length === 0 && (
            <Paper variant="outlined" sx={{ p: 3 }}>
              <Typography color="text.secondary">Nenhuma missão cadastrada.</Typography>
            </Paper>
          )}
          {missions.error && <Alert severity="error">{missions.error.message}</Alert>}
        </Stack>
      </Box>
    </Stack>
  );
}

function MissionNode({
  mission,
  missions,
  onUpdated,
  depth = 0,
}: {
  mission: Missao;
  missions: Missao[];
  onUpdated: () => Promise<unknown>;
  depth?: number;
}) {
  const { session } = useAuth();
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState({
    titulo: mission.titulo,
    descricao: mission.descricao ?? "",
    tipo: mission.tipo,
    value: String(mission.value),
    ativo: mission.ativo,
    parentId: mission.parentId ?? "",
  });
  const children = missions.filter((item) => item.parentId === mission.id);
  const invalidParentIds = new Set([
    mission.id,
    ...collectDescendantIds(mission.id, missions),
  ]);
  const updateMission = useMutation({
    mutationFn: () =>
      apiRequest<Missao>(
        `/api/missoes/${mission.id}`,
        {
          method: "PUT",
          body: JSON.stringify({
            titulo: form.titulo,
            descricao: form.descricao,
            tipo: form.tipo,
            value: Number(form.value),
            ativo: form.ativo,
            parentId: form.parentId || null,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setEditing(false);
      await onUpdated();
    },
  });

  return (
    <Box sx={{ ml: { xs: depth, sm: depth * 3 } }}>
      <Paper variant="outlined" sx={{ p: 2, borderLeft: 4, borderLeftColor: "primary.main" }}>
        <Stack direction="row" justifyContent="space-between" spacing={2}>
          <Box>
            <Typography fontWeight={700}>{mission.titulo}</Typography>
            <Typography variant="body2" color="text.secondary">
              {mission.descricao || "Sem descrição"}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} alignItems="flex-start">
            <Chip label={`${mission.value} XP`} size="small" />
            <Chip
              label={mission.ativo ? "Ativa" : "Inativa"}
              size="small"
              color={mission.ativo ? "success" : "default"}
            />
            <Button
              size="small"
              startIcon={<EditOutlined />}
              onClick={() => setEditing(true)}
            >
              Editar
            </Button>
          </Stack>
        </Stack>
        <Typography variant="caption" color="text.secondary">
          {mission.tipo} · {children.length} etapa(s) filha(s)
        </Typography>
      </Paper>
      {children.length > 0 && (
        <Stack spacing={1.5} sx={{ mt: 1.5 }}>
          {children.map((child) => (
            <MissionNode
              key={child.id}
              mission={child}
              missions={missions}
              onUpdated={onUpdated}
              depth={depth + 1}
            />
          ))}
        </Stack>
      )}
      <Dialog open={editing} onClose={() => setEditing(false)} fullWidth maxWidth="sm">
        <DialogTitle>Editar missão</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Título"
              value={form.titulo}
              onChange={(event) => setForm({ ...form, titulo: event.target.value })}
              required
            />
            <TextField
              label="Descrição"
              value={form.descricao}
              onChange={(event) => setForm({ ...form, descricao: event.target.value })}
              multiline
              minRows={3}
            />
            <FormControl>
              <InputLabel>Missão pai</InputLabel>
              <Select
                label="Missão pai"
                value={form.parentId}
                onChange={(event) => setForm({ ...form, parentId: event.target.value })}
              >
                <MenuItem value="">Nenhuma, missão raiz</MenuItem>
                {missions
                  .filter((item) => !invalidParentIds.has(item.id))
                  .map((item) => (
                    <MenuItem key={item.id} value={item.id}>{item.titulo}</MenuItem>
                  ))}
              </Select>
            </FormControl>
            <TextField
              label="Tipo"
              value={form.tipo}
              onChange={(event) => setForm({ ...form, tipo: event.target.value })}
            />
            <TextField
              label="XP"
              type="number"
              value={form.value}
              onChange={(event) => setForm({ ...form, value: event.target.value })}
              inputProps={{ min: 0 }}
            />
            <Stack direction="row" alignItems="center" justifyContent="space-between">
              <Typography>Disponível para atribuição</Typography>
              <Switch
                checked={form.ativo}
                onChange={(event) => setForm({ ...form, ativo: event.target.checked })}
              />
            </Stack>
            {updateMission.error && (
              <Alert severity="error">{updateMission.error.message}</Alert>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => updateMission.mutate()}
            disabled={updateMission.isPending || !form.titulo.trim()}
          >
            Salvar alterações
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function collectDescendantIds(parentId: string, missions: Missao[]): string[] {
  const children = missions.filter((mission) => mission.parentId === parentId);
  return children.flatMap((child) => [
    child.id,
    ...collectDescendantIds(child.id, missions),
  ]);
}
