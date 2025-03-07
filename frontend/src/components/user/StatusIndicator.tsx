import { Box, Tooltip } from '@mui/material';
import { UserStatus, STATUS_COLORS } from '../../types/status';

interface StatusIndicatorProps {
  status: UserStatus | string;
  size?: number;
  showTooltip?: boolean;
  borderColor?: string;
}

export default function StatusIndicator({
  status,
  size = 10,
  showTooltip = true,
  borderColor = '#36393f'
}: StatusIndicatorProps) {
  const statusColor = STATUS_COLORS[status as UserStatus] || STATUS_COLORS[UserStatus.OFFLINE];
  
  const statusText = {
    [UserStatus.ONLINE]: 'Online',
    [UserStatus.IDLE]: 'Idle',
    [UserStatus.DO_NOT_DISTURB]: 'Do Not Disturb',
    [UserStatus.OFFLINE]: 'Offline'
  }[status as UserStatus] || 'Offline';

  const indicator = (
    <Box
      sx={{
        width: size,
        height: size,
        bgcolor: statusColor,
        borderRadius: '50%',
        border: `2px solid ${borderColor}`,
        position: 'absolute',
        bottom: -2,
        right: -2
      }}
    />
  );

  if (!showTooltip) {
    return indicator;
  }

  return (
    <Tooltip title={statusText} placement="top">
      {indicator}
    </Tooltip>
  );
} 