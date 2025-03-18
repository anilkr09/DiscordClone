  import { useState, useEffect, useCallback, useContext } from 'react';
  import { 
    Box, 
    Typography, 
    Avatar, 
    IconButton, 
    Tabs, 
    Tab, 
    CircularProgress,
    Divider,
    Badge
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
  import { useWebSocket } from '../../services/WebSocketProvider';
  import { useAuth } from '../../services/AuthProvider.tsx';
import { StatusUpdate } from '../../types/status';
import { useUserStatus } from '../../hooks/useUserStatus';

// import { useStatus } from '../../services/statusProvider';
import { useStatusService } from '../../services/status.service';


  enum FriendTab {
    ONLINE = 'online',
    ALL = 'all',
    PENDING = 'pending',
    ADD_FRIEND = 'add'
  }

  export default function FriendsList() {


    const { getStatus, updateCustomStatus } = useStatusService();
    // console.log("statusService", getStatus());


    const [activeTab, setActiveTab] = useState<FriendTab>(FriendTab.ONLINE);
    const [friends, setFriends] = useState<Friend[]>([]);
    const [friendRequests, setFriendRequests] = useState<FriendRequestType[]>([]);
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const { username } = useAuth();
    // console.log("username", username);
    const { stompClient, isConnected } = useWebSocket();

    const [connected, setConnected] = useState<boolean>(false); // New state to track stompClient.connected

    const [status, setStatus] = useState<any[]>([]);
    const [statuses, setStatuses] = useState<Map<number, UserStatus>>(new Map());

useEffect(() => {
  const fetchStatuses = async () => {
    try {
      const response = await getStatus(); // Assume getStatuses() returns a Promise<UserStatus[]>
      const statusMap = new Map<number, UserStatus>();
  
      response.forEach((st:StatusUpdate) => {
        statusMap.set(st.id,st.status);
      });
  
      setStatuses(statusMap);
    } catch (error) {
      console.error("Error fetching statuses:", error);
    }
  };
  fetchStatuses();
  
}, []);

const updateUserStatus = (id: number, newStatus: UserStatus) => {
  setStatuses((prevStatuses) => {
    const updatedMap = new Map(prevStatuses);
    updatedMap.set(id, newStatus);
    return updatedMap;
  });
};

const getUserStatus = (id: number) => statuses.get(id);

    useEffect(() => {
      



      console.log("status updated --  ", statuses);
    }, [statuses]);


    useEffect(() => {
      if (!stompClient || !isConnected || !connected) return;
          console.log("subscribing to topic "+ connected);
          // Subscribe to a topic
          const subscription = stompClient.subscribe("/topic/status", (status) => {
            setStatuses((prevStatuses) => {
              console.log("new status --  ", status.body);
              const updatedMap = new Map(prevStatuses);
              const statusMsg = JSON.parse(status.body);
                updatedMap.set(  statusMsg.userId,statusMsg.status);
              return updatedMap;
            });
          });
          console.log("subscribed to topic "+ connected);

          return () => {
              subscription.unsubscribe();
          };
    }, [connected]);


    useEffect(() => {
      setTimeout(() => {
        if (stompClient?.connected) {
            setConnected(stompClient.connected);
        }
    }, 1000);
    
    }, [stompClient]); // Runs whenever stompClient updates
    
 
    
    const loadData = useCallback(async () => {
      setLoading(true);
      setError(null);
      
      try {
        if (activeTab === FriendTab.PENDING) {
          const requests = await friendService.getFriendRequests();
          setFriendRequests(requests);
        } else {
          let friendsData: Friend[];
          
          if (activeTab === FriendTab.ONLINE) {
            // Get all friends and filter by real-time status
            friendsData = await friendService.getFriends();
            
            friendsData = friendsData.filter(friend => {
              const currentStatus= getUserStatus(friend.id);
              return currentStatus === UserStatus.ONLINE || 
                    currentStatus === UserStatus.IDLE || 
                    currentStatus === UserStatus.DO_NOT_DISTURB;
            });
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
    },[activeTab]);
    //  [activeTab, getStatus]);

    useEffect(() => {
      loadData();
        // console.log("friend requests dd "+"friendRequests");
      // Set up polling for friend requests
      const interval = setInterval(() => {
        if (activeTab === FriendTab.PENDING) {
          friendService.getFriendRequests()
            .then(requests => setFriendRequests(requests))
            .catch(error => console.error('Error polling friend requests:', error));
        }
      }, 30000); // Poll every 30 seconds
      
      return () => clearInterval(interval);
    }, [activeTab, loadData]);

    const handleTabChange = (_: React.SyntheticEvent, newValue: FriendTab) => {
      setActiveTab(newValue);
    };

    const handleAcceptFriendRequest = async (requestId: number) => {
      try {
        await friendService.acceptFriendRequest(requestId);
        // Remove the request from the list
        setFriendRequests(prev => prev.filter(request => request.id !== requestId));
        // Reload friends if on the friends tab
        if (activeTab !== FriendTab.PENDING) {
          loadData();
        }
      } catch (error) {
        console.error('Error accepting friend request:', error);
      }
    };

    const handleRejectFriendRequest = async (requestId: number) => {
      try {
        await friendService.rejectFriendRequest(requestId);
        // Remove the request from the list
        setFriendRequests(prev => prev.filter(request => request.id !== requestId));
      } catch (error) {
        console.error('Error rejecting friend request:', error);
      }
    };

    const handleRemoveFriend = async (friendId: number) => {
      try {
        await friendService.removeFriend(friendId);
        // Remove the friend from the list
        setFriends(prev => prev.filter(friend => friend.id !== friendId));
      } catch (error) {
        console.error('Error removing friend:', error);
      }
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
            
            {friendRequests.map(request => {
              console.log(JSON.stringify(request)); // Log request

              return ( // Explicitly return JSX
                <FriendRequest
                  key={request.id}
                  request={request}
                  onAccept={() => handleAcceptFriendRequest(request.id)}
                  onReject={() => handleRejectFriendRequest(request.id)}
                />
              );
            })}

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
              <FriendItem 
                key={friend.id} 
                friend={friend} 
                status={getUserStatus(friend.id)}
                onRemove={handleRemoveFriend} 
              />
            ))}
          </Box>
        </Box>
      );
    };

    // Count pending requests for badge
    const pendingCount = friendRequests.length;

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
              icon={
                pendingCount > 0 ? (
                  <Badge badgeContent={pendingCount} color="error">
                    <span>Pending</span>
                  </Badge>
                ) : 'Pending'
              }
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

  // Helper function to render a friend item
  const FriendItem = ({ 
    friend, 
    status,
    onRemove 
  }: { 
    friend: Friend;
    status: UserStatus;
    onRemove: (friendId: number) => void;
  }) => {
    
    return (
      <Box 
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
            status={status}
            borderColor="#36393f"
          />
        </Box>
        
        <Typography sx={{ flexGrow: 1, fontSize: '14px' }}>
          {friend.username}
        </Typography>
        
        <Typography sx={{ color: '#b9bbbe', fontSize: '14px' }}>
          {status === UserStatus.ONLINE ? 'Online' : 
          status === UserStatus.IDLE ? 'Idle' : 
          status === UserStatus.DO_NOT_DISTURB ? 'Do Not Disturb' : 'Offline'}
        </Typography>
        
        <IconButton size="small" sx={{ color: '#b9bbbe' }}>
          <ChatIcon fontSize="small" />
        </IconButton>
        
        <IconButton 
          size="small" 
          sx={{ color: '#b9bbbe' }}
          onClick={(e) => {
            e.stopPropagation();
            onRemove(friend.id);
          }}
        >
          <MoreHorizIcon fontSize="small" />
        </IconButton>
      </Box>
    );
  }; 