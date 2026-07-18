import {
  Box,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { listDisciplinaPeriodos, listDisciplinas } from "../../api/catalog";
import type { Disciplina } from "../../types";
import { ErrorAlert, PeriodsTable } from "./CatalogShared";

export function DisciplinasTree() {
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Disciplina | null>(null);
  const disciplines = useQuery({
    queryKey: ["catalog", "disciplines", query],
    queryFn: () => listDisciplinas(query, session),
  });
  const periods = useQuery({
    queryKey: ["catalog", "discipline-periods", selected?.id],
    queryFn: () => listDisciplinaPeriodos(selected!.id, session),
    enabled: Boolean(selected),
  });

  return (
    <Stack spacing={2}>
      <TextField
        label="Buscar disciplina"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="ID, nome ou unidade"
      />
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", lg: "360px 1fr" }, gap: 2 }}>
        <Paper variant="outlined" sx={{ maxHeight: 650, overflow: "auto" }}>
          <List dense>
            {disciplines.data?.map((item) => (
              <ListItemButton
                key={item.id}
                selected={selected?.id === item.id}
                onClick={() => setSelected(item)}
              >
                <ListItemText
                  primary={item.nome}
                  secondary={`${item.id} · ${item.unidadeSigla ?? "Sem unidade"}`}
                />
              </ListItemButton>
            ))}
          </List>
          <ErrorAlert error={disciplines.error} />
        </Paper>
        <Paper variant="outlined">
          <Box sx={{ p: 2 }}>
            <Typography variant="h6">
              {selected ? selected.nome : "Selecione uma disciplina"}
            </Typography>
            {selected && <Typography color="text.secondary">ID {selected.id}</Typography>}
          </Box>
          {selected && <PeriodsTable data={periods.data} loading={periods.isLoading} />}
          <ErrorAlert error={periods.error} />
        </Paper>
      </Box>
    </Stack>
  );
}
