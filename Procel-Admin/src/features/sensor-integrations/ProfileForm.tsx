import {
  Alert,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useState, type FormEvent } from "react";
import { ApiError } from "../../lib/api";
import type {
  MedicaoIngestaoSource,
  ProfileCreateRequest,
  ProfileResponse,
  ProfileUpdateRequest,
} from "../../types/sensorIntegrations";

const sources: MedicaoIngestaoSource[] = ["MQTT", "REST", "FILE", "API"];

interface ProfileFormProps {
  profile?: ProfileResponse;
  sourceReadonly?: boolean;
  pending: boolean;
  error?: Error | null;
  onSubmit: (payload: ProfileCreateRequest | ProfileUpdateRequest) => void;
}

export function ProfileForm({
  profile,
  sourceReadonly = false,
  pending,
  error,
  onSubmit,
}: ProfileFormProps) {
  const [nome, setNome] = useState(profile?.nome ?? "");
  const [descricao, setDescricao] = useState(profile?.descricao ?? "");
  const [source, setSource] = useState<MedicaoIngestaoSource>(profile?.source ?? "REST");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit({
      nome: nome.trim(),
      descricao: descricao.trim() || null,
      source: sourceReadonly ? null : source,
    });
  };

  const sourceMessage =
    error instanceof ApiError && error.code === "PROFILE_SOURCE_IMMUTABLE"
      ? "Source nao pode ser alterado apos a primeira publicacao."
      : error?.message;

  return (
    <Paper component="form" variant="outlined" sx={{ p: 2 }} onSubmit={submit}>
      <Stack spacing={2}>
        <Typography variant="h6">{profile ? "Configuracao do perfil" : "Novo perfil"}</Typography>
        {sourceReadonly && (
          <Alert severity="info">
            Source fica imutavel apos uma versao ser publicada.
          </Alert>
        )}
        <TextField
          label="Nome"
          value={nome}
          onChange={(event) => setNome(event.target.value)}
          required
          disabled={pending}
        />
        <TextField
          label="Descricao"
          value={descricao}
          onChange={(event) => setDescricao(event.target.value)}
          multiline
          minRows={3}
          disabled={pending}
        />
        <FormControl disabled={pending || sourceReadonly}>
          <InputLabel id="integration-profile-source-label">Source</InputLabel>
          <Select
            id="integration-profile-source"
            labelId="integration-profile-source-label"
            label="Source"
            value={source}
            onChange={(event) => setSource(event.target.value as MedicaoIngestaoSource)}
          >
            {sources.map((item) => (
              <MenuItem key={item} value={item}>{item}</MenuItem>
            ))}
          </Select>
        </FormControl>
        <Button type="submit" variant="contained" disabled={pending || !nome.trim()}>
          Salvar
        </Button>
        {sourceMessage && <Alert severity="error">{sourceMessage}</Alert>}
      </Stack>
    </Paper>
  );
}
