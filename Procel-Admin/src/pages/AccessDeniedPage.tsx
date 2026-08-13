import { Alert, Box, Button, Paper, Stack, Typography } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";

export function AccessDeniedPage() {
  return (
    <Box sx={{ display: "grid", minHeight: "60vh", placeItems: "center", p: 2 }}>
      <Paper variant="outlined" sx={{ maxWidth: 520, p: 3 }}>
        <Stack spacing={2}>
          <Typography variant="h4">Acesso negado</Typography>
          <Alert severity="warning">
            Sua conta nao possui permissao para acessar esta area.
          </Alert>
          <Button component={RouterLink} to="/" variant="contained">
            Voltar para a visao geral
          </Button>
        </Stack>
      </Paper>
    </Box>
  );
}
