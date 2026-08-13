import { EditOutlined, PowerSettingsNewOutlined } from "@mui/icons-material";
import {
  Chip,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import type { ProfileResponse } from "../../types/sensorIntegrations";
import { activeLabel } from "./status";

interface ProfilesTableProps {
  profiles: ProfileResponse[];
  onToggleActive: (profile: ProfileResponse) => void;
}

export function ProfilesTable({ profiles, onToggleActive }: ProfilesTableProps) {
  return (
    <TableContainer component={Paper} variant="outlined">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Nome</TableCell>
            <TableCell>Source</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Criado em</TableCell>
            <TableCell>Atualizado em</TableCell>
            <TableCell align="right">Acoes</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {profiles.map((profile) => (
            <TableRow key={profile.id} hover>
              <TableCell>
                {profile.nome}
              </TableCell>
              <TableCell>{profile.source}</TableCell>
              <TableCell>
                <Chip
                  label={activeLabel(profile.ativo)}
                  color={profile.ativo ? "success" : "default"}
                  size="small"
                />
              </TableCell>
              <TableCell>{new Date(profile.createdAt).toLocaleString()}</TableCell>
              <TableCell>{new Date(profile.updatedAt).toLocaleString()}</TableCell>
              <TableCell align="right">
                <Tooltip title="Abrir detalhes">
                  <IconButton
                    component={RouterLink}
                    to={`/integracoes/perfis/${profile.id}`}
                    aria-label={`Abrir perfil ${profile.nome}`}
                  >
                    <EditOutlined />
                  </IconButton>
                </Tooltip>
                <Tooltip title={profile.ativo ? "Desativar perfil" : "Ativar perfil"}>
                  <IconButton
                    onClick={() => onToggleActive(profile)}
                    aria-label={profile.ativo ? "Desativar perfil" : "Ativar perfil"}
                  >
                    <PowerSettingsNewOutlined />
                  </IconButton>
                </Tooltip>
              </TableCell>
            </TableRow>
          ))}
          {profiles.length === 0 && (
            <TableRow>
              <TableCell colSpan={6} align="center">
                Nenhum perfil encontrado.
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
