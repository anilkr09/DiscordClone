import { useState, useEffect } from 'react';
import { 
  Box, 
  Typography, 
  Avatar, 
  IconButton, 
  Tabs, 
  Tab, 
  CircularProgress,
  Divider
} from '@mui/material';
import ChatIcon from '@mui/icons-material/Chat';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import StatusIndicator from '../user/StatusIndicator';
import AddFriend from './AddFriend';
import FriendRequest from './FriendRequest';
import { Friend, FriendRequest as FriendRequestType, FriendshipStatus } from '../../types/friend';
import { UserStatus } from '../../types/status';
import friendService from '../../services/friend.service';

enum FriendTab {
  ONLINE = 'online',
  ALL = 'all',
  PENDING = 'pending',
  ADD_FRIEND = 'add'
}

export default function FriendsList() {
  const [activeTab, setActiveTab] = useState<FriendTab>(FriendTab.ONLINE);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [friendRequests, setFriendRequests] = useState<FriendRequestType[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadData();
  }, [activeTab]);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    
    try {
      if (activeTab === FriendTab.PENDING) {
        const requests = await friendService.getFriendRequests();
        setFriendRequests(requests);
      } else {
        let friendsData: Friend[];
        
        if (activeTab === FriendTab.ONLINE) {
          // Get only online friends
          friendsData = await friendService.getFriends();
          friendsData = friendsData.filter(friend => 
            friend.status === UserStatus.ONLINE || 
            friend.status === UserStatus.IDLE || 
            friend.status === UserStatus.DO_NOT_DISTURB
          );
        } else {
          // Get all friends
          friendsData = await friendService.getFriends();
        }
        
        setFriends(friendsData);
      }
    } catch (error) {
      console.error('Error loading friends data:', error);
      setError('Failed to load friends. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleTabChange = (_: React.SyntheticEvent, newValue: FriendTab) => {
    setActiveTab(newValue);
  };

  const handleAcceptFriendRequest = (friendId: number) => {
    // Remove the request from the list
    setFriendRequests(prev => prev.filter(request => 
      !(request.senderId === friendId || request.receiverId === friendId)
    ));
    
    // Reload friends if on the friends tab
    if (activeTab !== FriendTab.PENDING) {
      loadData();
    }
  };

  const handleRejectFriendRequest = (requestId: number) => {
    // Remove the request from the list
    setFriendRequests(prev => prev.filter(request => request.id !== requestId));
  };

  const renderContent = () => {
    if (loading) {
      return (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress sx={{ color: '#5865f2' }} />
        </Box>
      );
    }

    if (error) {
      return (
        <Box sx={{ p: 3, textAlign: 'center', color: '#ed4245' }}>
          <Typography>{error}</Typography>
        </Box>
      );
    }

    if (activeTab === FriendTab.ADD_FRIEND) {
      return <AddFriend />;
    }

    if (activeTab === FriendTab.PENDING) {
      if (friendRequests.length === 0) {
        return (
          <Box sx={{ p: 3, textAlign: 'center', color: '#96989d' }}>
            <Typography>No pending friend requests</Typography>
          </Box>
        );
      }

      return (
        <Box sx={{ p: 2 }}>
          <Typography sx={{ 
            color: '#96989d', 
            fontSize: '12px', 
            textTransform: 'uppercase', 
            fontWeight: 'bold', 
            mb: 2,
            px: 1
          }}>
            PENDING — {friendRequests.length}
          </Typography>
          
          {friendRequests.map(request => (
            <FriendRequest 
              key={request.id}
              request={request}
              onAccept={handleAcceptFriendRequest}
              onReject={handleRejectFriendRequest}
            />
          ))}
        </Box>
      );
    }

    // Online or All friends tab
    if (friends.length === 0) {
      return (
        <Box sx={{ p: 3, textAlign: 'center', color: '#96989d' }}>
          <Typography>
            {activeTab === FriendTab.ONLINE 
              ? 'No one is around to play with...' 
              : 'You don\'t have any friends yet. Try adding some!'}
          </Typography>
        </Box>
      );
    }

    return (
      <Box sx={{ p: 2 }}>
        <Typography sx={{ 
          color: '#96989d', 
          fontSize: '12px', 
          textTransform: 'uppercase', 
          fontWeight: 'bold', 
          mb: 2,
          px: 1
        }}>
          {activeTab === FriendTab.ONLINE ? 'ONLINE' : 'ALL FRIENDS'} — {friends.length}
        </Typography>
        
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {friends.map(friend => (
            <Box 
              key={friend.id}
              sx={{ 
                display: 'flex', 
                alignItems: 'center', 
                p: 1, 
                gap: 1.5,
                borderRadius: '4px',
                cursor: 'pointer',
                '&:hover': {
                  bgcolor: '#32353b'
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
                  borderColor="#36393f"
                />
              </Box>
              
              <Typography sx={{ flexGrow: 1, fontSize: '14px' }}>
                {friend.username}
              </Typography>
              
              <Typography sx={{ color: '#b9bbbe', fontSize: '14px' }}>
                {friend.status === UserStatus.ONLINE ? 'Online' : 
                 friend.status === UserStatus.IDLE ? 'Idle' : 
                 friend.status === UserStatus.DO_NOT_DISTURB ? 'Do Not Disturb' : 'Offline'}
              </Typography>
              
              <IconButton size="small" sx={{ color: '#b9bbbe' }}>
                <ChatIcon fontSize="small" />
              </IconButton>
              
              <IconButton size="small" sx={{ color: '#b9bbbe' }}>
                <MoreHorizIcon fontSize="small" />
              </IconButton>
            </Box>
          ))}
        </Box>
      </Box>
    );
  };

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ 
        height: '48px', 
        px: 2, 
        display: 'flex', 
        alignItems: 'center', 
        borderBottom: '1px solid #26282c',
        gap: 2
      }}>
        <PersonAddIcon sx={{ fontSize: 20 }} />
        <Typography sx={{ fontWeight: 'bold' }}>Friends</Typography>
        
        <Divider orientation="vertical" flexItem sx={{ mx: 1, bgcolor: '#26282c' }} />
        
        <Tabs 
          value={activeTab} 
          onChange={handleTabChange}
          sx={{ 
            minHeight: '48px',
            '& .MuiTabs-indicator': {
              display: 'none'
            }
          }}
        >
          <Tab 
            label="Online" 
            value={FriendTab.ONLINE} 
            sx={{ 
              minHeight: '48px',
              color: activeTab === FriendTab.ONLINE ? 'white' : '#96989d',
              bgcolor: activeTab === FriendTab.ONLINE ? '#4f545c' : 'transparent',
              borderRadius: '4px',
              mx: 0.5,
              '&:hover': {
                bgcolor: activeTab === FriendTab.ONLINE ? '#4f545c' : '#36393f',
                color: 'white'
              }
            }}
          />
          <Tab 
            label="All" 
            value={FriendTab.ALL} 
            sx={{ 
              minHeight: '48px',
              color: activeTab === FriendTab.ALL ? 'white' : '#96989d',
              bgcolor: activeTab === FriendTab.ALL ? '#4f545c' : 'transparent',
              borderRadius: '4px',
              mx: 0.5,
              '&:hover': {
                bgcolor: activeTab === FriendTab.ALL ? '#4f545c' : '#36393f',
                color: 'white'
              }
            }}
          />
          <Tab 
            label="Pending" 
            value={FriendTab.PENDING} 
            sx={{ 
              minHeight: '48px',
              color: activeTab === FriendTab.PENDING ? 'white' : '#96989d',
              bgcolor: activeTab === FriendTab.PENDING ? '#4f545c' : 'transparent',
              borderRadius: '4px',
              mx: 0.5,
              '&:hover': {
                bgcolor: activeTab === FriendTab.PENDING ? '#4f545c' : '#36393f',
                color: 'white'
              }
            }}
          />
          <Tab 
            label="Add Friend" 
            value={FriendTab.ADD_FRIEND} 
            sx={{ 
              minHeight: '48px',
              color: activeTab === FriendTab.ADD_FRIEND ? 'white' : '#3ba55d',
              bgcolor: activeTab === FriendTab.ADD_FRIEND ? '#4f545c' : 'transparent',
              borderRadius: '4px',
              mx: 0.5,
              '&:hover': {
                bgcolor: activeTab === FriendTab.ADD_FRIEND ? '#4f545c' : '#36393f',
                color: activeTab === FriendTab.ADD_FRIEND ? 'white' : '#3ba55d'
              }
            }}
          />
        </Tabs>
      </Box>
      
      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        {renderContent()}
      </Box>
    </Box>
  );
} 