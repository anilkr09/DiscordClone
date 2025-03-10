import { useState, useEffect } from 'react';
import { 
  Box, 
  Typography, 
  Avatar, 
  Button, 
  CircularProgress,
  Tooltip
} from '@mui/material';
import { FriendRequest as FriendRequestType } from '../../types/friend';
import StatusIndicator from '../user/StatusIndicator';
import { UserStatus } from '../../types/status';
import userService from '../../services/user.service';

interface FriendRequestProps {
  request: FriendRequestType;
  onAccept: () => void;
  onReject: () => void;
}

export default function FriendRequest({ request, onAccept, onReject }: FriendRequestProps) {
  const [isAccepting, setIsAccepting] = useState<boolean>(false);
  const [isRejecting, setIsRejecting] = useState<boolean>(false);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  
  useEffect(() => {
   try{
    
    setCurrentUserId(userService.getCurrentUser().id);
       }
      
      catch(error){ console.error('Error getting current user:', error);}
  }, []);
    
  // Determine if the current user is the receiver
  console.log("current user id dd ---- "+ currentUserId+"requwst id dd ---- "+ request.receiverId);

  const isIncoming = currentUserId === request.receiverId;
  
  const username = isIncoming ? request.senderUsername : request.receiverUsername;
  const avatarUrl = isIncoming ? request.senderAvatarUrl : request.receiverAvatarUrl;
  const avatarText = username.charAt(0).toUpperCase();
  
  const handleAccept = async () => {
    if (isAccepting || isRejecting) return;
    
    setIsAccepting(true);
    try {
      await onAccept();
    } catch (error) {
      console.error('Error accepting friend request:', error);
    } finally {
      setIsAccepting(false);
    }
  };
  
  const handleReject = async () => {
    if (isAccepting || isRejecting) return;
    
    setIsRejecting(true);
    try {
      await onReject();
    } catch (error) {
      console.error('Error rejecting friend request:', error);
    } finally {
      setIsRejecting(false);
    }
  };

  // Format the date
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString(undefined, { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric' 
    });
  };

  if (currentUserId === null) {
    return null; // Or a loading spinner
  }

  return (
    <Box sx={{ 
      display: 'flex', 
      alignItems: 'center', 
      p: 2, 
      borderRadius: '8px',
      '&:hover': { bgcolor: '#32353b' }
    }}>
      <Box sx={{ position: 'relative' }}>
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
        <StatusIndicator 
          status={UserStatus.ONLINE} // Default to online for now
          borderColor="#36393f"
          size={12}
        />
      </Box>
      
      <Box sx={{ flexGrow: 1 }}>
        <Typography sx={{ fontWeight: 'bold', fontSize: '16px' }}>
          {username}
        </Typography>
        <Typography sx={{ color: '#96989d', fontSize: '14px' }}>
          {isIncoming ? 'Incoming Friend Request' : 'Outgoing Friend Request'}
          {request.createdAt && (
            <Tooltip title={formatDate(request.createdAt)}>
              <span style={{ marginLeft: '8px', fontSize: '12px' }}>
                • {formatDate(request.createdAt)}
              </span>
            </Tooltip>
          )}
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