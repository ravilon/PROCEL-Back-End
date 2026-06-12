import { Alert, Card, CardContent, Grid, Stack, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../auth/AuthContext";
import { apiBaseUrl, apiRequest } from "../lib/api";
import type { Pessoa } from "../types";

export function DashboardPage() {
  const { session, hasAnyRole } = useAuth();
  const profile = useQuery({
    queryKey: ["pessoa", session?.userId],
    queryFn: () =>
      apiRequest<Pessoa>(`/api/pessoas/${session!.userId}`, {}, session),
    enabled: Boolean(session),
  });

  return (
    <Stack spacing={3}>
      <div>
        <Typography variant="h4">Visao geral</Typography>
        <Typography color="text.secondary">
          Estado atual do acesso ao sistema.
        </Typography>
      </div>

      {profile.isError && (
        <Alert severity="error">
          Nao foi possivel consultar o perfil: {profile.error.message}
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary">Usuario</Typography>
              <Typography variant="h6">
                {profile.data?.nome ?? session?.userId}
              </Typography>
              <Typography variant="body2">{session?.email}</Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary">Perfil de acesso</Typography>
              <Typography variant="h6">{session?.roles.join(", ")}</Typography>
              <Typography variant="body2">
                {hasAnyRole("ADMIN", "OPERADOR")
                  ? "Acesso gerencial"
                  : "Acesso pessoal"}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <Card>
            <CardContent>
              <Typography color="text.secondary">API configurada</Typography>
              <Typography variant="body1" sx={{ wordBreak: "break-all" }}>
                {apiBaseUrl}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Stack>
  );
}
