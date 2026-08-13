import { AddOutlined, SearchOutlined } from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputAdornment,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import {
  activateIntegrationProfile,
  createIntegrationProfile,
  deactivateIntegrationProfile,
  listIntegrationProfiles,
} from "../../api/sensorIntegrations";
import type { ProfileCreateRequest, ProfileResponse } from "../../types/sensorIntegrations";
import { ConfirmActionDialog } from "./ConfirmActionDialog";
import { ProfileForm } from "./ProfileForm";
import { ProfilesTable } from "./ProfilesTable";

type StatusFilter = "all" | "active" | "inactive";

export function IntegrationsAdminPage() {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<StatusFilter>("all");
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedProfile, setSelectedProfile] = useState<ProfileResponse | null>(null);

  const profiles = useQuery({
    queryKey: ["sensor-integrations", "profiles"],
    queryFn: () => listIntegrationProfiles(true, session),
  });

  const createProfile = useMutation({
    mutationFn: (payload: ProfileCreateRequest) => createIntegrationProfile(payload, session),
    onSuccess: async () => {
      setCreateOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
  });

  const activateProfile = useMutation({
    mutationFn: (profileId: string) => activateIntegrationProfile(profileId, session),
    onSuccess: async () => {
      setSelectedProfile(null);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
  });

  const deactivateProfile = useMutation({
    mutationFn: (profileId: string) => deactivateIntegrationProfile(profileId, session),
    onSuccess: async () => {
      setSelectedProfile(null);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
  });

  const filteredProfiles = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    return [...(profiles.data ?? [])]
      .filter((profile) => {
        if (status === "active" && !profile.ativo) return false;
        if (status === "inactive" && profile.ativo) return false;
        if (!normalized) return true;
        return [profile.nome, profile.descricao ?? "", profile.source]
          .some((value) => value.toLowerCase().includes(normalized));
      })
      .sort((a, b) => a.nome.localeCompare(b.nome, undefined, { sensitivity: "base" }));
  }, [profiles.data, query, status]);

  const togglePending = activateProfile.isPending || deactivateProfile.isPending;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={2}>
        <Box>
          <Typography variant="h4">Integracoes</Typography>
          <Typography color="text.secondary">
            Gerencie perfis, parsers e vinculos administrativos de sensores.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button component={RouterLink} to="/integracoes/snapshot" variant="outlined">
            Snapshot
          </Button>
          <Button
            startIcon={<AddOutlined />}
            variant="contained"
            onClick={() => setCreateOpen(true)}
          >
            Novo perfil
          </Button>
        </Stack>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          <TextField
            label="Buscar"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            fullWidth
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined />
                </InputAdornment>
              ),
            }}
          />
          <FormControl sx={{ minWidth: 180 }}>
            <InputLabel id="integration-profile-status-label">Status</InputLabel>
            <Select
              id="integration-profile-status"
              labelId="integration-profile-status-label"
              label="Status"
              value={status}
              onChange={(event) => setStatus(event.target.value as StatusFilter)}
            >
              <MenuItem value="all">Todos</MenuItem>
              <MenuItem value="active">Ativos</MenuItem>
              <MenuItem value="inactive">Inativos</MenuItem>
            </Select>
          </FormControl>
        </Stack>
      </Paper>

      {profiles.isLoading && <CircularProgress size={24} />}
      {profiles.isError && <Alert severity="error">{profiles.error.message}</Alert>}
      {!profiles.isLoading && !profiles.isError && (
        <ProfilesTable profiles={filteredProfiles} onToggleActive={setSelectedProfile} />
      )}

      <Dialog open={createOpen} onClose={() => !createProfile.isPending && setCreateOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Novo perfil</DialogTitle>
        <DialogContent>
          <ProfileForm
            pending={createProfile.isPending}
            error={createProfile.error}
            onSubmit={(payload) => createProfile.mutate(payload as ProfileCreateRequest)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)} disabled={createProfile.isPending}>
            Fechar
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmActionDialog
        open={Boolean(selectedProfile)}
        title={selectedProfile?.ativo ? "Desativar perfil" : "Ativar perfil"}
        message={
          selectedProfile?.ativo
            ? `Desativar o perfil ${selectedProfile.nome}?`
            : `Ativar o perfil ${selectedProfile?.nome ?? ""}?`
        }
        confirmLabel={selectedProfile?.ativo ? "Desativar" : "Ativar"}
        pending={togglePending}
        error={(activateProfile.error ?? deactivateProfile.error)?.message}
        onCancel={() => setSelectedProfile(null)}
        onConfirm={() => {
          if (!selectedProfile) return;
          if (selectedProfile.ativo) {
            deactivateProfile.mutate(selectedProfile.id);
          } else {
            activateProfile.mutate(selectedProfile.id);
          }
        }}
      />
    </Stack>
  );
}
