import {
  AccountCircleOutlined,
  AccountTreeOutlined,
  ApiOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MenuBookOutlined,
} from "@mui/icons-material";
import {
  AppBar,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
} from "@mui/material";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { Role } from "../types";

const drawerWidth = 260;

interface NavigationItem {
  label: string;
  path: string;
  icon: React.ReactNode;
  roles?: Role[];
}

const navigation: NavigationItem[] = [
  { label: "Visao geral", path: "/", icon: <DashboardOutlined /> },
  { label: "Navegador de dados", path: "/catalogo", icon: <AccountTreeOutlined /> },
  { label: "Operacoes da API", path: "/operacoes", icon: <ApiOutlined />, roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"] },
  { label: "Minhas disciplinas", path: "/disciplinas", icon: <MenuBookOutlined />, roles: ["USUARIO", "ADMIN", "OPERADOR", "ANALISTA"] },
];

export function AppLayout() {
  const { session, logout } = useAuth();
  const location = useLocation();
  const visibleNavigation = navigation.filter(
    (item) => !item.roles || item.roles.some((role) => session?.roles.includes(role)),
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "grey.100" }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>PROCEL Console</Typography>
          <Chip icon={<AccountCircleOutlined />} label={session?.email} size="small" sx={{ mr: 2 }} />
          <Button color="inherit" startIcon={<LogoutOutlined />} onClick={logout}>Sair</Button>
        </Toolbar>
      </AppBar>
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: "border-box" },
        }}
      >
        <Toolbar />
        <Box sx={{ p: 2 }}>
          <Typography variant="overline" color="text.secondary">Acesso</Typography>
          <Typography variant="body2">{session?.userId}</Typography>
          <Typography variant="caption" color="text.secondary">{session?.roles.join(", ")}</Typography>
        </Box>
        <Divider />
        <List>
          {visibleNavigation.map((item) => (
            <ListItemButton
              key={item.path}
              component={NavLink}
              to={item.path}
              selected={item.path === "/" ? location.pathname === "/" : location.pathname.startsWith(item.path)}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
