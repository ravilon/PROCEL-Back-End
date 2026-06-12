import {
  AddOutlined,
  RefreshOutlined,
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
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiRequest } from "../lib/api";
import type {
  AlunoDisciplinaStatus,
  DisciplinaAluno,
} from "../types";

function currentAcademicPeriod() {
  const now = new Date();
  return `${now.getFullYear()}/${now.getMonth() < 6 ? 1 : 2}`;
}

const statusColor = {
  ATIVA: "success",
  CONCLUIDA: "primary",
  CANCELADA: "default",
} as const;

export function DisciplinasPage() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [periodoLetivo, setPeriodoLetivo] = useState(currentAcademicPeriod);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [disciplinaId, setDisciplinaId] = useState("");
  const [turma, setTurma] = useState("");
  const [status, setStatus] = useState<AlunoDisciplinaStatus>("ATIVA");

  const queryKey = ["disciplinas", session?.userId, periodoLetivo];
  const disciplinas = useQuery({
    queryKey,
    queryFn: () =>
      apiRequest<DisciplinaAluno[]>(
        `/api/pessoas/${session!.userId}/disciplinas?periodoLetivo=${encodeURIComponent(periodoLetivo)}`,
        {},
        session,
      ),
    enabled: Boolean(session && /^\d{4}\/[12]$/.test(periodoLetivo)),
  });

  const vincular = useMutation({
    mutationFn: () =>
      apiRequest<DisciplinaAluno>(
        `/api/pessoas/${session!.userId}/disciplinas`,
        {
          method: "POST",
          body: JSON.stringify({
            disciplinaId: Number(disciplinaId),
            turma,
            periodoLetivo,
            status,
          }),
        },
        session,
      ),
    onSuccess: async () => {
      setDialogOpen(false);
      setDisciplinaId("");
      setTurma("");
      setStatus("ATIVA");
      await queryClient.invalidateQueries({ queryKey });
    },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    vincular.mutate();
  };

  return (
    <Stack spacing={3}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: 2,
        }}
      >
        <div>
          <Typography variant="h4">Minhas disciplinas</Typography>
          <Typography color="text.secondary">
            Vinculos por turma e periodo letivo.
          </Typography>
        </div>
        <Button
          variant="contained"
          startIcon={<AddOutlined />}
          onClick={() => setDialogOpen(true)}
        >
          Vincular disciplina
        </Button>
      </Box>

      <Paper sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            label="Periodo letivo"
            value={periodoLetivo}
            onChange={(event) => setPeriodoLetivo(event.target.value)}
            helperText="Formato AAAA/S, por exemplo 2026/1"
            error={!/^\d{4}\/[12]$/.test(periodoLetivo)}
          />
          <Button
            startIcon={<RefreshOutlined />}
            onClick={() => disciplinas.refetch()}
            disabled={!/^\d{4}\/[12]$/.test(periodoLetivo)}
          >
            Atualizar
          </Button>
        </Stack>
      </Paper>

      {disciplinas.isError && (
        <Alert severity="error">{disciplinas.error.message}</Alert>
      )}

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Disciplina</TableCell>
              <TableCell>Unidade</TableCell>
              <TableCell>Turma</TableCell>
              <TableCell>Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {disciplinas.data?.map((item) => (
              <TableRow key={item.vinculoId}>
                <TableCell>{item.disciplinaId}</TableCell>
                <TableCell>{item.disciplinaNome}</TableCell>
                <TableCell>{item.unidadeSigla ?? "-"}</TableCell>
                <TableCell>{item.turma}</TableCell>
                <TableCell>
                  <Chip
                    label={item.status}
                    color={statusColor[item.status]}
                    size="small"
                  />
                </TableCell>
              </TableRow>
            ))}
            {!disciplinas.isLoading && disciplinas.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  Nenhuma disciplina vinculada neste periodo.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog
        open={dialogOpen}
        onClose={() => !vincular.isPending && setDialogOpen(false)}
        fullWidth
        maxWidth="sm"
        PaperProps={{ component: "form", onSubmit: submit }}
      >
        <DialogTitle>Vincular disciplina</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {vincular.isError && (
              <Alert severity="error">{vincular.error.message}</Alert>
            )}
            <TextField
              label="ID da disciplina"
              type="number"
              required
              value={disciplinaId}
              onChange={(event) => setDisciplinaId(event.target.value)}
            />
            <TextField
              label="Turma"
              required
              value={turma}
              onChange={(event) => setTurma(event.target.value)}
              placeholder="T1"
            />
            <TextField
              label="Periodo letivo"
              required
              value={periodoLetivo}
              onChange={(event) => setPeriodoLetivo(event.target.value)}
            />
            <FormControl>
              <InputLabel id="status-label">Status</InputLabel>
              <Select
                labelId="status-label"
                label="Status"
                value={status}
                onChange={(event) =>
                  setStatus(event.target.value as AlunoDisciplinaStatus)
                }
              >
                <MenuItem value="ATIVA">ATIVA</MenuItem>
                <MenuItem value="CONCLUIDA">CONCLUIDA</MenuItem>
                <MenuItem value="CANCELADA">CANCELADA</MenuItem>
              </Select>
            </FormControl>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={vincular.isPending}>
            {vincular.isPending ? "Salvando..." : "Salvar"}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
