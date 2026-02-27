'use client';

import * as React from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import List from '@mui/material/List';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import MenuIcon from '@mui/icons-material/Menu';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import PhoneIcon from '@mui/icons-material/Phone';
import GroupIcon from '@mui/icons-material/Group';
import PhoneInTalkIcon from '@mui/icons-material/PhoneInTalk';
import HubIcon from '@mui/icons-material/Hub';
import EditNoteIcon from '@mui/icons-material/EditNote';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import SettingsIcon from '@mui/icons-material/Settings';
import { useThemeContext } from '../../context/ThemeContext';

const DRAWER_WIDTH = 240;
const COLLAPSED_DRAWER_WIDTH = 64;

interface NavItem {
  segment: string;
  title: string;
  icon: React.ReactNode;
}

const NAVIGATION: NavItem[] = [
  { segment: 'agents', title: 'Agents', icon: <GroupIcon sx={{ fontSize: 24 }} /> },
  { segment: 'calls', title: 'Calls', icon: <PhoneInTalkIcon sx={{ fontSize: 24 }} /> },
  { segment: 'ivr', title: 'IVR Sessions', icon: <SmartToyIcon sx={{ fontSize: 24 }} /> },
  { segment: 'topology', title: 'Topology', icon: <HubIcon sx={{ fontSize: 24 }} /> },
  { segment: 'editor', title: 'Editor', icon: <EditNoteIcon sx={{ fontSize: 24 }} /> },
  { segment: 'settings', title: 'Settings', icon: <SettingsIcon sx={{ fontSize: 24 }} /> },
];

const PAGE_TITLES: Record<string, string> = {
  '/agents': 'Agents',
  '/calls': 'Calls',
  '/ivr': 'IVR Sessions',
  '/topology': 'Topology',
  '/editor': 'Infrastructure Editor',
  '/settings': 'Settings',
};

interface DashboardLayoutProps {
  children: React.ReactNode;
  toolbarActions?: React.ReactNode;
}

export function DashboardLayout({ children, toolbarActions }: DashboardLayoutProps) {
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const pathname = usePathname();
  const { mode, toggleTheme } = useThemeContext();

  const pageTitle = PAGE_TITLES[pathname] || 'OSCC Admin';

  const handleDrawerToggle = () => {
    setDrawerOpen(!drawerOpen);
  };

  const drawerWidth = drawerOpen ? DRAWER_WIDTH : COLLAPSED_DRAWER_WIDTH;

  const drawer = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Drawer Header */}
      <Toolbar
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: drawerOpen ? 'space-between' : 'center',
          px: drawerOpen ? 2 : 1,
        }}
      >
        {drawerOpen && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <PhoneIcon sx={{ color: 'text.primary' }} />
            <Typography variant="h6" noWrap sx={{ color: 'text.primary' }}>
              OSCC Admin
            </Typography>
          </Box>
        )}
        <IconButton onClick={handleDrawerToggle} size="small">
          {drawerOpen ? <ChevronLeftIcon /> : <MenuIcon />}
        </IconButton>
      </Toolbar>

      {/* Navigation List */}
      <List sx={{ flex: 1, p: 0 }}>
        {NAVIGATION.map((item) => {
          const href = `/${item.segment}`;
          const isActive = pathname === href;

          return (
            <ListItem key={item.segment} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link}
                href={href}
                selected={isActive}
                sx={{
                  minHeight: 56,
                  flexDirection: drawerOpen ? 'row' : 'column',
                  justifyContent: drawerOpen ? 'flex-start' : 'center',
                  alignItems: 'center',
                  py: 1,
                  px: drawerOpen ? 2 : 1,
                  borderRadius: '0 !important',
                  gap: drawerOpen ? 2 : 0,
                  borderLeft: isActive ? 3 : 0,
                  borderLeftStyle: 'solid',
                  borderLeftColor: 'text.primary',
                  '&.Mui-selected': {
                    backgroundColor: 'transparent',
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    minWidth: 24,
                    width: 24,
                    height: 24,
                    m: 0,
                    justifyContent: 'center',
                    color: isActive ? 'primary.main' : 'inherit',
                    '& .MuiSvgIcon-root': {
                      width: 24,
                      height: 24,
                    },
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <Typography
                  variant={drawerOpen ? 'body1' : 'caption'}
                  sx={{
                    fontWeight: isActive ? 600 : 400,
                    color: isActive ? 'primary.main' : 'text.secondary',
                    textAlign: drawerOpen ? 'left' : 'center',
                    fontSize: drawerOpen ? undefined : '0.65rem',
                    m: 0,
                    lineHeight: 1,
                    maxWidth: drawerOpen ? undefined : 56,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {item.title}
                </Typography>
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* App Bar */}
      <AppBar
        position="fixed"
        sx={{
          width: `calc(100% - ${drawerWidth}px)`,
          ml: `${drawerWidth}px`,
          transition: (theme) =>
            theme.transitions.create(['width', 'margin'], {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.leavingScreen,
            }),
          bgcolor: 'background.paper',
          borderLeft: 'none',
        }}
        color="default"
        elevation={1}
      >
        <Toolbar>
          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            {pageTitle}
          </Typography>

          {/* Custom toolbar actions */}
          {toolbarActions}

          {/* Theme Toggle */}
          <IconButton onClick={toggleTheme} color="inherit" sx={{ ml: 1 }}>
            {mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon />}
          </IconButton>
        </Toolbar>
      </AppBar>

      {/* Sidebar Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            bgcolor: 'background.paper',
            transition: (theme) =>
              theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.leavingScreen,
              }),
            overflowX: 'hidden',
          },
        }}
      >
        {drawer}
      </Drawer>

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: `calc(100% - ${drawerWidth}px)`,
          minHeight: '100vh',
          pt: '64px', // AppBar height
          transition: (theme) =>
            theme.transitions.create(['width', 'margin'], {
              easing: theme.transitions.easing.sharp,
              duration: theme.transitions.duration.leavingScreen,
            }),
        }}
      >
        {children}
      </Box>
    </Box>
  );
}
