import {
  Alert,
  Box,
  Chip,
  Divider,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { Medicao, PeriodoAula, TipoSensor } from "../../types";

import { measurementStatus, measurementStatusConfig } from "./catalogMeasurement";

export function ErrorAlert({ error }: { error: Error | null }) {
  return error ? <Alert severity="error">{error.message}</Alert> : null;
}

export function Empty({ text }: { text: string }) {
  return (
    <Typography color="text.secondary" sx={{ p: 2, textAlign: "center" }}>
      {text}
    </Typography>
  );
}

export function MeasurementCard({
  measurement,
  parameters,
}: {
  measurement: Medicao;
  parameters: TipoSensor["parametros"];
}) {
  const status = measurementStatus(measurement);
  const statusConfig = measurementStatusConfig[status];

  return (
    <Paper variant="outlined" sx={{ overflow: "hidden" }}>
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="center"
        sx={{ p: 2, bgcolor: "grey.50" }}
      >
        <Box>
          <Typography fontWeight={700}>
            {new Date(measurement.timestamp).toLocaleString()}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Origem: {measurement.source}
          </Typography>
        </Box>
        <Chip
          icon={statusConfig.icon}
          label={statusConfig.label}
          color={statusConfig.color}
          variant={status === "UNCLASSIFIED" ? "outlined" : "filled"}
        />
      </Stack>
      <Divider />
      <Box
        sx={{
          p: 2,
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" },
          gap: 1.5,
        }}
      >
        {Object.entries(measurement.valores).map(([name, value]) => {
          const parameter = parameters.find((item) => item.nome === name);
          const qualifications = measurement.qualificacoes[name] ?? [];
          const rejected = qualifications.some((item) =>
            ["ALERTA", "CRITICO", "INVALIDO"].includes(item.resultado),
          );
          const approved = qualifications.some((item) =>
            ["IDEAL", "NORMAL"].includes(item.resultado),
          );

          return (
            <Box
              key={name}
              sx={{
                p: 1.5,
                border: 1,
                borderColor: rejected
                  ? "error.light"
                  : approved
                    ? "success.light"
                    : "divider",
                borderRadius: 1,
              }}
            >
              <Typography variant="caption" color="text.secondary">
                {parameter?.descricao || name}
              </Typography>
              <Typography variant="h6">
                {String(value)}
                {parameter?.numericUnit ? (
                  <Typography component="span" variant="body2" sx={{ ml: 0.5 }}>
                    {parameter.numericUnit}
                  </Typography>
                ) : null}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                {name}
              </Typography>
              <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
                {qualifications.map((qualification) => (
                  <Chip
                    key={qualification.id}
                    size="small"
                    color={
                      ["ALERTA", "CRITICO", "INVALIDO"].includes(qualification.resultado)
                        ? "error"
                        : "success"
                    }
                    label={`${qualification.resultado}: ${
                      qualification.regraNome ?? "regra"
                    }`}
                  />
                ))}
                {qualifications.length === 0 && (
                  <Chip size="small" variant="outlined" label="Sem regra disparada" />
                )}
              </Stack>
            </Box>
          );
        })}
      </Box>
    </Paper>
  );
}

export function PeriodsTable({
  data,
  loading,
}: {
  data?: PeriodoAula[];
  loading: boolean;
}) {
  return (
    <>
      <TableContainer sx={{ maxHeight: 420 }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell>Data</TableCell>
              <TableCell>Horario</TableCell>
              <TableCell>Disciplina</TableCell>
              <TableCell>Turma</TableCell>
              <TableCell>Tipo</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.map((item) => (
              <TableRow key={item.id}>
                <TableCell>{item.data}</TableCell>
                <TableCell>{item.horaInicio} - {item.horaFim}</TableCell>
                <TableCell>{item.disciplinaNome ?? item.descricao}</TableCell>
                <TableCell>{item.turma ?? "-"}</TableCell>
                <TableCell>{item.tipo}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      {!loading && data?.length === 0 && <Empty text="Nenhum periodo de aula encontrado." />}
    </>
  );
}
