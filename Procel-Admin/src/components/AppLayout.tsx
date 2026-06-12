import {
  AccountCircleOutlined,
  AccountTreeOutlined,
  ApiOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MenuBookOutlined,
  MenuOutlined,
  OpenInNewOutlined,
  SensorsOutlined,
  SyncOutlined,
  TaskAltOutlined,
} from "@mui/icons-material";
import {
  AppBar,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  IconButton,
  Link,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
} from "@mui/material";
import { useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiBaseUrl } from "../lib/api";
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
  {
    label: "Sensores e regras",
    path: "/sensores",
    icon: <SensorsOutlined />,
    roles: ["ADMIN", "OPERADOR"],
  },
  {
    label: "Missoes",
    path: "/missoes",
    icon: <TaskAltOutlined />,
    roles: ["ADMIN", "OPERADOR"],
  },
  {
    label: "Sincronizacoes",
    path: "/sincronizacoes",
    icon: <SyncOutlined />,
    roles: ["ADMIN", "OPERADOR"],
  },
  {
    label: "Operacoes da API",
    path: "/operacoes",
    icon: <ApiOutlined />,
    roles: ["ADMIN", "OPERADOR", "ANALISTA", "USUARIO", "INGESTOR"],
  },
  {
    label: "Minhas disciplinas",
    path: "/disciplinas",
    icon: <MenuBookOutlined />,
    roles: ["USUARIO", "ADMIN", "OPERADOR", "ANALISTA"],
  },
];

export function AppLayout() {
  const { session, logout } = useAuth();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const visibleNavigation = navigation.filter(
    (item) => !item.roles || item.roles.some((role) => session?.roles.includes(role)),
  );

  const drawerContent = (
    <>
      <Toolbar />
      <Box sx={{ p: 2 }}>
        <Typography variant="overline" color="text.secondary">Acesso</Typography>
        <Typography variant="body2" sx={{ overflowWrap: "anywhere" }}>
          {session?.userId}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {session?.roles.join(", ")}
        </Typography>
      </Box>
      <Divider />
      <List>
        {visibleNavigation.map((item) => (
          <ListItemButton
            key={item.path}
            component={NavLink}
            to={item.path}
            selected={
              item.path === "/"
                ? location.pathname === "/"
                : location.pathname.startsWith(item.path)
            }
            onClick={() => setMobileOpen(false)}
          >
            <ListItemIcon>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
        <ListItemButton
          component="a"
          href={`${apiBaseUrl}/swagger-ui/index.html`}
          target="_blank"
          rel="noreferrer"
        >
          <ListItemIcon><OpenInNewOutlined /></ListItemIcon>
          <ListItemText primary="Documentacao Swagger" />
        </ListItemButton>
      </List>
    </>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "grey.100" }}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ px: { xs: 1, sm: 2 } }}>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setMobileOpen(true)}
            sx={{ mr: 1, display: { md: "none" } }}
            aria-label="Abrir menu"
          >
            <MenuOutlined />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            PROCEL Console
          </Typography>
          <Chip
            icon={<AccountCircleOutlined />}
            label={session?.email}
            size="small"
            variant="outlined"
            sx={{
              mr: 2,
              display: { xs: "none", sm: "inline-flex" },
              color: "common.white",
              borderColor: "rgba(255,255,255,0.7)",
              "& .MuiChip-icon": { color: "common.white" },
            }}
          />
          <Button
            color="inherit"
            startIcon={<LogoutOutlined />}
            onClick={logout}
            sx={{
              minWidth: { xs: 40, sm: 64 },
              "& .MuiButton-startIcon": { mr: { xs: 0, sm: 1 } },
            }}
          >
            <Box component="span" sx={{ display: { xs: "none", sm: "inline" } }}>
              Sair
            </Box>
          </Button>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          display: { xs: "none", md: "block" },
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: "border-box" },
        }}
      >
        {drawerContent}
      </Drawer>
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={() => setMobileOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: "block", md: "none" },
          [`& .MuiDrawer-paper`]: { width: drawerWidth, boxSizing: "border-box" },
        }}
      >
        {drawerContent}
      </Drawer>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 1.5, sm: 2, md: 3 },
          minWidth: 0,
          width: { xs: "100%", md: `calc(100% - ${drawerWidth}px)` },
          display: "flex",
          flexDirection: "column",
        }}
      >
        <Toolbar />
        <Box sx={{ flexGrow: 1 }}>
          <Outlet />
        </Box>
        <Box
          component="footer"
          sx={{ mt: 4, pt: 2, borderTop: 1, borderColor: "divider", textAlign: "center" }}
        >
          <Typography variant="caption" color="text.secondary">
            PROCEL Admin v0.1 | Desenvolvida para o PROCEL | Contato: Ravilon Aguiar,
            2026 |{" "}
            <Link href="mailto:ravilonaguiardossantos@gmail.com">
              ravilonaguiardossantos@gmail.com
            </Link>
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}
