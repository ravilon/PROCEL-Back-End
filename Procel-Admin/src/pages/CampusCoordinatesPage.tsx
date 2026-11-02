import { Alert, Button, Card, CardContent, Chip, Stack, TextField, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import { listCampuses, updateCampus, type Campus } from "../api/sharedContexts";
import { useAuth } from "../auth/AuthContext";

export function CampusCoordinatesPage() {
  const { session } = useAuth();
  const [campuses, setCampuses] = useState<Campus[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState("");
  const reload = async () => setCampuses(await listCampuses(session));
  useEffect(() => { void listCampuses(session).then(setCampuses).catch((caught) => setError(caught instanceof Error ? caught.message : "Falha ao carregar campi")); }, [session]);
  const visible = useMemo(() => campuses.filter((campus) => campus.nome.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())), [campuses, query]);
  return <Stack spacing={2}>
    <div><Typography variant="h4">Coordenadas dos campi</Typography><Typography color="text.secondary">Referências geográficas usadas dinamicamente pelos contextos compartilhados.</Typography></div>
    {error && <Alert severity="error" onClose={() => setError("")}>{error}</Alert>}
    <TextField label="Pesquisar campus" value={query} onChange={(event) => setQuery(event.target.value)} />
    {visible.map((campus) => <CampusCoordinateCard key={campus.nome} campus={campus} onSaved={reload} onError={setError} />)}
  </Stack>;
}

function CampusCoordinateCard({ campus, onSaved, onError }: { campus: Campus; onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const { session } = useAuth();
  const [latitude, setLatitude] = useState(campus.latitude?.toString() ?? "");
  const [longitude, setLongitude] = useState(campus.longitude?.toString() ?? "");
  const [saving, setSaving] = useState(false);
  const partial = Boolean(latitude) !== Boolean(longitude);
  const lat = latitude === "" ? null : Number(latitude);
  const lon = longitude === "" ? null : Number(longitude);
  const invalid = partial || (lat != null && (!Number.isFinite(lat) || lat < -90 || lat > 90)) || (lon != null && (!Number.isFinite(lon) || lon < -180 || lon > 180));
  const save = async () => { setSaving(true); try { await updateCampus(campus.nome, { latitude: lat, longitude: lon }, session); await onSaved(); } catch (caught) { onError(caught instanceof Error ? caught.message : "Falha ao salvar coordenadas"); } finally { setSaving(false); } };
  return <Card><CardContent><Stack spacing={2}>
    <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" gap={1}><Typography variant="h6">{campus.nome}</Typography><Chip color={campus.latitude == null ? "warning" : "success"} label={campus.latitude == null ? "Sem coordenadas" : "Coordenadas configuradas"} /></Stack>
    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}><TextField type="number" label="Latitude" value={latitude} onChange={(event) => setLatitude(event.target.value)} inputProps={{ min: -90, max: 90, step: "any" }} fullWidth /><TextField type="number" label="Longitude" value={longitude} onChange={(event) => setLongitude(event.target.value)} inputProps={{ min: -180, max: 180, step: "any" }} fullWidth /></Stack>
    {partial && <Alert severity="warning">Informe latitude e longitude em conjunto.</Alert>}
    <div><Typography variant="body2" color="text.secondary">Contextos que usam este campus</Typography><Stack direction="row" useFlexGap flexWrap="wrap" gap={1} sx={{ mt: 1 }}>{campus.contextoNomes.length ? campus.contextoNomes.map((name) => <Chip key={name} label={name} size="small" />) : <Typography variant="body2">Nenhum contexto.</Typography>}</Stack></div>
    <Button variant="contained" disabled={invalid || saving} onClick={() => void save()} sx={{ alignSelf: "flex-start" }}>{saving ? "Salvando…" : "Salvar coordenadas"}</Button>
  </Stack></CardContent></Card>;
}
