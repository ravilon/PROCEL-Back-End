import {
  AccountCircleOutlined,
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

const drawerWidth = 240;

const navigation = [
  { label: "Visao geral", path: "/", icon: <DashboardOutlined /> },
  { label: "Minhas disciplinas", path: "/disciplinas", icon: <MenuBookOutlined /> },
];

export function AppLayout() {
  const { session, logout } = useAuth();
  const location = useLocation();

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "grey.100" }}>
      <AppBar
        position="fixed"
        sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}
      >
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            PROCEL Console
          </Typography>
          <Chip
            icon={<AccountCircleOutlined />}
            label={session?.email}
            color="default"
            size="small"
            sx={{ mr: 2 }}
          />
          <Button color="inherit" startIcon={<LogoutOutlined />} onClick={logout}>
            Sair
          </Button>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: {
            width: drawerWidth,
            boxSizing: "border-box",
          },
        }}
      >
        <Toolbar />
        <Box sx={{ p: 2 }}>
          <Typography variant="overline" color="text.secondary">
            Acesso
          </Typography>
          <Typography variant="body2">{session?.userId}</Typography>
          <Typography variant="caption" color="text.secondary">
            {session?.roles.join(", ")}
          </Typography>
        </Box>
        <Divider />
        <List>
          {navigation.map((item) => (
            <ListItemButton
              key={item.path}
              component={NavLink}
              to={item.path}
              selected={location.pathname === item.path}
            >
              <ListItemIcon>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
