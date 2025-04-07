import { useState, useEffect } from 'react';
import { useWebSocketTopic } from '../../services/WebSocketProvider';
import { useStatusContext  } from '../../services/StatusProvider';
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
import { useStatus } from '../../services/StatusProvider';
interface UserStatusSelectorProps {
  currentStatus: UserStatus;
  anchorEl: HTMLElement | null;
  onClose: () => void;
}

export default function UserStatusSelector({ 
  currentStatus, 
  anchorEl, 
  onClose 
}: UserStatusSelectorProps) {
  const [customStatus, setCustomStatus] = useState<string>('');
  const [showCustomInput, setShowCustomInput] = useState<boolean>(false);
  const {  friendStatuses } = useStatus();

  const { messages } = useWebSocketTopic("/topic/status");
  const { updateCustomStatus } = useStatusContext();

  useEffect(() => {
    console.log("messages", messages);
  }, [messages]);

  const handleStatusChange =  (status: UserStatus) => {
    console.log("status click", status);
    try {

       updateCustomStatus(status);

      onClose();
    } catch (error) {
      console.error('Failed to update status:', error);
    }
  };

  const handleCustomStatusSubmit = async () => {
    if (!customStatus.trim()) {
      setShowCustomInput(false);
      return;
    }

    try {
      const customStatusObj: CustomStatus = {
        text: customStatus.trim()
      };
          // await useStatusService.updateCustomStatus(currentStatus, customStatusObj);
      setShowCustomInput(false);
      onClose();
    } catch (error) {
      console.error('Failed to update custom status:', error);
    }
  };

  const handleClearCustomStatus = async () => {
    try {
      // await useStatusService.clearCustomStatus();
      setCustomStatus('');
      setShowCustomInput(false);
    } catch (error) {
      console.error('Failed to clear custom status:', error);
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

      {showCustomInput ? (
        <Box sx={{ p: 2 }}>
          <TextField
            fullWidth
            variant="outlined"
            placeholder="What's happening?"
            value={customStatus}
            onChange={(e) => setCustomStatus(e.target.value)}
            size="small"
            InputProps={{
              sx: {
                color: 'white',
                bgcolor: '#2f3136',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#40444b'
                },
                '&:hover .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#40444b'
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: '#5865f2'
                }
              }
            }}
            sx={{ mb: 1 }}
          />
          <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
            <Button 
              variant="text" 
              onClick={() => setShowCustomInput(false)}
              sx={{ color: '#dcddde' }}
            >
              Cancel
            </Button>
            <Box>
              <Button 
                variant="text" 
                onClick={handleClearCustomStatus}
                sx={{ color: '#dcddde', mr: 1 }}
              >
                Clear
              </Button>
              <Button 
                variant="contained" 
                onClick={handleCustomStatusSubmit}
                sx={{ bgcolor: '#5865f2', '&:hover': { bgcolor: '#4752c4' } }}
              >
                Save
              </Button>
            </Box>
          </Box>
        </Box>
      ) : (
        <MenuItem onClick={() => setShowCustomInput(true)}>
          <ListItemText>Set a custom status</ListItemText>
        </MenuItem>
      )}
    </Menu>
  );
} 