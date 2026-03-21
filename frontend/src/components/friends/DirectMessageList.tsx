import { useState, useEffect } from 'react';
import { Box, Typography, Avatar, IconButton } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import StatusIndicator from '../user/StatusIndicator';
import { Friend } from '../../types/friend';
import { UserStatus } from '../../types/status';
import { useStatus } from '../../hooks/useStatus';
import{useFriends} from"../../hooks/useFriends";
interface DirectMessageListProps {
  onAddDM?: () => void;
}

export default function DirectMessageList({ onAddDM }: DirectMessageListProps) {

  const { friendStatuses } = useStatus();
  const { friends:allFriends } = useFriends();
  const getStatus = (userId: number) => {
    return friendStatuses[userId] || UserStatus.OFFLINE;
  };
  const [selectedFriend, setSelectedFriend] = useState<Friend | null>(null);
  const navigate = useNavigate();

        const loading = allFriends.isLoading;



    // Sort by online status first, then by username
      const friends = allFriends.data.sort((a, b) => {
        const aStatus = getStatus(a.id);
        const bStatus = getStatus(b.id);
        console.log("aStatus", aStatus);
        console.log("bStatus", bStatus);
        const aOnline = aStatus === UserStatus.ONLINE || 
                       aStatus === UserStatus.IDLE || 
                       aStatus === UserStatus.DO_NOT_DISTURB;
        const bOnline = bStatus === UserStatus.ONLINE || 
                       bStatus === UserStatus.IDLE || 
                       bStatus === UserStatus.DO_NOT_DISTURB;
        
        if (aOnline && !bOnline) return -1;
        if (!aOnline && bOnline) return 1;
        return a.username.localeCompare(b.username);
      });
   

  const handleFriendClick = (friend) => {
    setSelectedFriend(friend);
    navigate(`/channels/@me/${friend.id}`);
  };

  const handleAddDM = () => {
    if (onAddDM) {
      onAddDM();
    }
  };

  return (
    <Box sx={{ p: 2 }}>
      <Box sx={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: 'center',
        mb: 2
      }}>
        <Typography sx={{ 
          color: '#96989d', 
          fontSize: '12px', 
          textTransform: 'uppercase',
          fontWeight: 'bold'
        }}>
          Direct Messagess
        </Typography>
        <IconButton 
          size="small" 
          onClick={handleAddDM}
          sx={{ color: '#96989d' }}
        >
          <AddIcon fontSize="small" />
        </IconButton>
      </Box>

      {loading ? (
        <Box sx={{ p: 2, color: '#96989d', textAlign: 'center' }}>
          <Typography variant="caption">Loading...</Typography>
        </Box>
      ) : friends.length === 0 ? (
        <Box sx={{ p: 2, color: '#96989d', textAlign: 'center' }}>
          <Typography variant="caption">No direct messages yet</Typography>
        </Box>
      ) : (
        friends.map(friend => {
          const isSelected = selectedFriend?.id === friend.id;
          
          return (
          <Box 
            key={friend.id}
            onClick={() => handleFriendClick(friend)}
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
                src={friend.avatarUrl}
                sx={{ 
                  width: 32, 
                  height: 32, 
                  bgcolor: '#ed4245',
                  fontSize: '14px'
                }}
              >
                {friend.username.charAt(0).toUpperCase()}
              </Avatar>
              <StatusIndicator 
                status={getStatus(friend.id)}
                borderColor="#2f3136"
              />
            </Box>
            <Typography sx={{  color: isSelected ? 'white' : '#b9bbbe', flexGrow: 1, fontSize: '14px' }}>
              {friend.username}
            </Typography>
          </Box>
        );})
      )}
    </Box>
  );
} 