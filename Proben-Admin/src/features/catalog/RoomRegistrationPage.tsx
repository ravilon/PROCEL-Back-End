import { SaveOutlined } from "@mui/icons-material";
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from "@mui/material";
import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { createCompartimento } from "../../api/catalog";
import { useAuth } from "../../auth/AuthContext";

const initialForm = {
  id: "",
  nome: "",
  tipo: "Sala",
  campusNome: "",
  predioNome: "",
  unidadeNome: "",
  pavimento: "",
  capacidade: "",
  area: "",
};

export function RoomRegistrationPage() {
  const { session } = useAuth();
  const [form, setForm] = useState(initialForm);
  const createRoom = useMutation({
    mutationFn: () =>
      createCompartimento(
        {
          id: form.id.trim(),
          nome: form.nome.trim(),
          tipo: form.tipo.trim(),
          campusNome: form.campusNome.trim(),
          predioNome: form.predioNome.trim(),
          unidadeNome: form.unidadeNome.trim(),
          pavimento: form.pavimento === "" ? null : Number(form.pavimento),
          capacidade: form.capacidade === "" ? null : Number(form.capacidade),
          area: form.area === "" ? null : Number(form.area),
        },
        session,
      ),
    onSuccess: () => setForm(initialForm),
  });

  const set = (field: keyof typeof initialForm, value: string) =>
    setForm((current) => ({ ...current, [field]: value }));

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h5">Cadastro de sala</Typography>
        <Typography color="text.secondary">
          Cadastre uma sala manualmente. Campus, prédio e unidade serão
          reutilizados quando já existirem.
        </Typography>
      </Box>
      {createRoom.isSuccess && (
        <Alert severity="success">
          Sala {createRoom.data.nome} cadastrada com sucesso.
        </Alert>
      )}
      {createRoom.isError && (
        <Alert severity="error">{createRoom.error.message}</Alert>
      )}
      <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
        <Stack
          component="form"
          spacing={2}
          onSubmit={(event) => {
            event.preventDefault();
            createRoom.mutate();
          }}
        >
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField required label="ID da sala" value={form.id} onChange={(e) => set("id", e.target.value)} fullWidth />
            <TextField required label="Nome" value={form.nome} onChange={(e) => set("nome", e.target.value)} fullWidth />
            <TextField required label="Tipo" value={form.tipo} onChange={(e) => set("tipo", e.target.value)} fullWidth />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField required label="Campus" value={form.campusNome} onChange={(e) => set("campusNome", e.target.value)} fullWidth />
            <TextField required label="Prédio" value={form.predioNome} onChange={(e) => set("predioNome", e.target.value)} fullWidth />
            <TextField required label="Unidade" value={form.unidadeNome} onChange={(e) => set("unidadeNome", e.target.value)} fullWidth />
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField type="number" label="Pavimento" value={form.pavimento} onChange={(e) => set("pavimento", e.target.value)} fullWidth />
            <TextField type="number" label="Capacidade" value={form.capacidade} onChange={(e) => set("capacidade", e.target.value)} inputProps={{ min: 0 }} fullWidth />
            <TextField type="number" label="Área (m²)" value={form.area} onChange={(e) => set("area", e.target.value)} inputProps={{ min: 0, step: "0.01" }} fullWidth />
          </Stack>
          <Button
            type="submit"
            variant="contained"
            startIcon={<SaveOutlined />}
            disabled={createRoom.isPending}
            sx={{ alignSelf: "flex-start" }}
          >
            {createRoom.isPending ? "Cadastrando..." : "Cadastrar sala"}
          </Button>
        </Stack>
      </Paper>
    </Stack>
  );
}
