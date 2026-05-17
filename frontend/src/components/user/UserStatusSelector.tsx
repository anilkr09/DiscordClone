import { Menu, MenuItem, ListItemIcon, ListItemText, Divider, ListSubheader } from '@mui/material';

import CircleIcon from '@mui/icons-material/Circle';
import NightsStayIcon from '@mui/icons-material/NightsStay';
import DoNotDisturbOnIcon from '@mui/icons-material/DoNotDisturbOn';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';

import { useStatus } from '../../hooks/useStatus';
import { UserStatus, STATUS_COLORS } from '../../types/status';

interface UserStatusSelectorProps {
  currentStatus: UserStatus;
  anchorEl: HTMLElement | null;
  onClose: () => void;
}

export default function UserStatusSelector({
  anchorEl,
  onClose,
}: UserStatusSelectorProps) {

  const { updateCustomStatus, clearCustomStatus } = useStatus();

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
      disableAutoFocusItem
      keepMounted
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
          mt: 1,
        },
      }}
      MenuListProps={{
        autoFocus: false,
      }}
    >
      <ListSubheader
        disableSticky
        sx={{
          bgcolor: '#18191c',
          color: '#ffffff',
          fontWeight: 'bold',
          lineHeight: '40px',
        }}
      >
        Set Status
      </ListSubheader>

      <MenuItem
        onClick={() => handleStatusChange(UserStatus.ONLINE)}
      >
        <ListItemIcon>
          <CircleIcon
            sx={{
              color: STATUS_COLORS[UserStatus.ONLINE],
            }}
          />
        </ListItemIcon>

        <ListItemText primary="Online" />
      </MenuItem>

      <MenuItem
        onClick={() => handleStatusChange(UserStatus.IDLE)}
      >
        <ListItemIcon>
          <NightsStayIcon
            sx={{
              color: STATUS_COLORS[UserStatus.IDLE],
            }}
          />
        </ListItemIcon>

        <ListItemText primary="Idle" />
      </MenuItem>

      <MenuItem
        onClick={() =>
          handleStatusChange(UserStatus.DO_NOT_DISTURB)
        }
      >
        <ListItemIcon>
          <DoNotDisturbOnIcon
            sx={{
              color: STATUS_COLORS[UserStatus.DO_NOT_DISTURB],
            }}
          />
        </ListItemIcon>

        <ListItemText primary="Do Not Disturb" />
      </MenuItem>

      <MenuItem
        onClick={() => handleStatusChange(UserStatus.OFFLINE)}
      >
        <ListItemIcon>
          <VisibilityOffIcon
            sx={{
              color: STATUS_COLORS[UserStatus.OFFLINE],
            }}
          />
        </ListItemIcon>

        <ListItemText primary="Invisible" />
      </MenuItem>

      <Divider
        sx={{
          my: 1,
          bgcolor: '#2f3136',
        }}
      />
    </Menu>
  );
}