import { useState, useEffect } from 'react';
import { Box, IconButton, useMediaQuery, useTheme, Drawer } from '@mui/material';
import { Outlet, useNavigate, useParams } from 'react-router-dom';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import MenuOpenIcon from '@mui/icons-material/MenuOpen';
import DirectMessageList from '../friends/DirectMessageList';
import UserProfile from '../user/UserProfile';
import { UserStatus } from '../../types/status';
import { User } from '../../types/auth';
import authService from '../../services/auth.service';
import { useStatus } from '../../hooks/useStatus';

export default function HomeView() {
  const navigate = useNavigate();
  const { friendId } = useParams();
  const theme = useTheme();
  const { friendStatuses } = useStatus();

  // md and below (< 900px) → sidebar collapses into a Drawer
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [sidebarOpen, setSidebarOpen] = useState(false);

 

  // Auto-close sidebar drawer when a DM is selected on mobile
  useEffect(() => {
    if (isMobile && friendId) setSidebarOpen(false);
  }, [friendId, isMobile]);

  const getUserStatus = (id: number) =>
    friendStatuses[id] || UserStatus.OFFLINE;

  const Sidebar = (
    <Box sx={{
      width: { xs: 280, md: 250 },
      flexShrink: 0,
      bgcolor: '#2b2d31',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      borderRight: '1px solid #1e1f22',
    }}>
      {/* Friends header */}
      <Box
        onClick={() => { navigate('/channels/@me'); setSidebarOpen(false); }}
        sx={{
          height: 48,
          flexShrink: 0,
          px: 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          fontWeight: 600,
          fontSize: '15px',
          color: '#f2f3f5',
          borderBottom: '1px solid #1e1f22',
          cursor: 'pointer',
          transition: 'background .15s',
          '&:hover': { bgcolor: '#35373c' },
        }}
      >
        <PeopleAltIcon sx={{ fontSize: 18, color: '#b5bac1' }} />
        Friends
      </Box>

      {/* DM list — scrollable, fills remaining height */}
      <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        <DirectMessageList onAddDM={() => console.log('Add DM')} />
      </Box>

     
    </Box>
  );

  return (
    // flex: 1 + minWidth: 0 fills the Outlet slot inside MainLayout
    <Box sx={{ flex: 1, minWidth: 0, display: 'flex', overflow: 'hidden' }}>

      {/* Part 2 — DM sidebar: inline on md+, Drawer on xs/sm */}
      {isMobile ? (
        <Drawer
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          PaperProps={{ sx: { bgcolor: '#2b2d31', border: 'none' } }}
        >
          {Sidebar}
        </Drawer>
      ) : (
        Sidebar
      )}

      {/* Part 3 — FriendsList or DirectMessage via Outlet */}
      <Box sx={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        bgcolor: '#313338',
        overflow: 'hidden',
      }}>

        {/* Mobile top bar with sidebar toggle */}
        {isMobile && (
          <Box sx={{
            height: 48,
            flexShrink: 0,
            bgcolor: '#313338',
            borderBottom: '1px solid #1e1f22',
            display: 'flex',
            alignItems: 'center',
            px: 1.5,
          }}>
            <IconButton
              size="small"
              onClick={() => setSidebarOpen(true)}
              sx={{ color: '#b5bac1' }}
            >
              <MenuOpenIcon fontSize="small" />
            </IconButton>
          </Box>
        )}

        {/* Route outlet — FriendsList or DirectMessage */}
        <Box sx={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}>
          <Outlet />
        </Box>

      </Box>
    </Box>
  );
}