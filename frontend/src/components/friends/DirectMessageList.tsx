import { useState } from 'react';
import { Box, Typography, Avatar, IconButton, Skeleton } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useNavigate, useParams } from 'react-router-dom';
import StatusIndicator from '../user/StatusIndicator';
import { Friend } from '../../types/friend';
import { UserStatus } from '../../types/status';
import { useStatus } from '../../hooks/useStatus';
import { useFriends } from '../../hooks/useFriends';

interface DirectMessageListProps {
  onAddDM?: () => void;
}

export default function DirectMessageList({ onAddDM }: DirectMessageListProps) {
  const { friendStatuses } = useStatus();
  const { friends: allFriends } = useFriends();
  const navigate = useNavigate();
  const { friendId } = useParams(); // highlight active DM

  const getStatus = (userId: number) =>
    friendStatuses[userId] || UserStatus.OFFLINE;

  const loading = allFriends.isLoading;

  const friends = [...(allFriends.data ?? [])].sort((a, b) => {
    const isOnline = (s: UserStatus) =>
      s === UserStatus.ONLINE || s === UserStatus.IDLE || s === UserStatus.DO_NOT_DISTURB;
    const aOn = isOnline(getStatus(a.id));
    const bOn = isOnline(getStatus(b.id));
    if (aOn && !bOn) return -1;
    if (!aOn && bOn) return 1;
    return a.username.localeCompare(b.username);
  });

  return (
    <Box sx={{ p: 1 }}>

      {/* Section header */}
      <Box sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        px: 1,
        py: 1.5,
      }}>
        <Typography sx={{
          color: '#949ba4',
          fontSize: 11,
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '.05em',
        }}>
          Direct Messages
        </Typography>
        <IconButton
          size="small"
          onClick={onAddDM}
          sx={{
            color: '#949ba4',
            p: 0.25,
            '&:hover': { color: '#f2f3f5' },
          }}
        >
          <AddIcon sx={{ fontSize: 16 }} />
        </IconButton>
      </Box>

      {/* List */}
      {loading ? (
        <Box sx={{ px: 1 }}>
          {[1, 2, 3].map(i => (
            <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 1.5, py: 1 }}>
              <Skeleton variant="circular" width={32} height={32} />
              <Skeleton variant="text" width="65%" />
            </Box>
          ))}
        </Box>
      ) : friends.length === 0 ? (
        <Box sx={{ px: 1, py: 2, textAlign: 'center' }}>
          <Typography sx={{ color: '#949ba4', fontSize: 13 }}>
            No direct messages yet
          </Typography>
        </Box>
      ) : (
        friends.map(friend => {
          const isActive = String(friend.id) === String(friendId);
          return (
            <Box
              key={friend.id}
              onClick={() => navigate(`/channels/@me/${friend.id}`)}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                px: 1,
                py: 0.75,
                borderRadius: 1,
                cursor: 'pointer',
                bgcolor: isActive ? '#404249' : 'transparent',
                transition: 'background .1s',
                '&:hover': { bgcolor: isActive ? '#404249' : '#35373c' },
              }}
            >
              <Box sx={{ position: 'relative', flexShrink: 0 }}>
                <Avatar
                  src={friend.avatarUrl}
                  sx={{ width: 32, height: 32, bgcolor: '#ed4245', fontSize: 13 }}
                >
                  {friend.username.charAt(0).toUpperCase()}
                </Avatar>
                <StatusIndicator status={getStatus(friend.id)} borderColor="#2b2d31" />
              </Box>

              <Typography sx={{
                color: isActive ? '#f2f3f5' : '#949ba4',
                fontSize: 14,
                fontWeight: isActive ? 500 : 400,
                flex: 1,
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                transition: 'color .1s',
              }}>
                {friend.username}
              </Typography>
            </Box>
          );
        })
      )}
    </Box>
  );
}