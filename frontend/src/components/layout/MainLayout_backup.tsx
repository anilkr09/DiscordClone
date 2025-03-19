import { Box, Typography, Avatar, InputBase, IconButton } from '@mui/material';
import { Outlet } from 'react-router-dom';
import AddIcon from '@mui/icons-material/Add';
import SearchIcon from '@mui/icons-material/Search';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import ChatIcon from '@mui/icons-material/Chat';

import PeopleAltIcon from '@mui/icons-material/PeopleAlt';

// Dummy data for the layout
const dummyServers = [
  { id: 1, name: 'Discord', initial: 'D' },
  { id: 2, name: 'Awesome Server', initial: 'AS' },
  { id: 3, name: 'Cool Group', initial: 'CG' },
];

const dummyFriends = [
  { id: 1, username: 'anilkr03', status: 'online', avatar: 'A' },
  { id: 2, username: 'johndoe', status: 'online', avatar: 'J' },
  { id: 3, username: 'janedoe', status: 'offline', avatar: 'J' },
];

const currentUser = {
  username: 'aaaa',
  status: 'Online',
  avatar: 'A'
};

export default function MainLayout() {
  return (
    <Box sx={{ display: 'flex', height: '100vh', bgcolor: '#36393f', color: '#dcddde', fontFamily: 'Arial, sans-serif' }}>
      {/* Left sidebar with server icons */}
      <Box sx={{
        width: '72px',
        bgcolor: '#202225',
        py: 1.5,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 1
      }}>
        {dummyServers.map(server => (
          <Box
            key={server.id}
            sx={{
              width: '48px',
              height: '48px',
              bgcolor: '#5865f2',
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
              fontWeight: 'bold',
              cursor: 'pointer',
              fontSize: '14px'
            }}
          >
            {server.initial}
          </Box>
        ))}
        <Box
          sx={{
            width: '48px',
            height: '48px',
            bgcolor: '#36393f',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#3ba55d',
            cursor: 'pointer',
            fontSize: '24px'
          }}
        >
          <AddIcon />
        </Box>
      </Box>

      {/* Channel sidebar */}
      <Box sx={{
        width: '240px',
        bgcolor: '#2f3136',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative'
      }}>
          {/* <Box sx={{ p: 2, fontWeight: 'bold', borderBottom: '1px solid #26282c' }}>
            Friends
          </Box> */}
        
        {/* <Box sx={{ flex: 1, overflowY: 'auto' }}>
          <Box sx={{ 
            px: 1, 
            py: 1, 
            display: 'flex', 
            justifyContent: 'space-between', 
            alignItems: 'center',
            color: '#96989d',
            fontSize: '12px',
            textTransform: 'uppercase',
            fontWeight: 'bold'
          }}>
            <Typography variant="caption" sx={{ fontWeight: 'bold' }}>
              Direct Messages
            </Typography>
            <AddIcon sx={{ fontSize: 18, cursor: 'pointer' }} />
          </Box>
          
          <Box sx={{ mt: 1 }}>
            {dummyFriends.map(friend => (
              <Box 
                key={friend.id}
                sx={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  p: 1, 
                  borderRadius: '4px',
                  cursor: 'pointer',
                  gap: 1.5,
                  '&:hover': {
                    bgcolor: '#36393f'
                  }
                }}
              >
                <Box sx={{ position: 'relative' }}>
                  <Avatar 
                    sx={{ 
                      width: 32, 
                      height: 32, 
                      bgcolor: '#ed4245',
                      fontSize: '14px'
                    }}
                  >
                    {friend.avatar}
                  </Avatar>
                  <Box 
                    sx={{ 
                      width: 10, 
                      height: 10, 
                      bgcolor: friend.status === 'online' ? '#3ba55d' : '#747f8d',
                      borderRadius: '50%',
                      border: '2px solid #2f3136',
                      position: 'absolute',
                      bottom: -2,
                      right: -2
                    }} 
                  />
                </Box>
                <Typography sx={{ flexGrow: 1, fontSize: '14px' }}>
                  {friend.username}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box> */}
        
        {/* Bottom user profile */}
        <Box sx={{ 
          p: 1, 
          bgcolor: '#292b2f', 
          display: 'flex', 
          alignItems: 'center', 
          gap: 1 
        }}>
          <Box sx={{ position: 'relative' }}>
            <Avatar 
              sx={{ 
                width: 32, 
                height: 32, 
                bgcolor: '#ed4245',
                fontSize: '14px'
              }}
            >
              {currentUser.avatar}
            </Avatar>
            <Box 
              sx={{ 
                width: 10, 
                height: 10, 
                bgcolor: '#3ba55d',
                borderRadius: '50%',
                border: '2px solid #292b2f',
                position: 'absolute',
                bottom: -2,
                right: -2
              }} 
            />
          </Box>
          <Box sx={{ flexGrow: 1 }}>
            <Typography sx={{ fontSize: '14px', fontWeight: 'bold' }}>
              {currentUser.username}
            </Typography>
            <Typography sx={{ fontSize: '12px', color: '#96989d' }}>
              {currentUser.status}
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Main content area */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Toolbar */}
        <Box sx={{ 
          height: '48px', 
          px: 2, 
          display: 'flex', 
          alignItems: 'center', 
          borderBottom: '1px solid #26282c',
          gap: 2
        }}>
          <PeopleAltIcon sx={{ fontSize: 20 }} />
          <Typography sx={{ fontWeight: 'bold' }}>Friends</Typography>
          
          <Box sx={{ 
            display: 'flex', 
            height: '100%', 
            alignItems: 'center', 
            ml: 2 
          }}>
            <Box sx={{ 
              px: 1, 
              mx: 1, 
              borderRadius: '4px', 
              cursor: 'pointer',
              fontSize: '14px'
            }}>
              Online
            </Box>
            <Box sx={{ 
              px: 1, 
              mx: 1, 
              borderRadius: '4px', 
              cursor: 'pointer',
              fontSize: '14px',
              bgcolor: '#4f545c',
              color: 'white'
            }}>
              All
            </Box>
            <Box sx={{ 
              px: 1, 
              mx: 1, 
              borderRadius: '4px', 
              cursor: 'pointer',
              fontSize: '14px'
            }}>
              Pending
            </Box>
          </Box>
          
          <Box sx={{ 
            bgcolor: '#3ba55d', 
            color: 'white', 
            px: 2, 
            py: 0.75, 
            borderRadius: '4px', 
            fontSize: '14px', 
            ml: 'auto', 
            cursor: 'pointer' 
          }}>
            Add Friend
          </Box>
        </Box>
        
        {/* Search container */}
        <Box sx={{ p: 2, borderBottom: '1px solid #26282c' }}>
          <Box sx={{ 
            bgcolor: '#202225', 
            borderRadius: '4px', 
            px: 2, 
            py: 1, 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'space-between' 
          }}>
            <InputBase 
              placeholder="Search" 
              sx={{ 
                color: 'white',
                fontSize: '14px',
                width: '100%'
              }} 
            />
            <SearchIcon sx={{ color: '#72767d', fontSize: 18 }} />
          </Box>
        </Box>
        
        {/* Online section */}
        <Box sx={{ p: 2, flex: 1, overflowY: 'auto' }}>
          <Typography sx={{ 
            color: '#96989d', 
            fontSize: '12px', 
            textTransform: 'uppercase', 
            fontWeight: 'bold', 
            mb: 2 
          }}>
            ONLINE — {dummyFriends.filter(f => f.status === 'online').length}
          </Typography>
          
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {dummyFriends.filter(friend => friend.status === 'online').map(friend => (
              <Box 
                key={friend.id}
                sx={{ 
                  display: 'flex', 
                  alignItems: 'center', 
                  p: 1, 
                  gap: 1.5,
                  borderRadius: '4px',
                  cursor: 'pointer',
                  '&:hover': {
                    bgcolor: '#32353b'
                  }
                }}
              >
                <Box sx={{ position: 'relative' }}>
                  <Avatar 
                    sx={{ 
                      width: 32, 
                      height: 32, 
                      bgcolor: '#ed4245',
                      fontSize: '14px'
                    }}
                  >
                    {friend.avatar}
                  </Avatar>
                  <Box 
                    sx={{ 
                      width: 10, 
                      height: 10, 
                      bgcolor: '#3ba55d',
                      borderRadius: '50%',
                      border: '2px solid #36393f',
                      position: 'absolute',
                      bottom: -2,
                      right: -2
                    }} 
                  />
                </Box>
                <Typography sx={{ flexGrow: 1, fontSize: '14px' }}>
                  {friend.username}
                </Typography>
                <Typography sx={{ color: '#b9bbbe', fontSize: '14px' }}>
                  Online
                </Typography>
                <IconButton size="small" sx={{ color: '#b9bbbe' }}>
                  <ChatIcon fontSize="small" />
                </IconButton>
                <IconButton size="small" sx={{ color: '#b9bbbe' }}>
                  <MoreHorizIcon fontSize="small" />
                </IconButton>
              </Box>
            ))}
          </Box>
        </Box>
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
      
      {/* This is where nested routes will render */}
      <Box sx={{ display: 'none' }}>
        <Outlet />
      </Box>
    </Box>
  );
}