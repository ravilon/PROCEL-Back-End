import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { AppLayout } from "./components/AppLayout";
import { ApiConsolePage } from "./pages/ApiConsolePage";
import { CatalogPage } from "./pages/CatalogPage";
import { DashboardPage } from "./pages/DashboardPage";
import { DisciplinasPage } from "./pages/DisciplinasPage";
import { IntegrationsAdminPage } from "./features/sensor-integrations/IntegrationsAdminPage";
import { LoginPage } from "./pages/LoginPage";
import { SensorsAdminPage } from "./pages/SensorsAdminPage";
import { MissionsAdminPage } from "./pages/MissionsAdminPage";
import { ProfileDetailsPage } from "./features/sensor-integrations/ProfileDetailsPage";
import { SnapshotPage } from "./features/sensor-integrations/SnapshotPage";
import { SyncAdminPage } from "./pages/SyncAdminPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<DashboardPage />} />
            <Route path="catalogo" element={<CatalogPage />} />
            <Route path="operacoes" element={<ApiConsolePage />} />
            <Route path="disciplinas" element={<DisciplinasPage />} />
            <Route path="sensores" element={<SensorsAdminPage />} />
            <Route path="missoes" element={<MissionsAdminPage />} />
            <Route path="sincronizacoes" element={<SyncAdminPage />} />
          </Route>
        </Route>
        <Route element={<ProtectedRoute allowedRoles={["ADMIN"]} />}>
          <Route element={<AppLayout />}>
            <Route path="integracoes" element={<IntegrationsAdminPage />} />
            <Route path="integracoes/perfis/:profileId" element={<ProfileDetailsPage />} />
            <Route path="integracoes/snapshot" element={<SnapshotPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
