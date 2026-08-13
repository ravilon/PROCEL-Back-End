import { AddOutlined, CheckCircleOutlined, EditOutlined } from "@mui/icons-material";
import {
  Alert,
  Button,
  Chip,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Tooltip,
  Typography,
} from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import {
  activateParserVersion,
  createParserVersion,
  updateParserVersion,
} from "../../api/sensorIntegrations";
import { ApiError } from "../../lib/api";
import type {
  ParserVersionRequest,
  ParserVersionResponse,
} from "../../types/sensorIntegrations";
import { ConfirmActionDialog } from "./ConfirmActionDialog";
import { ParserVersionEditor } from "./ParserVersionEditor";
import { statusColor } from "./status";

interface VersionsPanelProps {
  profileId: string;
  versions: ParserVersionResponse[];
}

interface ActivationTarget {
  version: ParserVersionResponse;
  expectedActiveVersionId: string | null;
}

export function VersionsPanel({ profileId, versions }: VersionsPanelProps) {
  const { session } = useAuth();
  const queryClient = useQueryClient();
  const sorted = useMemo(
    () => [...versions].sort((a, b) => b.version - a.version),
    [versions],
  );
  const activeVersion = versions.find((version) => version.status === "ACTIVE") ?? null;
  const editableDraft = sorted.find((version) => version.status === "DRAFT") ?? null;
  const [selectedVersionId, setSelectedVersionId] = useState<string | "new">(
    editableDraft?.id ?? sorted[0]?.id ?? "new",
  );
  const [activationTarget, setActivationTarget] = useState<ActivationTarget | null>(null);
  const [activationConflict, setActivationConflict] = useState("");

  const selectedVersion =
    selectedVersionId === "new"
      ? null
      : sorted.find((version) => version.id === selectedVersionId) ?? null;

  const invalidateVersions = async () => {
    await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "profile", profileId] });
    await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "versions", profileId] });
    await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "snapshot"] });
  };

  const createVersion = useMutation({
    mutationFn: (payload: ParserVersionRequest) => createParserVersion(profileId, payload, session),
    onSuccess: async (created) => {
      setSelectedVersionId(created.id);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "versions", profileId] });
    },
  });

  const updateVersion = useMutation({
    mutationFn: ({ versionId, payload }: { versionId: string; payload: ParserVersionRequest }) =>
      updateParserVersion(profileId, versionId, payload, session),
    onSuccess: async (updated) => {
      setSelectedVersionId(updated.id);
      await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "versions", profileId] });
    },
  });

  const activateVersion = useMutation({
    retry: false,
    mutationFn: (target: ActivationTarget) =>
      activateParserVersion(
        profileId,
        target.version.id,
        { expectedActiveVersionId: target.expectedActiveVersionId },
        session,
      ),
    onSuccess: async () => {
      setActivationTarget(null);
      setActivationConflict("");
      await invalidateVersions();
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.code === "PARSER_ACTIVATION_CONFLICT") {
        setActivationTarget(null);
        setActivationConflict("A versao ativa mudou. As versoes foram recarregadas; abra a confirmacao novamente.");
        await queryClient.invalidateQueries({ queryKey: ["sensor-integrations", "versions", profileId] });
      }
    },
  });

  const editorError = createVersion.error ?? updateVersion.error;
  const activationError =
    activateVersion.error instanceof ApiError && activateVersion.error.code === "PARSER_ACTIVATION_CONFLICT"
      ? undefined
      : activateVersion.error?.message;

  return (
    <Stack spacing={2}>
      {activationConflict && <Alert severity="warning">{activationConflict}</Alert>}
      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="flex-start">
        <Paper variant="outlined" sx={{ width: { xs: "100%", lg: 340 } }}>
          <Stack spacing={1} sx={{ p: 2 }}>
            <Button
              startIcon={<AddOutlined />}
              variant={selectedVersionId === "new" ? "contained" : "outlined"}
              onClick={() => setSelectedVersionId("new")}
            >
              Nova versao DRAFT
            </Button>
          </Stack>
          <Divider />
          <List dense>
            {sorted.map((version) => (
              <ListItem
                key={version.id}
                secondaryAction={
                  <Stack direction="row" spacing={0.5}>
                    <Tooltip title="Editar ou visualizar">
                      <IconButton onClick={() => setSelectedVersionId(version.id)} aria-label="Abrir versao">
                        <EditOutlined />
                      </IconButton>
                    </Tooltip>
                    {version.status === "DRAFT" && (
                      <Tooltip title="Ativar versao">
                        <IconButton
                          onClick={() => {
                            setActivationConflict("");
                            setActivationTarget({
                              version,
                              expectedActiveVersionId: activeVersion?.id ?? null,
                            });
                          }}
                          aria-label="Ativar versao"
                        >
                          <CheckCircleOutlined />
                        </IconButton>
                      </Tooltip>
                    )}
                  </Stack>
                }
              >
                <ListItemText
                  primary={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography>Versao {version.version}</Typography>
                      <Chip label={version.status} color={statusColor(version.status)} size="small" />
                    </Stack>
                  }
                  secondary={version.publishedAt ? new Date(version.publishedAt).toLocaleString() : "Nao publicada"}
                />
              </ListItem>
            ))}
            {sorted.length === 0 && (
              <ListItem>
                <ListItemText primary="Nenhuma versao criada." />
              </ListItem>
            )}
          </List>
        </Paper>
        <Stack sx={{ flex: 1, width: "100%" }}>
          <ParserVersionEditor
            key={selectedVersion?.id ?? "new"}
            version={selectedVersion}
            pending={createVersion.isPending || updateVersion.isPending}
            error={editorError}
            onSubmit={(payload) => {
              if (selectedVersion) {
                updateVersion.mutate({ versionId: selectedVersion.id, payload });
              } else {
                createVersion.mutate(payload);
              }
            }}
          />
        </Stack>
      </Stack>
      <ConfirmActionDialog
        open={Boolean(activationTarget)}
        title="Ativar versao"
        message={
          activationTarget
            ? `Ativar a versao ${activationTarget.version.version}? A versao ativa atual sera substituida se ela nao tiver mudado.`
            : ""
        }
        confirmLabel="Ativar"
        pending={activateVersion.isPending}
        error={activationError}
        onCancel={() => setActivationTarget(null)}
        onConfirm={() => activationTarget && activateVersion.mutate(activationTarget)}
      />
    </Stack>
  );
}
