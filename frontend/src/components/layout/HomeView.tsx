
import { useState, useEffect } from 'react';

import { Box, Typography, Avatar, InputBase, IconButton } from '@mui/material';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import DirectMessageList from '../friends/DirectMessageList';


export default function HomeView() {
    
    const location = useLocation();
    const navigate = useNavigate();

    const [showFriendsList, setShowFriendsList] = useState<boolean>(true);
    

    useEffect(() => {
        // Check if we're on the friends route
        // const isFriendsRoute = location.pathname === '/channels/@me' || location.pathname === '/app/friends';
        const isFriendsRoute = location.pathname.startsWith('/channels/@me');

        setShowFriendsList(isFriendsRoute);
      }, [location]);
    const handleFriendsClick = () => {
        navigate(`/channels/@me`);
    };
  
    const handleAddDM = () => {
      // This would open a dialog to select a friend to DM
      console.log('Add DM clicked');
    };
return (
<Box  sx={{ 
      height: '100vh',
    
      display: 'flex', 
      bgcolor: '#36393f', 

      color: '#dcddde', 
     
    }}>
      {showFriendsList ? (
   <Box sx={{
        width:'250px',
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
       
      </Box>
  ):null} 
   

{/* <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}> */}

  <Outlet />


{/* </Box> */}

</Box>
      


    )




    }