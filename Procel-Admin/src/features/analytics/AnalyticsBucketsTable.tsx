import { NavigateBeforeOutlined, NavigateNextOutlined } from "@mui/icons-material";
import {
  Box,
  Button,
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
import type { NumericBucketPage } from "../../types/analytics";
import { formatDateTime, formatNumber } from "./analyticsUtils";

export function AnalyticsBucketsTable({
  page,
  loading,
  onPageChange,
}: {
  page?: NumericBucketPage;
  loading: boolean;
  onPageChange: (page: number) => void;
}) {
  const currentPage = page?.page ?? 0;
  const totalPages = page?.totalPages ?? 0;
  const hasPrevious = currentPage > 0;
  const hasNext = totalPages > 0 && currentPage + 1 < totalPages;
  const isBeyondTotal =
    Boolean(page) && page!.content.length === 0 && page!.totalElements > 0;

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2}>
        <Box>
          <Typography variant="h6">Buckets persistidos</Typography>
          <Typography variant="body2" color="text.secondary">
            Ordenacao do backend: inicio, sensor, parametro, fim e versao.
          </Typography>
        </Box>
        {loading && (
          <Typography color="text.secondary" aria-live="polite">
            Atualizando tabela...
          </Typography>
        )}
        {isBeyondTotal && (
          <Typography color="text.secondary">
            Esta pagina nao possui buckets. Volte uma pagina para ver os resultados disponiveis.
          </Typography>
        )}
        <TableContainer sx={{ overflowX: "auto" }}>
          <Table size="small" aria-label="Tabela de buckets analiticos">
            <TableHead>
              <TableRow>
                <TableCell>Inicio</TableCell>
                <TableCell>Fim</TableCell>
                <TableCell>Sensor</TableCell>
                <TableCell>Parametro</TableCell>
                <TableCell>Compartimento</TableCell>
                <TableCell align="right">Media</TableCell>
                <TableCell align="right">Minimo</TableCell>
                <TableCell align="right">Maximo</TableCell>
                <TableCell align="right">Amostras</TableCell>
                <TableCell>Unidade</TableCell>
                <TableCell>Versao</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {page?.content.map((bucket) => (
                <TableRow
                  key={`${bucket.sensorExternalId}-${bucket.parametroDefId}-${bucket.bucketStart}-${bucket.bucketEnd}-${bucket.aggregationVersion}`}
                >
                  <TableCell>{formatDateTime(bucket.bucketStart)}</TableCell>
                  <TableCell>{formatDateTime(bucket.bucketEnd)}</TableCell>
                  <TableCell>{bucket.sensorNome || bucket.sensorExternalId}</TableCell>
                  <TableCell>{bucket.parametroNome}</TableCell>
                  <TableCell>{bucket.compartimentoId || "-"}</TableCell>
                  <TableCell align="right">{formatNumber(bucket.averageValue)}</TableCell>
                  <TableCell align="right">{formatNumber(bucket.minimumValue)}</TableCell>
                  <TableCell align="right">{formatNumber(bucket.maximumValue)}</TableCell>
                  <TableCell align="right">{bucket.sampleCount}</TableCell>
                  <TableCell>{bucket.unidade || "-"}</TableCell>
                  <TableCell>{bucket.aggregationVersion}</TableCell>
                </TableRow>
              ))}
              {!loading && page?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={11} align="center">
                    Nenhum bucket na pagina atual.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={1.5}
          alignItems={{ sm: "center" }}
          justifyContent="space-between"
        >
          <Typography variant="body2" color="text.secondary">
            Pagina {currentPage + 1} de {Math.max(totalPages, 1)} |{" "}
            {page?.totalElements ?? 0} elemento(s)
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button
              startIcon={<NavigateBeforeOutlined />}
              onClick={() => onPageChange(currentPage - 1)}
              disabled={!hasPrevious || loading}
            >
              Anterior
            </Button>
            <Button
              endIcon={<NavigateNextOutlined />}
              onClick={() => onPageChange(currentPage + 1)}
              disabled={!hasNext || loading}
            >
              Proxima
            </Button>
          </Stack>
        </Stack>
      </Stack>
    </Paper>
  );
}
