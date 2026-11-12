import {
  Alert,
  Box,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
  listPessoasPontosAnalytics,
  listPessoaPontosLancamentos,
} from "../../api/people";
import { useAuth } from "../../auth/AuthContext";

export function PeoplePointsPanel() {
  const { session } = useAuth();
  const [query, setQuery] = useState("");
  const [selectedPersonId, setSelectedPersonId] = useState<string | null>(null);
  const people = useQuery({
    queryKey: ["analytics", "people-points", query],
    queryFn: () => listPessoasPontosAnalytics(query, session),
    enabled: Boolean(session),
  });
  const ledger = useQuery({
    queryKey: ["analytics", "person-points-ledger", selectedPersonId],
    queryFn: () => listPessoaPontosLancamentos(selectedPersonId!, 0, 20, session),
    enabled: Boolean(session && selectedPersonId),
  });

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="h6">Pontos por usuário</Typography>
          <Typography variant="body2" color="text.secondary">
            Saldo e totais do ledger de pontos por pessoa. Selecione uma linha
            para consultar os lançamentos recentes.
          </Typography>
        </Box>
        <TextField
          label="Buscar usuário"
          placeholder="Nome, ID ou e-mail"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          fullWidth
        />
        {people.isFetching && <LinearProgress />}
        {people.isError && (
          <Alert severity="error">
            Falha ao carregar pontos por usuário: {String(people.error)}
          </Alert>
        )}
        <Box sx={{ overflowX: "auto" }}>
          <Table size="small" aria-label="Pontos por usuário">
            <TableHead>
              <TableRow>
                <TableCell>Usuário</TableCell>
                <TableCell>ID</TableCell>
                <TableCell align="right">Saldo</TableCell>
                <TableCell align="right">Concedidos</TableCell>
                <TableCell align="right">Estornados</TableCell>
                <TableCell align="right">Ajustes</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(people.data ?? []).map((person) => (
                <TableRow
                  key={person.pessoaId}
                  hover
                  sx={{ cursor: "pointer" }}
                  selected={selectedPersonId === person.pessoaId}
                  onClick={() => setSelectedPersonId(person.pessoaId)}
                >
                  <TableCell>
                    <Stack>
                      <Typography variant="body2">{person.nome}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {person.email}
                      </Typography>
                    </Stack>
                  </TableCell>
                  <TableCell>{person.pessoaId}</TableCell>
                  <TableCell align="right">{person.saldoTotalPontos}</TableCell>
                  <TableCell align="right">{person.totalConcedidoPontos}</TableCell>
                  <TableCell align="right">{person.totalEstornadoPontos}</TableCell>
                  <TableCell align="right">{person.totalAjustadoPontos}</TableCell>
                </TableRow>
              ))}
              {!people.isFetching && people.data?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    Nenhum usuário encontrado.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Box>
        <Typography variant="caption" color="text.secondary">
          A consulta retorna até 200 pessoas, ordenadas pelo saldo.
        </Typography>
        {selectedPersonId && (
          <Box>
            <Typography variant="subtitle1" sx={{ mb: 1 }}>
              Lançamentos recentes: {selectedPersonId}
            </Typography>
            {ledger.isError && (
              <Alert severity="error">Falha ao carregar lançamentos: {String(ledger.error)}</Alert>
            )}
            <Box sx={{ overflowX: "auto" }}>
              <Table size="small" aria-label="Lançamentos recentes de pontos">
                <TableHead>
                  <TableRow>
                    <TableCell>Data</TableCell>
                    <TableCell>Missão</TableCell>
                    <TableCell>Tipo</TableCell>
                    <TableCell align="right">Pontos</TableCell>
                    <TableCell>Descrição</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(ledger.data?.content ?? []).map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>{new Date(entry.createdAt).toLocaleString("pt-BR")}</TableCell>
                      <TableCell>{entry.missaoTitulo ?? "—"}</TableCell>
                      <TableCell>{entry.tipo}</TableCell>
                      <TableCell align="right">{entry.pontos}</TableCell>
                      <TableCell sx={{ overflowWrap: "anywhere" }}>{entry.descricao ?? "—"}</TableCell>
                    </TableRow>
                  ))}
                  {!ledger.isFetching && ledger.data?.content.length === 0 && (
                    <TableRow><TableCell colSpan={5} align="center">Nenhum lançamento para esta pessoa.</TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
            </Box>
          </Box>
        )}
      </Stack>
    </Paper>
  );
}
