import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";
import type { Role } from "../types";
import { AccessDeniedPage } from "../pages/AccessDeniedPage";

export function ProtectedRoute({ allowedRoles }: { allowedRoles?: Role[] }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  if (allowedRoles && !allowedRoles.some((role) => session.roles.includes(role))) {
    return <AccessDeniedPage />;
  }
  return <Outlet />;
}
