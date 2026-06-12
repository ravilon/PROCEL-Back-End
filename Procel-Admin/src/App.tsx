import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { AppLayout } from "./components/AppLayout";
import { ApiConsolePage } from "./pages/ApiConsolePage";
import { CatalogPage } from "./pages/CatalogPage";
import { DashboardPage } from "./pages/DashboardPage";
import { DisciplinasPage } from "./pages/DisciplinasPage";
import { LoginPage } from "./pages/LoginPage";
import { SensorsAdminPage } from "./pages/SensorsAdminPage";
import { MissionsAdminPage } from "./pages/MissionsAdminPage";

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
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
