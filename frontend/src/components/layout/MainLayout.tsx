import { useState, useEffect } from 'react';
import { Box, Typography, Avatar} from '@mui/material';
import {useLocation, useNavigate } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import HomeIcon from '@mui/icons-material/Home';
import CreateServerModal from '../servers/CreateServerModel';
import UserProfile from '../user/UserProfile';
import { UserStatus } from '../../types/status';
import { User } from '../../types/auth';
import authService from '../../services/auth.service';
import { useStatus } from '../../hooks/useStatus';
import HomeView from './HomeView';
import serverService from '../../services/server.service';
import { useServers } from '../../hooks/useServers';
// Dummy data for the layout
// import { useWebSocket } from '../../services/WebSocketProvider';

const dummyServers = [
  { id:0, name: 'Home', initial: 'Hjj' },
  { id:1, name: 'Default Server', initial: 'DS' }
];

export default function MainLayout() {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const navigate = useNavigate();
  const {  friendStatuses } = useStatus();
  const getUserStatus = (id: number) => friendStatuses[id] || UserStatus.OFFLINE;
  const [open, setOpen] = useState(false);
      const {servers,createServer} = useServers();

  useEffect( () => {
    setCurrentUser(authService.getCurrentUser());
  
  }, [navigate]);

 

  const handleServerClick = (serverId: number) => {
    if(serverId==0)
      navigate(`/channels/@me`);
    else
    navigate(`/channels/${serverId}`);
  };

  const handleAddServer = () => {
   setOpen(true); 
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
      width:'100%',
      display: 'flex', 
      bgcolor: '#36393f', 
      color: '#dcddde', 
      position: 'fixed',
  top: 0,
  left: 0,
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
        {servers.data.map(server => (
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
          {server.name.substring(0,3)/* {server?.initial === "Hjj" ? <HomeIcon /> : server?.initial} */}
            
          </Avatar>
        ))}

<CreateServerModal
  open={open}
  onClose={() => setOpen(false)}
  onCreate={(data) => {
    // serverService.createServer(data).then((response) => {
    //   console.log('Server created:', response);
    //   navigate(`/channels/${response.id}`);
    // })
    // .catch((error) => {
    //   console.error('Failed to create server:', error);
    // }); 
    createServer.mutate(data);
    console.log(data);
  }}
  onJoin={(inviteCode) => {
    // call backend API
    // POST /servers/join
    console.log(inviteCode);
      }}
/>
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
      {/* {conditionforserverorDM ? (
        ) : (
        )} */}


      {/* Main content area */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
      <HomeView></HomeView>
      </Box>
      

      {/* Right sidebar - Active Now */}
      <Box sx={{ 
        width: '340px', 
        bgcolor: '#2f3136', 
        p: 2, 
        borderLeft: '1px solid #26282c' 
      }}>
        <Typography sx={{ fontSize: '20px', fontWeight: 'bold', mb: 3 }}>
          {/* Active Now */}
        </Typography>
        
        <Box sx={{ textAlign: 'center', mt: 6 }}>
          <Typography sx={{ fontSize: '16px', fontWeight: 'bold', mb: 1 }}>
            
          </Typography>
          <Typography sx={{ color: '#96989d', fontSize: '14px', lineHeight: 1.4 }}>
            {/* When a friend starts an activity - like playing a game or hanging out on voice - we'll show it here! */}
          </Typography>
        </Box>
      </Box>
      <Box sx={{
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
        </Box>
    </Box>
  );
}