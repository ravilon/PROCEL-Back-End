import {
  ApartmentOutlined,
  LocationOnOutlined,
  MenuBookOutlined,
  PersonSearchOutlined,
} from "@mui/icons-material";
import { Box, Paper, Stack, Tab, Tabs, Typography } from "@mui/material";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { CampusCoordinatesPage } from "../../pages/CampusCoordinatesPage";
import { CompartimentosTree } from "./CompartimentosTree";
import { RoomRegistrationPage } from "./RoomRegistrationPage";
import { CursosTree } from "./CursosTree";
import { DisciplinasTree } from "./DisciplinasTree";
import { PessoasTree } from "./PessoasTree";
export function CatalogPage() {
  const { hasAnyRole } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const managerial = hasAnyRole("ADMIN", "OPERADOR");
  const canEditCampuses = hasAnyRole("ADMIN");
  const requestedSection = searchParams.get("secao");
  const allowedSections = [
    "compartimentos",
    ...(managerial ? ["cadastro-salas"] : []),
    "disciplinas",
    "cursos",
    ...(managerial ? ["pessoas"] : []),
    ...(canEditCampuses ? ["campi"] : []),
  ];
  const section = allowedSections.includes(requestedSection ?? "")
    ? requestedSection!
    : "compartimentos";

  const selectSection = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value === "compartimentos") next.delete("secao");
    else next.set("secao", value);
    setSearchParams(next, { replace: true });
  };

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Navegador de dados</Typography>
        <Typography color="text.secondary">
          Explore entidades e relacoes diretas mantendo o contexto do registro
          selecionado.
        </Typography>
      </Box>
      <Paper>
        <Tabs
          value={section}
          onChange={(_, value) => selectSection(value)}
          variant="scrollable"
        >
          <Tab
            value="compartimentos"
            icon={<ApartmentOutlined />}
            iconPosition="start"
            label="Compartimentos"
          />
          {managerial && (
            <Tab
              value="cadastro-salas"
              icon={<ApartmentOutlined />}
              iconPosition="start"
              label="Cadastrar sala"
            />
          )}
          <Tab
            value="disciplinas"
            icon={<MenuBookOutlined />}
            iconPosition="start"
            label="Disciplinas"
          />
          <Tab
            value="cursos"
            icon={<MenuBookOutlined />}
            iconPosition="start"
            label="Cursos"
          />
          {managerial && (
            <Tab
              value="pessoas"
              icon={<PersonSearchOutlined />}
              iconPosition="start"
              label="Pessoas"
            />
          )}
          {canEditCampuses && (
            <Tab
              value="campi"
              icon={<LocationOnOutlined />}
              iconPosition="start"
              label="Campi"
            />
          )}
        </Tabs>
      </Paper>
      {section === "compartimentos" && <CompartimentosTree />}
      {section === "cadastro-salas" && managerial && <RoomRegistrationPage />}
      {section === "disciplinas" && <DisciplinasTree />}
      {section === "cursos" && <CursosTree />}
      {section === "pessoas" && managerial && <PessoasTree />}
      {section === "campi" && canEditCampuses && (
        <CampusCoordinatesPage embedded />
      )}
    </Stack>
  );
}
