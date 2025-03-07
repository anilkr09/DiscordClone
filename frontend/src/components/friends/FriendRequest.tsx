import { useState } from 'react';
import { 
  Box, 
  Typography, 
  Avatar, 
  Button, 
  CircularProgress 
} from '@mui/material';
import { FriendRequest as FriendRequestType } from '../../types/friend';
import friendService from '../../services/friend.service';

interface FriendRequestProps {
  request: FriendRequestType;
  onAccept: (friendId: number) => void;
  onReject: (requestId: number) => void;
}

export default function FriendRequest({ request, onAccept, onReject }: FriendRequestProps) {
  const [isAccepting, setIsAccepting] = useState<boolean>(false);
  const [isRejecting, setIsRejecting] = useState<boolean>(false);
  
  // Determine if the current user is the sender or receiver
  const isIncoming = request.receiverId === 0; // Replace with actual current user ID check
  
  const username = isIncoming ? request.senderUsername : request.receiverUsername;
  const avatarUrl = isIncoming ? request.senderAvatarUrl : request.receiverAvatarUrl;
  const avatarText = username.charAt(0).toUpperCase();
  
  const handleAccept = async () => {
    setIsAccepting(true);
    try {
      await friendService.acceptFriendRequest(request.id);
      onAccept(isIncoming ? request.senderId : request.receiverId);
    } catch (error) {
      console.error('Error accepting friend request:', error);
    } finally {
      setIsAccepting(false);
    }
  };
  
  const handleReject = async () => {
    setIsRejecting(true);
    try {
      await friendService.rejectFriendRequest(request.id);
      onReject(request.id);
    } catch (error) {
      console.error('Error rejecting friend request:', error);
    } finally {
      setIsRejecting(false);
    }
  };

  return (
    <Box sx={{ 
      display: 'flex', 
      alignItems: 'center', 
      p: 2, 
      borderRadius: '8px',
      '&:hover': { bgcolor: '#32353b' }
    }}>
      <Avatar 
        src={avatarUrl}
        sx={{ 
          width: 40, 
          height: 40, 
          bgcolor: '#ed4245',
          fontSize: '16px',
          mr: 2
        }}
      >
        {avatarText}
      </Avatar>
      
      <Box sx={{ flexGrow: 1 }}>
        <Typography sx={{ fontWeight: 'bold', fontSize: '16px' }}>
          {username}
        </Typography>
        <Typography sx={{ color: '#96989d', fontSize: '14px' }}>
          {isIncoming ? 'Incoming Friend Request' : 'Outgoing Friend Request'}
        </Typography>
      </Box>
      
      {isIncoming && (
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            onClick={handleAccept}
            disabled={isAccepting || isRejecting}
            sx={{
              bgcolor: '#5865f2',
              '&:hover': { bgcolor: '#4752c4' },
              minWidth: '80px'
            }}
          >
            {isAccepting ? <CircularProgress size={20} color="inherit" /> : 'Accept'}
          </Button>
          
          <Button
            variant="contained"
            onClick={handleReject}
            disabled={isAccepting || isRejecting}
            sx={{
              bgcolor: '#4f545c',
              '&:hover': { bgcolor: '#36393f' },
              minWidth: '80px'
            }}
          >
            {isRejecting ? <CircularProgress size={20} color="inherit" /> : 'Ignore'}
          </Button>
        </Box>
      )}
      
      {!isIncoming && (
        <Button
          variant="contained"
          onClick={handleReject}
          disabled={isRejecting}
          sx={{
            bgcolor: '#4f545c',
            '&:hover': { bgcolor: '#36393f' }
          }}
        >
          {isRejecting ? <CircularProgress size={20} color="inherit" /> : 'Cancel'}
        </Button>
      )}
    </Box>
  );
} 