import { ClearOutlined, FilterAltOutlined } from "@mui/icons-material";
import {
  Autocomplete,
  Box,
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
import type { NumericBucketFilters } from "../../types/analytics";
import type { Compartimento, ParametroDef, Sensor } from "../../types";
import { PAGE_SIZE_OPTIONS, validatePeriod } from "./analyticsUtils";

export interface AnalyticsFilterState extends NumericBucketFilters {
  aggregationVersionText?: string;
}

interface AnalyticsFiltersProps {
  value: AnalyticsFilterState;
  sensors: Sensor[];
  parametros: ParametroDef[];
  compartimentos: Compartimento[];
  loadingCatalogs: boolean;
  onChange: (value: AnalyticsFilterState) => void;
  onApply: () => void;
  onClearOptional: () => void;
}

export function AnalyticsFilters({
  value,
  sensors,
  parametros,
  compartimentos,
  loadingCatalogs,
  onChange,
  onApply,
  onClearOptional,
}: AnalyticsFiltersProps) {
  const periodError = validatePeriod(value.from, value.to);
  const selectedSensor =
    sensors.find((sensor) => sensor.externalId === value.sensorExternalId) ?? null;
  const selectedParametro =
    parametros.find((parametro) => parametro.id === value.parametroDefId) ?? null;
  const selectedCompartimento =
    compartimentos.find((compartimento) => compartimento.id === value.compartimentoId) ?? null;

  return (
    <Paper
      component="form"
      variant="outlined"
      sx={{ p: { xs: 2, md: 3 } }}
      onSubmit={(event) => {
        event.preventDefault();
        onApply();
      }}
    >
      <Stack spacing={2.5}>
        <Box>
          <Typography variant="h6">Filtros</Typography>
          <Typography variant="body2" color="text.secondary">
            Consultas usam apenas buckets numericos persistidos.
          </Typography>
        </Box>
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, minmax(0, 1fr))",
              lg: "repeat(4, minmax(0, 1fr))",
            },
            gap: 2,
          }}
        >
          <TextField
            label="Inicio"
            type="datetime-local"
            required
            value={value.from}
            onChange={(event) => onChange({ ...value, from: event.target.value })}
            error={Boolean(periodError)}
            helperText={periodError || "Obrigatorio"}
            slotProps={{ inputLabel: { shrink: true } }}
            inputProps={{ "aria-label": "Inicio" }}
          />
          <TextField
            label="Fim"
            type="datetime-local"
            required
            value={value.to}
            onChange={(event) => onChange({ ...value, to: event.target.value })}
            error={Boolean(periodError)}
            helperText={periodError || "Obrigatorio"}
            slotProps={{ inputLabel: { shrink: true } }}
            inputProps={{ "aria-label": "Fim" }}
          />
          <Autocomplete
            options={sensors}
            value={selectedSensor}
            loading={loadingCatalogs}
            getOptionLabel={(sensor) => `${sensor.nome} (${sensor.externalId})`}
            isOptionEqualToValue={(option, selected) => option.externalId === selected.externalId}
            onChange={(_, sensor) =>
              onChange({ ...value, sensorExternalId: sensor?.externalId })
            }
            renderInput={(params) => (
              <TextField {...params} label="Sensor" placeholder="Todos os sensores" />
            )}
          />
          <Autocomplete
            options={parametros}
            value={selectedParametro}
            loading={loadingCatalogs}
            getOptionLabel={(parametro) =>
              `${parametro.nome}${parametro.numericUnit ? ` (${parametro.numericUnit})` : ""}`
            }
            isOptionEqualToValue={(option, selected) => option.id === selected.id}
            onChange={(_, parametro) =>
              onChange({ ...value, parametroDefId: parametro?.id })
            }
            renderInput={(params) => (
              <TextField {...params} label="Parametro" placeholder="Todos os parametros" />
            )}
          />
          <Autocomplete
            options={compartimentos}
            value={selectedCompartimento}
            loading={loadingCatalogs}
            getOptionLabel={(compartimento) => `${compartimento.nome} (${compartimento.id})`}
            isOptionEqualToValue={(option, selected) => option.id === selected.id}
            onChange={(_, compartimento) =>
              onChange({ ...value, compartimentoId: compartimento?.id })
            }
            renderInput={(params) => (
              <TextField {...params} label="Compartimento" placeholder="Todos os compartimentos" />
            )}
          />
          <TextField
            label="Versao de agregacao"
            type="number"
            value={value.aggregationVersionText ?? ""}
            onChange={(event) => {
              const text = event.target.value;
              onChange({
                ...value,
                aggregationVersionText: text,
                aggregationVersion: text === "" ? undefined : Number(text),
              });
            }}
            inputProps={{ min: 1, step: 1 }}
          />
          <FormControl>
            <InputLabel id="analytics-page-size-label">Tamanho da pagina</InputLabel>
            <Select
              labelId="analytics-page-size-label"
              label="Tamanho da pagina"
              value={value.size ?? 20}
              onChange={(event) => onChange({ ...value, size: Number(event.target.value) })}
            >
              {PAGE_SIZE_OPTIONS.map((size) => (
                <MenuItem key={size} value={size}>
                  {size}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
          <Button
            type="submit"
            variant="contained"
            startIcon={<FilterAltOutlined />}
            disabled={Boolean(periodError)}
          >
            Aplicar filtros
          </Button>
          <Button
            type="button"
            variant="outlined"
            startIcon={<ClearOutlined />}
            onClick={onClearOptional}
          >
            Limpar filtros opcionais
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
