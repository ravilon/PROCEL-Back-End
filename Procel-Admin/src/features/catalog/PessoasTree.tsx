import {
  AddOutlined,
  EditOutlined,
  DeleteOutlined,
} from "@mui/icons-material";
import {
  LinearProgress,
  Box,
  Chip,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  List,
  ListItemButton,
  ListItemText,
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
import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  createPessoa,
  deletePessoa,
  getPessoa,
  getPessoaCurso,
  linkDisciplinaPessoa,
  listAtividadesPessoa,
  listDisciplinasPessoa,
  updatePessoa,
} from "../../api/people";
import {
  listDisciplinaPeriodos,
  listDisciplinas,
  listPessoasResumo,
} from "../../api/catalog";
import type {
  AtividadeStatus,
  PessoaResumo,
  Role,
} from "../../types";
import { Empty, ErrorAlert } from "./CatalogShared";

export function PessoasTree() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<PessoaResumo | null>(null);
  const [activityStatus, setActivityStatus] = useState<AtividadeStatus | "ALL">("ALL");
  const [userDialogOpen, setUserDialogOpen] = useState(false);
  const [createUserDialogOpen, setCreateUserDialogOpen] = useState(false);
  const [deleteUserDialogOpen, setDeleteUserDialogOpen] = useState(false);
  const [newUserForm, setNewUserForm] = useState({
    userId: "",
    nome: "",
    email: "",
    telefone: "",
    matricula: "",
    password: "",
    roles: ["USUARIO"] as Role[],
  });
  const [userForm, setUserForm] = useState({
    nome: "",
    email: "",
    telefone: "",
    matricula: "",
    password: "",
    roles: [] as Role[],
  });
  const [period, setPeriod] = useState(`${new Date().getFullYear()}/${new Date().getMonth() < 6 ? 1 : 2}`);
  const [disciplineDialogOpen, setDisciplineDialogOpen] = useState(false);
  const [disciplineLink, setDisciplineLink] = useState({
    disciplinaId: "",
    turma: "",
    periodoLetivo: period,
    status: "ATIVA",
  });
  const people = useQuery({
    queryKey: ["catalog", "people", query],
    queryFn: () => listPessoasResumo(query, session),
  });
  const disciplines = useQuery({
    queryKey: ["person", selected?.id, "disciplines", period],
    queryFn: () => listDisciplinasPessoa(selected!.id, period, session),
    enabled: Boolean(selected && /^\d{4}\/[12]$/.test(period)),
  });
  const disciplineCatalog = useQuery({
    queryKey: ["catalog", "disciplines", "person-link"],
    queryFn: () => listDisciplinas("", session),
    enabled: disciplineDialogOpen,
  });
  const disciplinePeriods = useQuery({
    queryKey: ["catalog", "discipline-periods", disciplineLink.disciplinaId],
    queryFn: () => listDisciplinaPeriodos(Number(disciplineLink.disciplinaId), session),
    enabled: Boolean(disciplineDialogOpen && disciplineLink.disciplinaId),
  });
  const disciplineClasses = Array.from(
    new Set(
      disciplinePeriods.data
        ?.map((item) => item.turma?.trim())
        .filter((item): item is string => Boolean(item)) ?? [],
    ),
  ).sort((a, b) => a.localeCompare(b));
  const linkDiscipline = useMutation({
    mutationFn: () =>
      linkDisciplinaPessoa(
        selected!.id,
        {
          disciplinaId: Number(disciplineLink.disciplinaId),
          turma: disciplineLink.turma,
          periodoLetivo: disciplineLink.periodoLetivo,
          status: disciplineLink.status,
        },
        session,
      ),
    onSuccess: async () => {
      setDisciplineDialogOpen(false);
      setPeriod(disciplineLink.periodoLetivo);
      setDisciplineLink({
        disciplinaId: "",
        turma: "",
        periodoLetivo: disciplineLink.periodoLetivo,
        status: "ATIVA",
      });
      await queryClient.invalidateQueries({
        queryKey: ["person", selected?.id, "disciplines"],
      });
    },
  });
  const activities = useQuery({
    queryKey: ["person", selected?.id, "activities", activityStatus],
    queryFn: () =>
      listAtividadesPessoa(selected!.id, activityStatus === "ALL" ? "" : activityStatus, session),
    enabled: Boolean(selected),
  });
  const course = useQuery({
    queryKey: ["person", selected?.id, "course"],
    queryFn: () => getPessoaCurso(selected!.id, session),
    enabled: Boolean(selected),
  });
  const personDetails = useQuery({
    queryKey: ["person", selected?.id, "details"],
    queryFn: () => getPessoa(selected!.id, session),
    enabled: Boolean(selected && hasAnyRole("ADMIN")),
  });
  const updatePerson = useMutation({
    mutationFn: () =>
      updatePessoa(
        selected!.id,
        {
          nome: userForm.nome,
          email: userForm.email,
          userId: selected!.id,
          password: userForm.password || undefined,
          telefone: userForm.telefone,
          matricula: userForm.matricula,
          roles: userForm.roles,
        },
        session,
      ),
    onSuccess: async () => {
      setUserDialogOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["catalog", "people"] }),
        queryClient.invalidateQueries({ queryKey: ["person", selected?.id, "details"] }),
      ]);
    },
  });
  const createPerson = useMutation({
    mutationFn: () => createPessoa(newUserForm, session),
    onSuccess: async (created) => {
      setCreateUserDialogOpen(false);
      setNewUserForm({
        userId: "",
        nome: "",
        email: "",
        telefone: "",
        matricula: "",
        password: "",
        roles: ["USUARIO"],
      });
      setSelected({
        id: created.id,
        nome: created.nome,
        email: created.email,
        matricula: created.matricula,
        roles: created.roles,
      });
      await queryClient.invalidateQueries({ queryKey: ["catalog", "people"] });
    },
  });
  const deletePerson = useMutation({
    mutationFn: () => deletePessoa(selected!.id, session),
    onSuccess: async () => {
      setDeleteUserDialogOpen(false);
      setSelected(null);
      await queryClient.invalidateQueries({ queryKey: ["catalog", "people"] });
    },
  });

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
        <TextField
          fullWidth
          label="Buscar pessoa"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="ID, nome, e-mail ou matricula"
        />
        {hasAnyRole("ADMIN") && (
          <Button
            variant="contained"
            startIcon={<AddOutlined />}
            onClick={() => setCreateUserDialogOpen(true)}
            sx={{ flexShrink: 0 }}
          >
            Cadastrar usuario
          </Button>
        )}
      </Stack>
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "340px 1fr" }, gap: 2 }}>
        <Paper variant="outlined" sx={{ maxHeight: 650, overflow: "auto" }}>
          <List dense>
            {people.data?.map((item) => (
              <ListItemButton
                key={item.id}
                selected={selected?.id === item.id}
                onClick={() => setSelected(item)}
              >
                <ListItemText primary={item.nome} secondary={`${item.id} · ${item.email}`} />
              </ListItemButton>
            ))}
          </List>
          <ErrorAlert error={people.error} />
        </Paper>
        <Stack spacing={2}>
          {!selected && <Paper variant="outlined"><Empty text="Selecione uma pessoa." /></Paper>}
          {selected && (
            <>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack direction="row" justifyContent="space-between" spacing={2}>
                  <Typography variant="h6">{selected.nome}</Typography>
                  {hasAnyRole("ADMIN") && (
                    <Stack direction="row" spacing={1}>
                    <Button
                      startIcon={<EditOutlined />}
                      onClick={() => {
                        const person = personDetails.data;
                        setUserForm({
                          nome: person?.nome ?? selected.nome,
                          email: person?.email ?? selected.email,
                          telefone: person?.telefone ?? "",
                          matricula: person?.matricula ?? selected.matricula ?? "",
                          password: "",
                          roles: person?.roles ?? selected.roles,
                        });
                        setUserDialogOpen(true);
                      }}
                    >
                      Editar usuário
                    </Button>
                    <Button
                      color="error"
                      startIcon={<DeleteOutlined />}
                      onClick={() => setDeleteUserDialogOpen(true)}
                      disabled={selected.id === session?.userId}
                    >
                      Excluir
                    </Button>
                    </Stack>
                  )}
                </Stack>
                <Typography>{selected.email}</Typography>
                <Typography color="text.secondary">
                  Curso: {course.data?.curso?.nome ?? "Nao vinculado"}
                </Typography>
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  {selected.roles.map((role) => <Chip key={role} label={role} size="small" />)}
                </Stack>
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Stack direction="row" spacing={1.5}>
                    <Typography variant="h6">Disciplinas associadas</Typography>
                    <TextField
                      label="Período letivo"
                      value={period}
                      onChange={(event) => setPeriod(event.target.value)}
                      size="small"
                    />
                  </Stack>
                  <Button
                    variant="contained"
                    startIcon={<AddOutlined />}
                    onClick={() => {
                      setDisciplineLink({ ...disciplineLink, periodoLetivo: period });
                      setDisciplineDialogOpen(true);
                    }}
                  >
                    Atribuir disciplina
                  </Button>
                </Stack>
                <TableContainer sx={{ mt: 2 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Disciplina</TableCell>
                        <TableCell>Turma</TableCell>
                        <TableCell>Período</TableCell>
                        <TableCell>Status</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {disciplines.data?.map((discipline) => (
                        <TableRow key={discipline.vinculoId}>
                          <TableCell>{discipline.disciplinaNome}</TableCell>
                          <TableCell>{discipline.turma}</TableCell>
                          <TableCell>{discipline.periodoLetivo}</TableCell>
                          <TableCell>
                            <Chip label={discipline.status} size="small" />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
                {!disciplines.isLoading && disciplines.data?.length === 0 && (
                  <Empty text="Nenhuma disciplina associada neste período." />
                )}
                <ErrorAlert error={disciplines.error} />
              </Paper>
              <Paper variant="outlined" sx={{ p: 2 }}>
                <Stack
                  direction={{ xs: "column", sm: "row" }}
                  justifyContent="space-between"
                  spacing={2}
                >
                  <Typography variant="h6">Atividades e progresso</Typography>
                  <FormControl size="small" sx={{ minWidth: 190 }}>
                    <InputLabel>Status</InputLabel>
                    <Select
                      label="Status"
                      value={activityStatus}
                      onChange={(event) =>
                        setActivityStatus(event.target.value as AtividadeStatus | "ALL")
                      }
                    >
                      <MenuItem value="ALL">Todos os status</MenuItem>
                      {["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA", "EXPIRADA", "CANCELADA"].map(
                        (status) => (
                          <MenuItem key={status} value={status}>{status}</MenuItem>
                        ),
                      )}
                    </Select>
                  </FormControl>
                </Stack>
                <Stack spacing={1.5} sx={{ mt: 2 }}>
                  {activities.data?.map((activity) => (
                    <Box
                      key={activity.id}
                      sx={{
                        p: 2,
                        ml: activity.missaoParentId ? 3 : 0,
                        border: 1,
                        borderColor: "divider",
                        borderLeft: 4,
                        borderLeftColor:
                          activity.status === "CONCLUIDA"
                            ? "success.main"
                            : activity.status === "EM_ANDAMENTO"
                              ? "warning.main"
                              : "grey.400",
                        borderRadius: 1,
                      }}
                    >
                      <Stack direction="row" justifyContent="space-between" spacing={2}>
                        <Box>
                          <Typography fontWeight={700}>{activity.missaoTitulo}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {activity.missaoDescricao || activity.missaoTipo}
                          </Typography>
                        </Box>
                        <Chip label={activity.status} size="small" />
                      </Stack>
                      {activity.totalFilhas > 0 && (
                        <Box sx={{ mt: 1.5 }}>
                          <Stack direction="row" justifyContent="space-between">
                            <Typography variant="caption">
                              {activity.filhasConcluidas} de {activity.totalFilhas} etapas concluídas
                            </Typography>
                            <Typography variant="caption">
                              {activity.progressoPercentual}%
                            </Typography>
                          </Stack>
                          <LinearProgress
                            variant="determinate"
                            value={activity.progressoPercentual}
                            sx={{ mt: 0.5, height: 8, borderRadius: 1 }}
                          />
                        </Box>
                      )}
                    </Box>
                  ))}
                  {!activities.isLoading && activities.data?.length === 0 && (
                    <Empty text="Nenhuma atividade encontrada para este filtro." />
                  )}
                </Stack>
                <ErrorAlert error={activities.error} />
              </Paper>
            </>
          )}
        </Stack>
      </Box>
      <Dialog
        open={createUserDialogOpen}
        onClose={() => !createPerson.isPending && setCreateUserDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Cadastrar usuario</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="ID do usuario"
              value={newUserForm.userId}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, userId: event.target.value })
              }
              required
              helperText="Identificador usado no login. Nao podera ser alterado."
            />
            <TextField
              label="Nome"
              value={newUserForm.nome}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, nome: event.target.value })
              }
              required
            />
            <TextField
              label="E-mail"
              type="email"
              value={newUserForm.email}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, email: event.target.value })
              }
              required
            />
            <TextField
              label="Senha inicial"
              type="password"
              value={newUserForm.password}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, password: event.target.value })
              }
              required
            />
            <TextField
              label="Telefone"
              value={newUserForm.telefone}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, telefone: event.target.value })
              }
            />
            <TextField
              label="Matricula"
              value={newUserForm.matricula}
              onChange={(event) =>
                setNewUserForm({ ...newUserForm, matricula: event.target.value })
              }
            />
            <FormControl>
              <InputLabel>Perfis de acesso</InputLabel>
              <Select
                multiple
                label="Perfis de acesso"
                value={newUserForm.roles}
                onChange={(event) =>
                  setNewUserForm({
                    ...newUserForm,
                    roles: typeof event.target.value === "string"
                      ? event.target.value.split(",") as Role[]
                      : event.target.value as Role[],
                  })
                }
              >
                {["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"].map((role) => (
                  <MenuItem key={role} value={role}>{role}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <ErrorAlert error={createPerson.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setCreateUserDialogOpen(false)}
            disabled={createPerson.isPending}
          >
            Cancelar
          </Button>
          <Button
            variant="contained"
            onClick={() => createPerson.mutate()}
            disabled={
              createPerson.isPending
              || !newUserForm.userId.trim()
              || !newUserForm.nome.trim()
              || !newUserForm.email.trim()
              || !newUserForm.password
              || newUserForm.roles.length === 0
            }
          >
            Cadastrar
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={deleteUserDialogOpen}
        onClose={() => !deletePerson.isPending && setDeleteUserDialogOpen(false)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>Excluir usuario</DialogTitle>
        <DialogContent>
          <Typography>
            Confirma a exclusao de <strong>{selected?.nome}</strong> ({selected?.id})?
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            A exclusao e permanente e pode ser recusada caso existam dados vinculados.
          </Typography>
          <ErrorAlert error={deletePerson.error} />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setDeleteUserDialogOpen(false)}
            disabled={deletePerson.isPending}
          >
            Cancelar
          </Button>
          <Button
            color="error"
            variant="contained"
            onClick={() => deletePerson.mutate()}
            disabled={deletePerson.isPending || !selected}
          >
            Excluir permanentemente
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={userDialogOpen}
        onClose={() => setUserDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Editar usuário</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nome"
              value={userForm.nome}
              onChange={(event) => setUserForm({ ...userForm, nome: event.target.value })}
              required
            />
            <TextField
              label="E-mail"
              type="email"
              value={userForm.email}
              onChange={(event) => setUserForm({ ...userForm, email: event.target.value })}
              required
            />
            <TextField
              label="Telefone"
              value={userForm.telefone}
              onChange={(event) => setUserForm({ ...userForm, telefone: event.target.value })}
            />
            <TextField
              label="Matrícula"
              value={userForm.matricula}
              onChange={(event) => setUserForm({ ...userForm, matricula: event.target.value })}
            />
            <TextField
              label="Nova senha"
              type="password"
              value={userForm.password}
              onChange={(event) => setUserForm({ ...userForm, password: event.target.value })}
              helperText="Deixe em branco para manter a senha atual."
            />
            <FormControl>
              <InputLabel>Perfis de acesso</InputLabel>
              <Select
                multiple
                label="Perfis de acesso"
                value={userForm.roles}
                onChange={(event) =>
                  setUserForm({
                    ...userForm,
                    roles: typeof event.target.value === "string"
                      ? event.target.value.split(",") as Role[]
                      : event.target.value as Role[],
                  })
                }
              >
                {["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"].map((role) => (
                  <MenuItem key={role} value={role}>{role}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <ErrorAlert error={updatePerson.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setUserDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => updatePerson.mutate()}
            disabled={
              updatePerson.isPending
              || !userForm.nome.trim()
              || !userForm.email.trim()
              || userForm.roles.length === 0
            }
          >
            Salvar alterações
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={disciplineDialogOpen}
        onClose={() => setDisciplineDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Atribuir disciplina a {selected?.nome}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <FormControl required>
              <InputLabel>Disciplina</InputLabel>
              <Select
                label="Disciplina"
                value={disciplineLink.disciplinaId}
                onChange={(event) =>
                  setDisciplineLink({
                    ...disciplineLink,
                    disciplinaId: event.target.value,
                    turma: "",
                  })
                }
              >
                {disciplineCatalog.data?.map((discipline) => (
                  <MenuItem key={discipline.id} value={String(discipline.id)}>
                    {discipline.nome}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            {disciplineClasses.length > 0 ? (
              <FormControl required>
                <InputLabel>Turma</InputLabel>
                <Select
                  label="Turma"
                  value={disciplineLink.turma}
                  onChange={(event) =>
                    setDisciplineLink({ ...disciplineLink, turma: event.target.value })
                  }
                >
                  {disciplineClasses.map((turma) => (
                    <MenuItem key={turma} value={turma}>{turma}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            ) : (
              <TextField
                label="Turma"
                value={disciplineLink.turma}
                onChange={(event) =>
                  setDisciplineLink({ ...disciplineLink, turma: event.target.value })
                }
                helperText={
                  disciplineLink.disciplinaId && !disciplinePeriods.isLoading
                    ? "Nenhuma turma sincronizada; informe manualmente."
                    : "Selecione uma disciplina para carregar as turmas."
                }
                required
              />
            )}
            <ErrorAlert error={disciplinePeriods.error} />
            <TextField
              label="Período letivo"
              value={disciplineLink.periodoLetivo}
              onChange={(event) =>
                setDisciplineLink({ ...disciplineLink, periodoLetivo: event.target.value })
              }
              placeholder="2026/1"
              required
            />
            <FormControl>
              <InputLabel>Status</InputLabel>
              <Select
                label="Status"
                value={disciplineLink.status}
                onChange={(event) =>
                  setDisciplineLink({ ...disciplineLink, status: event.target.value })
                }
              >
                <MenuItem value="ATIVA">Ativa</MenuItem>
                <MenuItem value="CONCLUIDA">Concluída</MenuItem>
                <MenuItem value="CANCELADA">Cancelada</MenuItem>
              </Select>
            </FormControl>
            <ErrorAlert error={linkDiscipline.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDisciplineDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => linkDiscipline.mutate()}
            disabled={
              linkDiscipline.isPending
              || !disciplineLink.disciplinaId
              || !disciplineLink.turma.trim()
              || !/^\d{4}\/[12]$/.test(disciplineLink.periodoLetivo)
            }
          >
            Atribuir
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
