import { Alert, Box, Button, Paper, TextField, Typography } from "@mui/material";
import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (session) {
    return <Navigate to="/" replace />;
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login(email, password);
      const target =
        (location.state as { from?: { pathname?: string } } | null)?.from
          ?.pathname ?? "/";
      navigate(target, { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Falha no login");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        bgcolor: "grey.100",
        p: 2,
      }}
    >
      <Paper component="form" onSubmit={submit} sx={{ p: 4, width: 380 }}>
        <Typography variant="h4" gutterBottom>
          PROCEL
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          Console de gerenciamento
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField
          label="E-mail"
          type="email"
          fullWidth
          required
          margin="normal"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <TextField
          label="Senha"
          type="password"
          fullWidth
          required
          margin="normal"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Button
          type="submit"
          variant="contained"
          fullWidth
          size="large"
          disabled={submitting}
          sx={{ mt: 3 }}
        >
          {submitting ? "Entrando..." : "Entrar"}
        </Button>
      </Paper>
    </Box>
  );
}
