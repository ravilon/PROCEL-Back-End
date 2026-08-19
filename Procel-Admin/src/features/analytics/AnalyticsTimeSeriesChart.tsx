import { ShowChartOutlined } from "@mui/icons-material";
import { Box, Paper, Stack, Typography } from "@mui/material";
import { LineChart } from "@mui/x-charts/LineChart";
import type { NumericBucket } from "../../types/analytics";
import {
  bucketSeriesLabel,
  formatDateTime,
  formatNumber,
} from "./analyticsUtils";

export function AnalyticsTimeSeriesChart({ buckets }: { buckets: NumericBucket[] }) {
  const ordered = [...buckets].sort((a, b) => {
    const byStart = new Date(a.bucketStart).getTime() - new Date(b.bucketStart).getTime();
    if (byStart !== 0) return byStart;
    return bucketSeriesLabel(a).localeCompare(bucketSeriesLabel(b));
  });
  const timestamps = Array.from(new Set(ordered.map((bucket) => bucket.bucketStart))).sort();
  const groups = new Map<string, NumericBucket[]>();
  for (const bucket of ordered) {
    const label = bucketSeriesLabel(bucket);
    groups.set(label, [...(groups.get(label) ?? []), bucket]);
  }
  const series = Array.from(groups.entries()).map(([label, values]) => {
    const valuesByStart = new Map(values.map((bucket) => [bucket.bucketStart, bucket]));
    return {
      id: label,
      label,
      data: timestamps.map((timestamp) => valuesByStart.get(timestamp)?.averageValue ?? null),
      valueFormatter: (value: number | null) => (value === null ? "-" : formatNumber(value)),
    };
  });
  const firstBucket = ordered[0];

  return (
    <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} alignItems="center">
          <ShowChartOutlined color="primary" />
          <Box>
            <Typography variant="h6">Serie temporal</Typography>
            <Typography variant="body2" color="text.secondary">
              Grafico limitado a pagina atual: {ordered.length} ponto(s) em {series.length} serie(s).
            </Typography>
          </Box>
        </Stack>
        {ordered.length === 0 ? (
          <Typography color="text.secondary">Sem dados para desenhar o grafico.</Typography>
        ) : (
          <>
            <Box sx={{ width: "100%", height: { xs: 300, md: 380 } }}>
              <LineChart
                xAxis={[
                  {
                    data: timestamps.map((timestamp) => new Date(timestamp)),
                    scaleType: "time",
                    valueFormatter: (value) =>
                      value instanceof Date ? formatDateTime(value.toISOString()) : String(value),
                  },
                ]}
                series={series}
                height={360}
                margin={{ left: 70, right: 24, top: 32, bottom: 64 }}
                grid={{ horizontal: true }}
                slotProps={{ legend: { direction: "horizontal" } }}
              />
            </Box>
            <Typography variant="body2" color="text.secondary" aria-live="polite">
              Valores do grafico usam averageValue do backend. Tooltip: inicio{" "}
              {formatDateTime(firstBucket?.bucketStart)}, fim{" "}
              {formatDateTime(firstBucket?.bucketEnd)}, media{" "}
              {formatNumber(firstBucket?.averageValue, firstBucket?.unidade)}, minimo{" "}
              {formatNumber(firstBucket?.minimumValue, firstBucket?.unidade)}, maximo{" "}
              {formatNumber(firstBucket?.maximumValue, firstBucket?.unidade)}, amostras{" "}
              {firstBucket?.sampleCount ?? 0}.
            </Typography>
          </>
        )}
      </Stack>
    </Paper>
  );
}
