import { useState, useEffect } from 'react';
import { Box, Typography, Avatar, IconButton } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate } from 'react-router-dom';
import StatusIndicator from '../user/StatusIndicator';
import { Friend } from '../../types/friend';
import { UserStatus } from '../../types/status';
import friendService from '../../services/friend.service';

interface DirectMessageListProps {
  onAddDM?: () => void;
}

export default function DirectMessageList({ onAddDM }: DirectMessageListProps) {
  const [friends, setFriends] = useState<Friend[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const navigate = useNavigate();

  useEffect(() => {
    loadFriends();
  }, []);

  const loadFriends = async () => {
    setLoading(true);
    try {
      const data = await friendService.getFriends();
      // Sort by online status first, then by username
      const sortedFriends = data.sort((a, b) => {
        const aOnline = a.status === UserStatus.ONLINE || a.status === UserStatus.IDLE || a.status === UserStatus.DO_NOT_DISTURB;
        const bOnline = b.status === UserStatus.ONLINE || b.status === UserStatus.IDLE || b.status === UserStatus.DO_NOT_DISTURB;
        
        if (aOnline && !bOnline) return -1;
        if (!aOnline && bOnline) return 1;
        return a.username.localeCompare(b.username);
      });
      
      setFriends(sortedFriends);
    } catch (error) {
      console.error('Failed to load friends:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleFriendClick = (friendId: number) => {
    navigate(`/app/dm/${friendId}`);
  };

  const handleAddDM = () => {
    if (onAddDM) {
      onAddDM();
    }
  };

  return (
    <Box sx={{ width: '100%' }}>
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
        <IconButton 
          size="small" 
          onClick={handleAddDM}
          sx={{ 
            color: '#96989d', 
            p: 0.5,
            '&:hover': { 
              color: 'white',
              bgcolor: 'transparent'
            }
          }}
        >
          <AddIcon sx={{ fontSize: 18 }} />
        </IconButton>
      </Box>
      
      <Box sx={{ mt: 1 }}>
        {loading ? (
          <Box sx={{ p: 2, color: '#96989d', textAlign: 'center' }}>
            <Typography variant="caption">Loading...</Typography>
          </Box>
        ) : friends.length === 0 ? (
          <Box sx={{ p: 2, color: '#96989d', textAlign: 'center' }}>
            <Typography variant="caption">No direct messages yet</Typography>
          </Box>
        ) : (
          friends.map(friend => (
            <Box 
              key={friend.id}
              onClick={() => handleFriendClick(friend.id)}
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
                  status={friend.status as UserStatus} 
                  borderColor="#2f3136"
                />
              </Box>
              <Typography sx={{ flexGrow: 1, fontSize: '14px' }}>
                {friend.username}
              </Typography>
            </Box>
          ))
        )}
      </Box>
    </Box>
  );
} 