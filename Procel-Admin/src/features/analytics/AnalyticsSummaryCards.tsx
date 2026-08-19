import { AssessmentOutlined } from "@mui/icons-material";
import { Box, Card, CardContent, Chip, Paper, Stack, Typography } from "@mui/material";
import type { NumericBucketSummary } from "../../types/analytics";
import { formatDateTime, formatNumber } from "./analyticsUtils";

export function AnalyticsSummaryCards({
  summaries,
  loading,
}: {
  summaries: NumericBucketSummary[];
  loading: boolean;
}) {
  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <AssessmentOutlined color="primary" />
          <Box>
            <Typography variant="h6">Resumo consolidado</Typography>
            <Typography variant="body2" color="text.secondary">
              Um conjunto de cards por grupo retornado pelo backend; grupos com
              sensores, parametros, unidades ou versoes diferentes nao sao misturados.
            </Typography>
          </Box>
        </Stack>
        {loading && (
          <Typography color="text.secondary" aria-live="polite">
            Atualizando resumo...
          </Typography>
        )}
        {!loading && summaries.length === 0 && (
          <Typography color="text.secondary">
            Sem resumo para os filtros aplicados.
          </Typography>
        )}
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              md: "repeat(2, minmax(0, 1fr))",
              xl: "repeat(3, minmax(0, 1fr))",
            },
            gap: 2,
          }}
        >
          {summaries.map((summary) => (
            <Card
              key={`${summary.sensorExternalId}-${summary.parametroDefId}-${summary.compartimentoId}-${summary.aggregationVersion}`}
              variant="outlined"
            >
              <CardContent>
                <Stack spacing={1.5}>
                  <Box>
                    <Typography fontWeight={700}>
                      {summary.sensorNome || summary.sensorExternalId}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {summary.parametroNome} {summary.unidade ? `(${summary.unidade})` : ""}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    <Chip size="small" label={`Versao ${summary.aggregationVersion}`} />
                    <Chip size="small" label={`${summary.bucketCount} buckets`} />
                    <Chip size="small" label={`${summary.sampleCount} amostras`} />
                  </Stack>
                  <Box
                    sx={{
                      display: "grid",
                      gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
                      gap: 1.5,
                    }}
                  >
                    <Metric label="Media ponderada" value={formatNumber(summary.averageValue, summary.unidade)} />
                    <Metric label="Minimo" value={formatNumber(summary.minimumValue, summary.unidade)} />
                    <Metric label="Maximo" value={formatNumber(summary.maximumValue, summary.unidade)} />
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {formatDateTime(summary.from)} ate {formatDateTime(summary.to)}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Box>
      </Stack>
    </Paper>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography fontWeight={700} sx={{ overflowWrap: "anywhere" }}>
        {value}
      </Typography>
    </Box>
  );
}
