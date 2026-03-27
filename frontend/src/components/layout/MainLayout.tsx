import { useState, useEffect } from 'react';
import { Box, Avatar, useMediaQuery, useTheme, Drawer, IconButton } from '@mui/material';
import { Outlet, useNavigate } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import HomeIcon from '@mui/icons-material/Home';
import { Tooltip } from "@mui/material";

import MenuIcon from '@mui/icons-material/Menu';
import CreateServerModal from '../servers/CreateServerModel';
import authService from '../../services/auth.service';
import { useServers } from '../../hooks/useServers';
import toast from 'react-hot-toast';
import UserProfile from '../user/UserProfile';
import { User } from '../../types/auth';
import { UserStatus } from '../../types/status';
import { useStatus } from '../../hooks/useStatus';
const defaultServers = [
  { id: 0, name: 'Home', owner: { id: 0, username: 'Admin', email: 'admin@example.com' } }
];

export default function MainLayout() {
  const navigate = useNavigate();
  const theme = useTheme();

  // Breakpoints
  const isXs = useMediaQuery(theme.breakpoints.down('sm'));   // < 600px  → rail hidden, hamburger shown
  const isSm = useMediaQuery(theme.breakpoints.down('md'));   // < 900px  → rail visible, sidebar in Drawer

  const [open, setOpen] = useState(false);
  const [railOpen, setRailOpen] = useState(false); // mobile Drawer for server rail
  const [error, setError] = useState<string | null>(null);
  const { servers, createServer,joinServer } = useServers();
    const [currentUser, setCurrentUser] = useState<User | null>(null);
   useEffect(() => {
      setCurrentUser(authService.getCurrentUser());
    }, []);
  const { friendStatuses } = useStatus();
  const getUserStatus = (id: number) =>
    friendStatuses[id] || UserStatus.OFFLINE;
  const handleServerClick = (serverId: number) => {
    if (serverId === 0) navigate('/channels/@me');
    else navigate(`/channels/${serverId}`);
    setRailOpen(false);
  };

  const ServerRail = (
    <Box sx={{
      width: { xs: '100%', sm: 72 },     // full-width inside Drawer on xs, fixed 72px on sm+
      minWidth: { sm: 72 },
      flexShrink: 0,
      bgcolor: '#1e1f22',
      display: 'flex',
      flexDirection: { xs: 'row', sm: 'column' },
      alignItems: 'center',
      justifyContent: { xs: 'flex-start', sm: 'flex-start' },
      py: { xs: 1, sm: 2 },
      px: { xs: 1, sm: 0 },
      gap: { xs: 1, sm: 1.5 },
      overflowX: { xs: 'auto', sm: 'visible' },
    }}>

{[...defaultServers, ...servers.data].map(server => (
  <Tooltip
    key={server.id}
    title={server.id === 0 ? "Home" : server.name}
    placement="right-start"
    arrow
  >
    <Avatar
      onClick={() => handleServerClick(server.id)}
      sx={{
        width: { xs: 40, sm: 48 },
        height: { xs: 40, sm: 48 },
        flexShrink: 0,
        bgcolor: '#36393f',
        cursor: 'pointer',
        fontSize: { xs: '11px', sm: '13px' },
        transition: 'border-radius .15s, background .15s',
        '&:hover': { bgcolor: '#5865f2', borderRadius: '16px' },
      }}
    >
      {server.id === 0
        ? <HomeIcon sx={{ fontSize: { xs: 18, sm: 22 } }} />
        : server.name.substring(0, 3)}
    </Avatar>
  </Tooltip>
))}

      <Avatar
        onClick={() => setOpen(true)}
        sx={{
          width: { xs: 40, sm: 48 },
          height: { xs: 40, sm: 48 },
          flexShrink: 0,
          bgcolor: '#36393f',
          cursor: 'pointer',
          transition: 'border-radius .15s, background .15s',
          '&:hover': { bgcolor: '#3ba55d', borderRadius: '16px' },
        }}
      >
        <AddIcon sx={{ fontSize: { xs: 18, sm: 22 } }} />
      </Avatar>

      <CreateServerModal
        open={open}
        onClose={() => setOpen(false)}
        onCreate={(data) => {
          createServer.mutate(data, {
            onSuccess: (server) => {
              setOpen(false);
              navigate(`/channels/${server.id}`);
            },
            onError: (error: any) => {
              const message =
                error?.response?.data?.fieldErrors?.name ||
                error?.response?.data?.message ||
                'Failed to create server';
              toast.error(message);
              setError(message);
            },
          });
        }}
        onJoin={(inviteCode) => { joinServer.mutate(inviteCode); }}
      />
    </Box>
  );

  return (
    <Box sx={{
      height: '100vh',
      width: '100vw',
      display: 'flex',
      flexDirection: 'column',
      bgcolor: '#313338',
      color: '#dcddde',
      overflow: 'hidden',
    }}>

      {/* ── Mobile top bar (xs only) ── */}
      {isXs && (
        <Box sx={{
          height: 48,
          flexShrink: 0,
          bgcolor: '#1e1f22',
          display: 'flex',
          alignItems: 'center',
          px: 1,
          gap: 1,
          borderBottom: '1px solid #111214',
        }}>
          <IconButton size="small" onClick={() => setRailOpen(true)} sx={{ color: '#b5bac1' }}>
            <MenuIcon fontSize="small" />
          </IconButton>
        </Box>
      )}

      {/* ── Main row ── */}
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', overflow: 'hidden' }}>

        {/* Part 1 — Server rail: inline on sm+, Drawer on xs */}
        {isXs ? (
          <Drawer
            open={railOpen}
            onClose={() => setRailOpen(false)}
            PaperProps={{ sx: { bgcolor: '#1e1f22', border: 'none' } }}
          >
            {ServerRail}
          </Drawer>
        ) : (
          ServerRail
        )}

        {/* Parts 2 + 3 + 4 — filled by Outlet (HomeView or ServerView) */}
        <Box sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          overflow: 'hidden',
        }}>
          <Outlet />
        </Box>

      </Box>
       {/* User profile pinned to bottom */}
           {currentUser && (       <Box sx={{
        width:'200px',
        position: 'fixed',
        left:'20px',
        bottom:'0',
       
      }} >
      
      <UserProfile 
          user={currentUser}
          status={getUserStatus(currentUser.id)||UserStatus.ONLINE}
          customStatus={getUserStatus(currentUser.id)||UserStatus.ONLINE}
        />
        </Box>)}
    </Box>
  );
}