import { useState } from 'react';
import { Box, Avatar, Typography, IconButton } from '@mui/material';
import SettingsIcon from '@mui/icons-material/Settings';
import MicIcon from '@mui/icons-material/Mic';
import HeadsetIcon from '@mui/icons-material/Headset';
import StatusIndicator from './StatusIndicator';
import UserStatusSelector from './UserStatusSelector';
import { UserStatus } from '../../types/status';
import { User } from '../../types/auth';
import LogoutIcon from '@mui/icons-material/Logout';

interface UserProfileProps {
  user: User;
  status: UserStatus;
  customStatus: UserStatus;
}

export default function UserProfile({ user, status, customStatus }: UserProfileProps) {
  const [statusAnchorEl, setStatusAnchorEl] = useState<HTMLElement | null>(null);
  
  const handleStatusClick = (event: React.MouseEvent<HTMLElement>) => {
    setStatusAnchorEl(event.currentTarget);
  };

  const handleStatusClose = () => {
    setStatusAnchorEl(null);
  };
  const handleLogout = () => {
    // Remove authentication tokens
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");

    // Clear any user-related data
    sessionStorage.clear(); // Optional: if using sessionStorage
    // Dispatch an action if using Redux (example: dispatch(logoutUser()))

    // Redirect to login page
    window.location.href = "/login";
};

  // Get first letter of username for avatar
  const avatarText = user.username.charAt(0).toUpperCase();

  return (
    <Box sx={{ 
      p: 1, 
      bgcolor: '#292b2f', 
      display: 'flex', 
      alignItems: 'center', 
       border:'1px solid #2f3136',
        borderRadius:'8px',
     
      gap: 1 
    }}>
      <Box sx={{ position: 'relative' }} onClick={handleStatusClick}>
        <Avatar 
          src={user.avatarUrl}
          sx={{ 
            width: 32, 
            height: 32, 
            bgcolor: '#ed4245',
            fontSize: '14px',
            cursor: 'pointer'
          }}
        >
          {avatarText}
        </Avatar>
        <StatusIndicator 
          status={customStatus} 
          borderColor="#292b2f"
        />
      </Box>
      
      <Box sx={{ flexGrow: 1 }}>
        <Typography sx={{ fontSize: '14px', fontWeight: 'bold' }}>
          {user.username}
        </Typography>
        {customStatus ? (
          <Typography sx={{ fontSize: '12px', color: '#96989d' }}>
            {customStatus}
          </Typography>
        ) : (
          <Typography sx={{ fontSize: '12px', color: '#96989d' }}>
            {status === UserStatus.ONLINE ? 'Online' : 
             status === UserStatus.IDLE ? 'Idle' : 
             status === UserStatus.DO_NOT_DISTURB ? 'Do Not Disturb' : 'Offline'}
          </Typography>
        )}
      </Box>
      
      <IconButton size="small" sx={{ color: '#b9bbbe' } }   onClick={handleLogout}>
        Logout<LogoutIcon fontSize="small" />
      </IconButton>

      <UserStatusSelector 
        currentStatus={customStatus}
        anchorEl={statusAnchorEl}
        onClose={handleStatusClose}
      />
    </Box>
  );
} 