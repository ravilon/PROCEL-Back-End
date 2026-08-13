import { AddOutlined, PowerSettingsNewOutlined } from "@mui/icons-material";
import {
  Alert,
  Autocomplete,
  Button,
  Chip,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  activateIntegrationBinding,
  createIntegrationBinding,
  deactivateIntegrationBinding,
  searchCatalogSensors,
} from "../../api/sensorIntegrations";
import { ApiError } from "../../lib/api";
import type { Sensor } from "../../types/sensors";
import type { BindingResponse } from "../../types/sensorIntegrations";
import { ConfirmActionDialog } from "./ConfirmActionDialog";
import { activeLabel } from "./status";

interface BindingsPanelProps {
  profileId: string;
  bindings: BindingResponse[];
}

function bindingMessage(error?: Error | null) {
  if (!(error instanceof ApiError)) return error?.message;
  if (error.code === "BINDING_ALREADY_ACTIVE") return "Este binding ja esta ativo.";
  if (error.code === "BINDING_ALREADY_INACTIVE") return "Este binding ja esta inativo.";
  if (error.code === "PROFILE_INACTIVE") return "Ative o perfil antes de alterar bindings.";
  if (error.code === "SENSOR_INACTIVE") return "O sensor esta inativo.";
  return error.message;
}

export function BindingsPanel({ profileId, bindings }: BindingsPanelProps) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [sensorQuery, setSensorQuery] = useState("");
  const [selectedSensor, setSelectedSensor] = useState<Sensor | null>(null);
  const [targetBinding, setTargetBinding] = useState<BindingResponse | null>(null);

  const sensors = useQuery({
    queryKey: ["catalog", "sensors", sensorQuery],
    queryFn: () => searchCatalogSensors(sensorQuery, session),
  });

  const activeSensors = useMemo(
    () => (sensors.data ?? []).filter((sensor) => sensor.ativo),
    [sensors.data],
  );

  const invalidateBindings = async () => {
    await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "bindings", profileId] });
    await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "snapshot"] });
  };

  const createBinding = useMutation({
    mutationFn: (sensorExternalId: string) =>
      createIntegrationBinding(profileId, { sensorExternalId }, session),
    onSuccess: async () => {
      setSelectedSensor(null);
      await invalidateBindings();
    },
  });

  const activateBinding = useMutation({
    mutationFn: (bindingId: string) => activateIntegrationBinding(bindingId, session),
    onSuccess: async () => {
      setTargetBinding(null);
      await invalidateBindings();
    },
  });

  const deactivateBinding = useMutation({
    mutationFn: (bindingId: string) => deactivateIntegrationBinding(bindingId, session),
    onSuccess: async () => {
      setTargetBinding(null);
      await invalidateBindings();
    },
  });

  const targetError = bindingMessage(activateBinding.error ?? deactivateBinding.error);

  return (
    <Stack spacing={2}>
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          <Autocomplete
            options={activeSensors}
            loading={sensors.isLoading}
            value={selectedSensor}
            onInputChange={(_, value) => setSensorQuery(value)}
            onChange={(_, value) => setSelectedSensor(value)}
            getOptionLabel={(sensor) => `${sensor.nome} (${sensor.externalId})`}
            isOptionEqualToValue={(option, value) => option.externalId === value.externalId}
            sx={{ flex: 1 }}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Sensor ativo"
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {sensors.isLoading && <CircularProgress size={18} />}
                      {params.InputProps.endAdornment}
                    </>
                  ),
                }}
              />
            )}
          />
          <Button
            startIcon={<AddOutlined />}
            variant="contained"
            disabled={!selectedSensor || createBinding.isPending}
            onClick={() => selectedSensor && createBinding.mutate(selectedSensor.externalId)}
          >
            Vincular
          </Button>
        </Stack>
        {sensors.isError && <Alert severity="error" sx={{ mt: 2 }}>{sensors.error.message}</Alert>}
        {createBinding.error && (
          <Alert severity="error" sx={{ mt: 2 }}>{bindingMessage(createBinding.error)}</Alert>
        )}
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Sensor</TableCell>
              <TableCell>External ID</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Criado em</TableCell>
              <TableCell>Desativado em</TableCell>
              <TableCell align="right">Acoes</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {bindings.map((binding) => (
              <TableRow key={binding.id}>
                <TableCell>{binding.sensorNome}</TableCell>
                <TableCell>{binding.sensorExternalId}</TableCell>
                <TableCell>
                  <Chip
                    label={activeLabel(binding.ativo)}
                    color={binding.ativo ? "success" : "default"}
                    size="small"
                  />
                </TableCell>
                <TableCell>{new Date(binding.createdAt).toLocaleString()}</TableCell>
                <TableCell>
                  {binding.deactivatedAt ? new Date(binding.deactivatedAt).toLocaleString() : "-"}
                </TableCell>
                <TableCell align="right">
                  <Tooltip title={binding.ativo ? "Desativar binding" : "Ativar binding"}>
                    <IconButton onClick={() => setTargetBinding(binding)} aria-label="Alterar binding">
                      <PowerSettingsNewOutlined />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {bindings.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center">Nenhum binding encontrado.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <ConfirmActionDialog
        open={Boolean(targetBinding)}
        title={targetBinding?.ativo ? "Desativar binding" : "Ativar binding"}
        message={
          targetBinding?.ativo
            ? `Desativar binding do sensor ${targetBinding.sensorNome}?`
            : `Ativar binding do sensor ${targetBinding?.sensorNome ?? ""}?`
        }
        confirmLabel={targetBinding?.ativo ? "Desativar" : "Ativar"}
        pending={activateBinding.isPending || deactivateBinding.isPending}
        error={targetError}
        onCancel={() => setTargetBinding(null)}
        onConfirm={() => {
          if (!targetBinding) return;
          if (targetBinding.ativo) {
            deactivateBinding.mutate(targetBinding.id);
          } else {
            activateBinding.mutate(targetBinding.id);
          }
        }}
      />
    </Stack>
  );
}
