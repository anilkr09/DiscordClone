import { useState, useEffect } from 'react';
import { useWebSocketTopic } from '../../providers/WebSocketProvider';
// import { useStatusContext  } from '../../services/StatusProvider';
import { 
  Box, 
  Menu, 
  MenuItem, 
  ListItemIcon, 
  ListItemText, 
  TextField, 
  Button, 
  Typography,
  Divider
} from '@mui/material';
import { UserStatus, STATUS_COLORS, CustomStatus } from '../../types/status';
import CircleIcon from '@mui/icons-material/Circle';
import NightsStayIcon from '@mui/icons-material/NightsStay';
import DoNotDisturbOnIcon from '@mui/icons-material/DoNotDisturbOn';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
// import { useAuth } from '../../hooks/useAuth';
import { useStatus } from '../../hooks/useStatus';
interface UserStatusSelectorProps {
  currentStatus: UserStatus;
  anchorEl: HTMLElement | null;
  onClose: () => void;
}

export default function UserStatusSelector({ 
  anchorEl, 
  onClose 
}: UserStatusSelectorProps) {
  const {  updateCustomStatus, clearCustomStatus} = useStatus();





const handleStatusChange = (status: UserStatus) => {
  console.log("status click", status);

  try {
    // Close menu first
    onClose();

    // Then update after focus cleanup
    requestAnimationFrame(() => {
      if(status==UserStatus.ONLINE)
        clearCustomStatus();
      else
      updateCustomStatus(status);
    });

  } catch (error) {
    console.error('Failed to update status:', error);
  }
};
  

  return (
    <Menu
      anchorEl={anchorEl}
      open={Boolean(anchorEl)}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'top',
        horizontal: 'center',
      }}
      transformOrigin={{
        vertical: 'bottom',
        horizontal: 'center',
      }}
      PaperProps={{
        sx: {
          bgcolor: '#18191c',
          color: '#dcddde',
          width: 300,
          borderRadius: '4px',
          mt: 1
        }
      }}
    >
      <Typography sx={{ p: 2, fontWeight: 'bold' }}>
        Set Status
      </Typography>

      <MenuItem onClick={() => handleStatusChange(UserStatus.ONLINE)}>
        <ListItemIcon>
          <CircleIcon sx={{ color: STATUS_COLORS[UserStatus.ONLINE] }} />
        </ListItemIcon>
        <ListItemText>Online</ListItemText>
      </MenuItem>

      <MenuItem onClick={() => handleStatusChange(UserStatus.IDLE)}>
        <ListItemIcon>
          <NightsStayIcon sx={{ color: STATUS_COLORS[UserStatus.IDLE] }} />
        </ListItemIcon>
        <ListItemText>Idle</ListItemText>
      </MenuItem>

      <MenuItem onClick={() => handleStatusChange(UserStatus.DO_NOT_DISTURB)}>
        <ListItemIcon>
          <DoNotDisturbOnIcon sx={{ color: STATUS_COLORS[UserStatus.DO_NOT_DISTURB] }} />
        </ListItemIcon>
        <ListItemText>Do Not Disturb</ListItemText>
      </MenuItem>

      <MenuItem onClick={() => handleStatusChange(UserStatus.OFFLINE)}>
        <ListItemIcon>
          <VisibilityOffIcon sx={{ color: STATUS_COLORS[UserStatus.OFFLINE] }} />
        </ListItemIcon>
        <ListItemText>Invisible</ListItemText>
      </MenuItem>

      <Divider sx={{ my: 1, bgcolor: '#2f3136' }} />

     
    </Menu>
  );
} 