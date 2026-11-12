import { Autocomplete, Alert, Box, Chip, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { listPessoasResumo } from "../../api/catalog";
import { getPessoaPontos, listPessoaPontosLancamentos } from "../../api/people";
import { useAuth } from "../../auth/AuthContext";
import type { PessoaResumo } from "../../types/people";

export function PeoplePointsPanel() {
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const [person, setPerson] = useState<PessoaResumo | null>(null);
  const people = useQuery({
    queryKey: ["analytics", "points-people", query],
    queryFn: () => listPessoasResumo(query, session),
    enabled: Boolean(session && query.trim().length >= 2),
  });
  const balance = useQuery({
    queryKey: ["analytics", "person-points", person?.id],
    queryFn: () => getPessoaPontos(person!.id, session),
    enabled: Boolean(person && session),
  });
  const ledger = useQuery({
    queryKey: ["analytics", "person-points-ledger", person?.id],
    queryFn: () => listPessoaPontosLancamentos(person!.id, 0, 20, session),
    enabled: Boolean(person && session),
  });

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="h6">Pontos por usuário</Typography>
          <Typography variant="body2" color="text.secondary">
            Pesquise uma pessoa para consultar o saldo e os lançamentos auditáveis de pontos.
          </Typography>
        </Box>
        <Autocomplete
          options={people.data ?? []}
          value={person}
          loading={people.isFetching}
          filterOptions={(options) => options}
          getOptionLabel={(option) => `${option.nome} · ${option.id}`}
          isOptionEqualToValue={(a, b) => a.id === b.id}
          onInputChange={(_, value) => setQuery(value)}
          onChange={(_, value) => setPerson(value)}
          renderInput={(params) => (
            <TextField {...params} label="Buscar pessoa por nome, ID ou e-mail" helperText="Digite pelo menos 2 caracteres." />
          )}
        />
        {people.isError && <Alert severity="error">Falha ao buscar pessoas: {String(people.error)}</Alert>}
        {person && balance.data && (
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <Chip label={`Saldo: ${balance.data.saldoTotalPontos} pontos`} color="primary" />
            <Chip label={`Concedidos: ${balance.data.totalConcedidoPontos}`} />
            <Chip label={`Estornados: ${balance.data.totalEstornadoPontos}`} />
            <Chip label={`Ajustes: ${balance.data.totalAjustadoPontos}`} />
          </Stack>
        )}
        {person && balance.isError && <Alert severity="error">Falha ao carregar saldo: {String(balance.error)}</Alert>}
        {person && ledger.data && (
          <Box sx={{ overflowX: "auto" }}>
            <Table size="small">
              <TableHead><TableRow><TableCell>Data</TableCell><TableCell>Missão</TableCell><TableCell>Tipo</TableCell><TableCell align="right">Pontos</TableCell><TableCell>Descrição</TableCell></TableRow></TableHead>
              <TableBody>
                {ledger.data.content.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell>{new Date(entry.createdAt).toLocaleString("pt-BR")}</TableCell>
                    <TableCell>{entry.missaoTitulo ?? "—"}</TableCell>
                    <TableCell>{entry.tipo}</TableCell>
                    <TableCell align="right">{entry.pontos}</TableCell>
                    <TableCell sx={{ overflowWrap: "anywhere" }}>{entry.descricao ?? "—"}</TableCell>
                  </TableRow>
                ))}
                {ledger.data.content.length === 0 && <TableRow><TableCell colSpan={5} align="center">Nenhum lançamento para esta pessoa.</TableCell></TableRow>}
              </TableBody>
            </Table>
            <Typography variant="caption" color="text.secondary">Exibindo até 20 lançamentos mais recentes de {ledger.data.totalElements}.</Typography>
          </Box>
        )}
        {person && ledger.isError && <Alert severity="error">Falha ao carregar lançamentos: {String(ledger.error)}</Alert>}
      </Stack>
    </Paper>
  );
}
