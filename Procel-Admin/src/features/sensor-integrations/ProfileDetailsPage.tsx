import { ArrowBackOutlined, PowerSettingsNewOutlined } from "@mui/icons-material";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Paper,
  Stack,
  Tab,
  Tabs,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import {
  activateIntegrationProfile,
  deactivateIntegrationProfile,
  getIntegrationProfile,
  listIntegrationBindings,
  listParserVersions,
  updateIntegrationProfile,
} from "../../api/sensorIntegrations";
import { ApiError } from "../../lib/api";
import type { ProfileUpdateRequest } from "../../types/sensorIntegrations";
import { BindingsPanel } from "./BindingsPanel";
import { ConfirmActionDialog } from "./ConfirmActionDialog";
import { ProfileForm } from "./ProfileForm";
import { activeLabel, statusColor } from "./status";
import { VersionsPanel } from "./VersionsPanel";

type TabKey = "config" | "parser" | "versions" | "bindings";

export function ProfileDetailsPage() {
  const { profileId = "" } = useParams();
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<TabKey>("config");
  const [toggleOpen, setToggleOpen] = useState(false);
  const [sourceImmutableMessage, setSourceImmutableMessage] = useState("");

  const profile = useQuery({
    queryKey: ["sensor-integrations", "profile", profileId],
    queryFn: () => getIntegrationProfile(profileId, session),
    enabled: Boolean(profileId),
  });

  const versions = useQuery({
    queryKey: ["sensor-integrations", "versions", profileId],
    queryFn: () => listParserVersions(profileId, session),
    enabled: Boolean(profileId),
  });

  const bindings = useQuery({
    queryKey: ["sensor-integrations", "bindings", profileId],
    queryFn: () => listIntegrationBindings(profileId, true, session),
    enabled: Boolean(profileId),
  });

  const hasPublishedVersion = (versions.data ?? []).some(
    (version) => version.status === "ACTIVE" || version.status === "INACTIVE",
  );
  const activeVersion = (versions.data ?? []).find((version) => version.status === "ACTIVE") ?? null;

  const updateProfile = useMutation({
    mutationFn: (payload: ProfileUpdateRequest) => updateIntegrationProfile(profileId, payload, session),
    onSuccess: async () => {
      setSourceImmutableMessage("");
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profile", profileId] });
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "PROFILE_SOURCE_IMMUTABLE") {
        setSourceImmutableMessage("Source nao pode ser alterado apos a primeira publicacao.");
        await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profile", profileId] });
        await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "versions", profileId] });
      }
    },
  });

  const activateProfile = useMutation({
    mutationFn: () => activateIntegrationProfile(profileId, session),
    onSuccess: async () => {
      setToggleOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profile", profileId] });
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
  });

  const deactivateProfile = useMutation({
    mutationFn: () => deactivateIntegrationProfile(profileId, session),
    onSuccess: async () => {
      setToggleOpen(false);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profile", profileId] });
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profiles"] });
    },
  });

  const loading = profile.isLoading || versions.isLoading || bindings.isLoading;

  if (loading) return <CircularProgress size={24} />;

  if (profile.isError) return <Alert severity="error">{profile.error.message}</Alert>;
  if (!profile.data) return <Alert severity="error">Perfil nao encontrado.</Alert>;

  const togglePending = activateProfile.isPending || deactivateProfile.isPending;
  const toggleError = (activateProfile.error ?? deactivateProfile.error)?.message;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={2}>
        <Box>
          <Button component={RouterLink} to="/integracoes" startIcon={<ArrowBackOutlined />}>
            Voltar
          </Button>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1, flexWrap: "wrap" }}>
            <Typography variant="h4">{profile.data.nome}</Typography>
            <Chip
              label={activeLabel(profile.data.ativo)}
              color={profile.data.ativo ? "success" : "default"}
              size="small"
            />
            {activeVersion && (
              <Chip
                label={`ACTIVE v${activeVersion.version}`}
                color={statusColor(activeVersion.status)}
                size="small"
              />
            )}
          </Stack>
          <Typography color="text.secondary">Source {profile.data.source}</Typography>
        </Box>
        <Button
          startIcon={<PowerSettingsNewOutlined />}
          variant="outlined"
          onClick={() => setToggleOpen(true)}
        >
          {profile.data.ativo ? "Desativar perfil" : "Ativar perfil"}
        </Button>
      </Stack>

      {sourceImmutableMessage && <Alert severity="warning">{sourceImmutableMessage}</Alert>}
      {versions.isError && <Alert severity="error">{versions.error.message}</Alert>}
      {bindings.isError && <Alert severity="error">{bindings.error.message}</Alert>}

      <Paper variant="outlined">
        <Tabs
          value={tab}
          onChange={(_, value) => setTab(value)}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Configuracao" value="config" />
          <Tab label="Parser e mappings" value="parser" />
          <Tab label="Versoes" value="versions" />
          <Tab label="Sensores vinculados" value="bindings" />
        </Tabs>
      </Paper>

      {tab === "config" && (
        <ProfileForm
          profile={profile.data}
          sourceReadonly={hasPublishedVersion}
          pending={updateProfile.isPending}
          error={updateProfile.error}
          onSubmit={(payload) => updateProfile.mutate(payload as ProfileUpdateRequest)}
        />
      )}
      {tab === "parser" && (
        <VersionsPanel profileId={profileId} versions={versions.data ?? []} />
      )}
      {tab === "versions" && (
        <VersionsPanel profileId={profileId} versions={versions.data ?? []} />
      )}
      {tab === "bindings" && (
        <BindingsPanel profileId={profileId} bindings={bindings.data ?? []} />
      )}

      <ConfirmActionDialog
        open={toggleOpen}
        title={profile.data.ativo ? "Desativar perfil" : "Ativar perfil"}
        message={
          profile.data.ativo
            ? `Desativar o perfil ${profile.data.nome}?`
            : `Ativar o perfil ${profile.data.nome}?`
        }
        confirmLabel={profile.data.ativo ? "Desativar" : "Ativar"}
        pending={togglePending}
        error={toggleError}
        onCancel={() => setToggleOpen(false)}
        onConfirm={() => {
          if (profile.data.ativo) {
            deactivateProfile.mutate();
          } else {
            activateProfile.mutate();
          }
        }}
      />
    </Stack>
  );
}
