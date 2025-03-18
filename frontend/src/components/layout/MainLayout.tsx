import { useState, useEffect } from 'react';
import { Box, Typography, Avatar, InputBase, IconButton } from '@mui/material';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import ChatIcon from '@mui/icons-material/Chat';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import DirectMessageList from '../friends/DirectMessageList';
import FriendsList from '../friends/FriendsList';
import UserProfile from '../user/UserProfile';
import StatusIndicator from '../user/StatusIndicator';
import { StatusUpdate, UserStatus } from '../../types/status';
import { User } from '../../types/auth';
import authService from '../../services/auth.service';
import { useStatusService } from '../../services/status.service';
// Dummy data for the layout
import { useWebSocket } from '../../services/WebSocketProvider';

const dummyServers = [
  { id: 1, name: 'Discord', initial: 'D' },
  { id: 2, name: 'Awesome Server', initial: 'AS' },
  { id: 3, name: 'Cool Group', initial: 'CG' },
];

export default function MainLayout() {
  const [currentStatus, setCurrentStatus] = useState<UserStatus>(UserStatus.ONLINE);
  const [customStatus, setCustomStatus] = useState<string | undefined>(undefined);
  const [showFriendsList, setShowFriendsList] = useState<boolean>(true);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [connected, setConnected] = useState<boolean>(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { getStatus, updateCustomStatus } = useStatusService();
  const [statuses, setStatuses] = useState<Map<number, UserStatus>>(new Map());
  const {stompClient, isConnected} = useWebSocket();
  const getUserStatus = (id: number) => statuses.get(id);

useEffect(() => {
  const fetchStatuses = async () => {
    try {
      const response = await getStatus(); // Assume getStatuses() returns a Promise<UserStatus[]>
      const statusMap = new Map<number, UserStatus>();
  
      response.forEach((st:StatusUpdate) => {
        statusMap.set(st.id,st.status);
      });
  
      setStatuses(statusMap);
    } catch (error) {
      console.error("Error fetching statuses:", error);
    }
  };
  fetchStatuses();
  
}, []);

  useEffect(() => {
    setTimeout(() => {
      if (stompClient?.connected) {
          setConnected(stompClient.connected);
      }
  }, 1000);
  
  }, [stompClient]);

  useEffect(() => {
    if (!stompClient || !isConnected || !connected) return;
        console.log("subscribing to topic "+ connected);
        // Subscribe to a topic
        const subscription = stompClient.subscribe("/topic/status", (status) => {
          setStatuses((prevStatuses) => {
            console.log("new status --  ", status.body);
            const updatedMap = new Map(prevStatuses);
            const statusMsg = JSON.parse(status.body);
              updatedMap.set(  statusMsg.userId,statusMsg.status);
            return updatedMap;
          });
        });
        console.log("subscribed to topic "+ connected);

        return () => {
            subscription.unsubscribe();
        };
  }, [stompClient, isConnected, connected]);


  useEffect( () => {
    setCurrentUser(authService.getCurrentUser());
  
  }, [navigate]);

  useEffect(() => {
    // Check if we're on the friends route
    const isFriendsRoute = location.pathname === '/app' || location.pathname === '/app/friends';
    setShowFriendsList(isFriendsRoute);
  }, [location]);

  const handleServerClick = (serverId: number) => {
    navigate(`/app/servers/${serverId}`);
  };

  const handleAddServer = () => {
    navigate('/app/servers/new');
  };

  const handleFriendsClick = () => {
    navigate('/app/friends');
  };

  const handleAddDM = () => {
    // This would open a dialog to select a friend to DM
    console.log('Add DM clicked');
  };

  if (!currentUser) {
    return null; // Or a loading spinner
  }

  return (
    <Box sx={{ 
      height: '100vh', 
      display: 'flex', 
      bgcolor: '#36393f', 
      color: '#dcddde' 
    }}>
      {/* Server list sidebar */}
      <Box sx={{ 
        width: '72px', 
        bgcolor: '#202225', 
        display: 'flex', 
        flexDirection: 'column', 
        alignItems: 'center', 
        py: 2, 
        gap: 2 
      }}>
        {dummyServers.map(server => (
          <Avatar 
            key={server.id}
            onClick={() => handleServerClick(server.id)}
            sx={{ 
              width: 48, 
              height: 48, 
              bgcolor: '#36393f',
              cursor: 'pointer',
              transition: 'all 0.2s',
              '&:hover': {
                bgcolor: '#5865f2',
                borderRadius: '16px'
              }
            }}
          >
            {server.initial}
          </Avatar>
        ))}
        
        <Avatar 
          onClick={handleAddServer}
          sx={{ 
            width: 48, 
            height: 48, 
            bgcolor: '#36393f',
            cursor: 'pointer',
            '&:hover': {
              bgcolor: '#3ba55d',
              borderRadius: '16px'
            }
          }}
        >
          <AddIcon />
        </Avatar>
      </Box>

      {/* Channel/DM list sidebar */}
      <Box sx={{
        width: '240px',
        bgcolor: '#2f3136',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative'
      }}>
        <Box 
          sx={{ 
            p: 2, 
            fontWeight: 'bold', 
            borderBottom: '1px solid #26282c',
            cursor: 'pointer'
          }}
          onClick={handleFriendsClick}
        >
          Friends
        </Box>
        
        <Box sx={{ flex: 1, overflowY: 'auto' }}>
          <DirectMessageList onAddDM={handleAddDM} />
        </Box>
        
        {/* Bottom user profile */}
        <UserProfile 
          user={currentUser}
          status={getUserStatus(currentUser.id)||UserStatus.ONLINE}
          customStatus={getUserStatus(currentUser.id)||UserStatus.ONLINE}
        />
      </Box>

      {/* Main content area */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
        {showFriendsList ? (
          <FriendsList />
        ) : (
          <Outlet />
        )}
      </Box>

      {/* Right sidebar - Active Now */}
      <Box sx={{ 
        width: '340px', 
        bgcolor: '#2f3136', 
        p: 2, 
        borderLeft: '1px solid #26282c' 
      }}>
        <Typography sx={{ fontSize: '20px', fontWeight: 'bold', mb: 3 }}>
          Active Now
        </Typography>
        
        <Box sx={{ textAlign: 'center', mt: 6 }}>
          <Typography sx={{ fontSize: '16px', fontWeight: 'bold', mb: 1 }}>
            It's quiet for now...
          </Typography>
          <Typography sx={{ color: '#96989d', fontSize: '14px', lineHeight: 1.4 }}>
            When a friend starts an activity - like playing a game or hanging out on voice - we'll show it here!
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}