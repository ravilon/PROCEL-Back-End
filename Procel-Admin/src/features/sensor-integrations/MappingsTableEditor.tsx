import { AddOutlined, DeleteOutlined } from "@mui/icons-material";
import {
  Button,
  Checkbox,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
} from "@mui/material";
import type { ValueMappingRequest } from "../../types/sensorIntegrations";
import { MAX_MAPPINGS, pointerError } from "./validation";

interface MappingsTableEditorProps {
  mappings: ValueMappingRequest[];
  readonly: boolean;
  onChange: (mappings: ValueMappingRequest[]) => void;
}

export function MappingsTableEditor({ mappings, readonly, onChange }: MappingsTableEditorProps) {
  const names = mappings.map((mapping) => mapping.parameterName.trim()).filter(Boolean);

  const update = (index: number, mapping: ValueMappingRequest) => {
    onChange(mappings.map((item, itemIndex) => (itemIndex === index ? mapping : item)));
  };

  return (
    <>
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Parametro</TableCell>
              <TableCell>Value pointer</TableCell>
              <TableCell>Obrigatorio</TableCell>
              {!readonly && <TableCell align="right">Acoes</TableCell>}
            </TableRow>
          </TableHead>
          <TableBody>
            {mappings.map((mapping, index) => {
              const duplicate =
                Boolean(mapping.parameterName.trim())
                && names.filter((name) => name === mapping.parameterName.trim()).length > 1;
              return (
                <TableRow key={index}>
                  <TableCell>
                    <TextField
                      value={mapping.parameterName}
                      onChange={(event) => update(index, { ...mapping, parameterName: event.target.value })}
                      required
                      fullWidth
                      size="small"
                      disabled={readonly}
                      error={duplicate || !mapping.parameterName.trim()}
                      helperText={duplicate ? "Duplicado" : ""}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      value={mapping.valuePointer}
                      onChange={(event) => update(index, { ...mapping, valuePointer: event.target.value })}
                      required
                      fullWidth
                      size="small"
                      disabled={readonly}
                      error={Boolean(pointerError(mapping.valuePointer, true))}
                      helperText={pointerError(mapping.valuePointer, true)}
                    />
                  </TableCell>
                  <TableCell>
                    <Checkbox
                      checked={mapping.required}
                      onChange={(event) => update(index, { ...mapping, required: event.target.checked })}
                      disabled={readonly}
                      inputProps={{ "aria-label": "Mapping obrigatorio" }}
                    />
                  </TableCell>
                  {!readonly && (
                    <TableCell align="right">
                      <Tooltip title="Remover mapping">
                        <span>
                          <IconButton
                            onClick={() => onChange(mappings.filter((_, itemIndex) => itemIndex !== index))}
                            disabled={mappings.length === 1}
                            aria-label="Remover mapping"
                          >
                            <DeleteOutlined />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      {!readonly && (
        <Button
          startIcon={<AddOutlined />}
          onClick={() => onChange([...mappings, { parameterName: "", valuePointer: "", required: true }])}
          disabled={mappings.length >= MAX_MAPPINGS}
        >
          Adicionar mapping
        </Button>
      )}
    </>
  );
}
