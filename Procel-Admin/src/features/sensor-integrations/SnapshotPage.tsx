import {
  Alert,
  Button,
  Chip,
  CircularProgress,
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
import { useQuery } from "@tanstack/react-query";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { getIntegrationSnapshot } from "../../api/sensorIntegrations";

export function SnapshotPage() {
  const { session } = useAuth();
  const snapshot = useQuery({
    queryKey: ["sensor-integrations", "snapshot"],
    queryFn: () => getIntegrationSnapshot(session),
  });

  if (snapshot.isLoading) return <CircularProgress size={24} />;
  if (snapshot.isError) return <Alert severity="error">{snapshot.error.message}</Alert>;
  if (!snapshot.data) return <Alert severity="error">Snapshot indisponivel.</Alert>;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={2}>
        <Stack>
          <Typography variant="h4">Snapshot de integracoes</Typography>
          <Typography color="text.secondary">
            Versao {snapshot.data.version} gerada em {new Date(snapshot.data.generatedAt).toLocaleString()}
          </Typography>
        </Stack>
        <Button component={RouterLink} to="/integracoes" variant="outlined">
          Voltar
        </Button>
      </Stack>

      {snapshot.data.profiles.map((profile) => (
        <Paper key={profile.id} variant="outlined" sx={{ p: 2 }}>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: "wrap" }}>
              <Typography variant="h6">{profile.nome}</Typography>
              <Chip label={profile.source} size="small" />
              <Chip label={`ACTIVE v${profile.activeParserVersion.version}`} color="success" size="small" />
            </Stack>
            <Typography variant="subtitle2">Parser</Typography>
            <TableContainer>
              <Table size="small">
                <TableBody>
                  <TableRow>
                    <TableCell>Modo</TableCell>
                    <TableCell>{profile.activeParserVersion.sensorResolutionMode}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>messageIdPointer</TableCell>
                    <TableCell>{profile.activeParserVersion.messageIdPointer}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>sensorExternalIdPointer</TableCell>
                    <TableCell>{profile.activeParserVersion.sensorExternalIdPointer ?? "-"}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>timestampPointer</TableCell>
                    <TableCell>{profile.activeParserVersion.timestampPointer}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>sourceReceivedAtPointer</TableCell>
                    <TableCell>{profile.activeParserVersion.sourceReceivedAtPointer ?? "-"}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell>timestampFormat</TableCell>
                    <TableCell>{profile.activeParserVersion.timestampFormat}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="subtitle2">Mappings</Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Parametro</TableCell>
                    <TableCell>Pointer</TableCell>
                    <TableCell>Obrigatorio</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {profile.activeParserVersion.valueMappings.map((mapping) => (
                    <TableRow key={`${profile.id}-${mapping.parameterName}`}>
                      <TableCell>{mapping.parameterName}</TableCell>
                      <TableCell>{mapping.valuePointer}</TableCell>
                      <TableCell>{mapping.required ? "Sim" : "Nao"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Typography variant="subtitle2">Bindings</Typography>
            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Sensor</TableCell>
                    <TableCell>External ID</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {profile.bindings.map((binding) => (
                    <TableRow key={`${profile.id}-${binding.sensorExternalId}`}>
                      <TableCell>{binding.sensorNome}</TableCell>
                      <TableCell>{binding.sensorExternalId}</TableCell>
                    </TableRow>
                  ))}
                  {profile.bindings.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={2} align="center">Nenhum binding ativo.</TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </Paper>
      ))}
      {snapshot.data.profiles.length === 0 && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography color="text.secondary">Nenhum perfil ativo no snapshot.</Typography>
        </Paper>
      )}
    </Stack>
  );
}
