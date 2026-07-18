import { ApartmentOutlined, MenuBookOutlined, PersonSearchOutlined } from "@mui/icons-material";
import { Box, Paper, Stack, Tab, Tabs, Typography } from "@mui/material";
import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { CompartimentosTree } from "./CompartimentosTree";
import { CursosTree } from "./CursosTree";
import { DisciplinasTree } from "./DisciplinasTree";
import { PessoasTree } from "./PessoasTree";
export function CatalogPage() {
  const { hasAnyRole } = useAuth();
  const [tab, setTab] = useState(0);
  const managerial = hasAnyRole("ADMIN", "OPERADOR");

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Navegador de dados</Typography>
        <Typography color="text.secondary">
          Explore entidades e relacoes diretas mantendo o contexto do registro selecionado.
        </Typography>
      </Box>
      <Paper>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable">
          <Tab icon={<ApartmentOutlined />} iconPosition="start" label="Compartimentos" />
          <Tab icon={<MenuBookOutlined />} iconPosition="start" label="Disciplinas" />
          <Tab icon={<MenuBookOutlined />} iconPosition="start" label="Cursos" />
          {managerial && (
            <Tab icon={<PersonSearchOutlined />} iconPosition="start" label="Pessoas" />
          )}
        </Tabs>
      </Paper>
      {tab === 0 && <CompartimentosTree />}
      {tab === 1 && <DisciplinasTree />}
      {tab === 2 && <CursosTree />}
      {tab === 3 && managerial && <PessoasTree />}
    </Stack>
  );
}