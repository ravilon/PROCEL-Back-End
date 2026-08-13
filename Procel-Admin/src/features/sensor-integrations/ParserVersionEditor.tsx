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
import { useMemo, useState, type FormEvent } from "react";
import { ApiError } from "../../lib/api";
import type {
  ParserVersionRequest,
  ParserVersionResponse,
  SensorResolutionMode,
  ValueMappingRequest,
} from "../../types/sensorIntegrations";
import { MappingsTableEditor } from "./MappingsTableEditor";
import { pointerError, toNullable, validateParserRequest } from "./validation";

interface ParserVersionEditorProps {
  version?: ParserVersionResponse | null;
  pending: boolean;
  error?: Error | null;
  onSubmit: (payload: ParserVersionRequest) => void;
}

const emptyMappings: ValueMappingRequest[] = [
  { parameterName: "", valuePointer: "", required: true },
];

export function ParserVersionEditor({ version, pending, error, onSubmit }: ParserVersionEditorProps) {
  const readonly = Boolean(version && version.status !== "DRAFT");
  const [sensorResolutionMode, setSensorResolutionMode] = useState<SensorResolutionMode>(
    version?.sensorResolutionMode ?? "ROUTE_SENSOR",
  );
  const [messageIdPointer, setMessageIdPointer] = useState(version?.messageIdPointer ?? "/messageId");
  const [sensorExternalIdPointer, setSensorExternalIdPointer] = useState(version?.sensorExternalIdPointer ?? "");
  const [timestampPointer, setTimestampPointer] = useState(version?.timestampPointer ?? "/timestamp");
  const [sourceReceivedAtPointer, setSourceReceivedAtPointer] = useState(version?.sourceReceivedAtPointer ?? "");
  const [mappings, setMappings] = useState<ValueMappingRequest[]>(
    version?.valueMappings.map((mapping) => ({
      parameterName: mapping.parameterName,
      valuePointer: mapping.valuePointer,
      required: mapping.required,
    })) ?? emptyMappings,
  );
  const [submitErrors, setSubmitErrors] = useState<string[]>([]);

  const payload = useMemo<ParserVersionRequest>(() => ({
    sensorResolutionMode,
    messageIdPointer: messageIdPointer.trim(),
    sensorExternalIdPointer:
      sensorResolutionMode === "ROUTE_SENSOR" ? null : toNullable(sensorExternalIdPointer),
    timestampPointer: timestampPointer.trim(),
    sourceReceivedAtPointer: toNullable(sourceReceivedAtPointer),
    timestampFormat: "ISO_INSTANT",
    valueMappings: mappings.map((mapping) => ({
      parameterName: mapping.parameterName.trim(),
      valuePointer: mapping.valuePointer.trim(),
      required: mapping.required,
    })),
  }), [
    mappings,
    messageIdPointer,
    sensorExternalIdPointer,
    sensorResolutionMode,
    sourceReceivedAtPointer,
    timestampPointer,
  ]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const errors = validateParserRequest(payload);
    setSubmitErrors(errors);
    if (errors.length === 0) {
      onSubmit(payload);
    }
  };

  const friendlyError =
    error instanceof ApiError && error.code === "PARSER_VERSION_IMMUTABLE"
      ? "Somente versoes DRAFT podem ser editadas."
      : error instanceof ApiError && error.code === "POINTER_INVALID"
        ? "Revise os JSON Pointers informados."
        : error instanceof ApiError && error.code === "TOO_MANY_MAPPINGS"
          ? "O limite de 100 mappings foi excedido."
          : error?.message;

  return (
    <Paper component="form" variant="outlined" sx={{ p: 2 }} onSubmit={submit}>
      <Stack spacing={2}>
        <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}>
          <Typography variant="h6">
            {version ? `Versao ${version.version}` : "Nova versao DRAFT"}
          </Typography>
          {version && <Typography color="text.secondary">{version.status}</Typography>}
        </Stack>
        {readonly && (
          <Alert severity="info">
            Versoes publicadas sao imutaveis.
          </Alert>
        )}
        <FormControl disabled={pending || readonly}>
          <InputLabel id="parser-sensor-resolution-mode-label">Modo de sensor</InputLabel>
          <Select
            id="parser-sensor-resolution-mode"
            labelId="parser-sensor-resolution-mode-label"
            label="Modo de sensor"
            value={sensorResolutionMode}
            onChange={(event) => setSensorResolutionMode(event.target.value as SensorResolutionMode)}
          >
            <MenuItem value="ROUTE_SENSOR">ROUTE_SENSOR</MenuItem>
            <MenuItem value="PAYLOAD_POINTER">PAYLOAD_POINTER</MenuItem>
          </Select>
        </FormControl>
        <TextField
          label="messageIdPointer"
          value={messageIdPointer}
          onChange={(event) => setMessageIdPointer(event.target.value)}
          disabled={pending || readonly}
          required
          error={Boolean(pointerError(messageIdPointer, true))}
          helperText={pointerError(messageIdPointer, true)}
        />
        <TextField
          label="sensorExternalIdPointer"
          value={sensorExternalIdPointer}
          onChange={(event) => setSensorExternalIdPointer(event.target.value)}
          disabled={pending || readonly || sensorResolutionMode === "ROUTE_SENSOR"}
          required={sensorResolutionMode === "PAYLOAD_POINTER"}
          error={sensorResolutionMode === "PAYLOAD_POINTER" && Boolean(pointerError(sensorExternalIdPointer, true))}
          helperText={
            sensorResolutionMode === "ROUTE_SENSOR"
              ? "Vazio em ROUTE_SENSOR"
              : pointerError(sensorExternalIdPointer, true)
          }
        />
        <TextField
          label="timestampPointer"
          value={timestampPointer}
          onChange={(event) => setTimestampPointer(event.target.value)}
          disabled={pending || readonly}
          required
          error={Boolean(pointerError(timestampPointer, true))}
          helperText={pointerError(timestampPointer, true)}
        />
        <TextField
          label="sourceReceivedAtPointer"
          value={sourceReceivedAtPointer}
          onChange={(event) => setSourceReceivedAtPointer(event.target.value)}
          disabled={pending || readonly}
          error={Boolean(pointerError(sourceReceivedAtPointer, false))}
          helperText={pointerError(sourceReceivedAtPointer, false)}
        />
        <TextField label="timestampFormat" value="ISO_INSTANT" disabled />
        <MappingsTableEditor mappings={mappings} readonly={pending || readonly} onChange={setMappings} />
        {submitErrors.map((item) => (
          <Alert key={item} severity="error">{item}</Alert>
        ))}
        {friendlyError && <Alert severity="error">{friendlyError}</Alert>}
        {!readonly && (
          <Button type="submit" variant="contained" disabled={pending}>
            Salvar versao
          </Button>
        )}
      </Stack>
    </Paper>
  );
}
