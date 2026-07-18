import {
  AddOutlined,
} from "@mui/icons-material";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../../auth/AuthContext";
import { createCurso, listCursos } from "../../api/people";
import { ErrorAlert } from "./CatalogShared";

export function CursosTree() {
  const { session, hasAnyRole } = useAuth();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState("");
  const [courseDialogOpen, setCourseDialogOpen] = useState(false);
  const [newCourse, setNewCourse] = useState({ nome: "", unidadeSigla: "" });
  const courses = useQuery({
    queryKey: ["courses", query],
    queryFn: () => listCursos(query, session),
  });
  const createCourse = useMutation({
    mutationFn: () => createCurso(newCourse, session),
    onSuccess: async () => {
      setCourseDialogOpen(false);
      setNewCourse({ nome: "", unidadeSigla: "" });
      await queryClient.invalidateQueries({ queryKey: ["courses"] });
    },
  });

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
        <TextField
          label="Buscar curso"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="ID, nome ou unidade"
          fullWidth
        />
        {hasAnyRole("ADMIN", "OPERADOR") && (
          <Button
            variant="contained"
            startIcon={<AddOutlined />}
            onClick={() => setCourseDialogOpen(true)}
          >
            Cadastrar curso
          </Button>
        )}
      </Stack>
      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Curso</TableCell>
              <TableCell>Unidade</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {courses.data?.map((course) => (
              <TableRow key={course.id}>
                <TableCell>{course.id}</TableCell>
                <TableCell>{course.nome}</TableCell>
                <TableCell>{course.unidadeSigla ?? "-"}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <ErrorAlert error={courses.error} />
      <Dialog
        open={courseDialogOpen}
        onClose={() => setCourseDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Cadastrar curso</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Nome"
              value={newCourse.nome}
              onChange={(event) => setNewCourse({ ...newCourse, nome: event.target.value })}
              required
              autoFocus
            />
            <TextField
              label="Sigla da unidade"
              value={newCourse.unidadeSigla}
              onChange={(event) =>
                setNewCourse({ ...newCourse, unidadeSigla: event.target.value })
              }
              placeholder="Ex.: CDTec"
            />
            <ErrorAlert error={createCourse.error} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCourseDialogOpen(false)}>Cancelar</Button>
          <Button
            variant="contained"
            onClick={() => createCourse.mutate()}
            disabled={createCourse.isPending || !newCourse.nome.trim()}
          >
            Cadastrar
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
