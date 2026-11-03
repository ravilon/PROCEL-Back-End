import { InsightsOutlined } from "@mui/icons-material";
import { Paper, Stack, Typography } from "@mui/material";

export function AnalyticsEmptyState({ message }: { message: string }) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack spacing={1} alignItems="center" textAlign="center">
        <InsightsOutlined color="disabled" fontSize="large" />
        <Typography fontWeight={700}>Nenhum bucket encontrado</Typography>
        <Typography color="text.secondary">{message}</Typography>
      </Stack>
    </Paper>
  );
}
