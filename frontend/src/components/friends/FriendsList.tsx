import { useState } from "react";
import {
  Box,
  Typography,
  Avatar,
  IconButton,
  Tabs,
  Tab,
  CircularProgress,
  Divider,
  Badge,
  Skeleton
} from "@mui/material";

import ChatIcon from "@mui/icons-material/Chat";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import CloseIcon from "@mui/icons-material/Close";
import CheckIcon from "@mui/icons-material/Check";

import StatusIndicator from "../user/StatusIndicator";
import AddFriend from "./AddFriend";

import { Friend, FriendRequest as FriendRequestType } from "../../types/friend";
import { UserStatus } from "../../types/status";

import { useFriends } from "../../hooks/useFriends";
import { useStatus } from "../../hooks/useStatus";

enum FriendTab {
  ONLINE = "online",
  ALL = "all",
  PENDING = "pending",
  ADD_FRIEND = "add"
}

export default function FriendsList() {
  const [activeTab, setActiveTab] = useState<FriendTab>(FriendTab.ONLINE);

  const { friendStatuses } = useStatus();
  const getUserStatus = (userId: number) =>
    friendStatuses[userId] || UserStatus.OFFLINE;

  const {
    friends,
    incomingRequests,
    outgoingRequests,
    acceptFriend,
    rejectFriend,
    removeFriend
  } = useFriends();

  const isLoading =
    friends.isLoading ||
    incomingRequests.isLoading ||
    outgoingRequests.isLoading;

  const isError =
    friends.isError ||
    incomingRequests.isError ||
    outgoingRequests.isError;

  const allFriends = friends.data ?? [];
  const incoming = incomingRequests.data ?? [];
  console.log("Incoming Requests:", incoming);
  const outgoing = outgoingRequests.data ?? [];
  const visibleFriends =
    activeTab === FriendTab.ONLINE
      ? allFriends.filter(
          f => getUserStatus(f.id) === UserStatus.ONLINE
        )
      : allFriends;

  const handleTabChange = (_: React.SyntheticEvent, value: FriendTab) => {
    setActiveTab(value);
  };

  const openDM = (friendId: number) => {
    // navigate(`/channels/@me/${friendId}`);
    console.log("Open DM with", friendId);
  };

  const renderLoading = () => (
    <Box sx={{ p: 2 }}>
      {[1, 2, 3].map(i => (
        <Box key={i} sx={{ display: "flex", gap: 2, mb: 2 }}>
          <Skeleton variant="circular" width={32} height={32} />
          <Skeleton variant="text" width="60%" />
        </Box>
      ))}
    </Box>
  );

  const renderFriends = () => {

    console.log("Visible Friends:", visibleFriends);
    
    if (visibleFriends.length === 0) {
      return (
        <Box sx={{ p: 3, textAlign: "center", color: "white" }}>
          <Typography>
            {activeTab === FriendTab.ONLINE
              ? "No one is around to play with..."
              : "You don’t have any friends yet."}
          </Typography>
        </Box>
      );
    }

    return (
      <Box sx={{ p: 2 }}>
        <Typography sx={sectionTitle}>
          {activeTab === FriendTab.ONLINE ? "ONLINE" : "ALL FRIENDS"} —{" "}
          {visibleFriends.length}
        </Typography>

        {visibleFriends.map(friend => (
          <FriendItem
            key={friend.id}
            friend={friend}
            status={getUserStatus(friend.id)}
            onChat={() => openDM(friend.id)}
            onRemove={() => removeFriend.mutate(friend.id)}
          />
        ))}
      </Box>
    );
  };

  const renderPending = () => {
    if (incoming.length === 0 && outgoing.length === 0) {
      return (
        <Box sx={{ p: 3, textAlign: "center", color: "#96989d" }}>
          <Typography>No pending requests</Typography>
        </Box>
      );
    }

    return (
      <Box sx={{ p: 2 }}>
        {incoming.length > 0 && (
          <>
            <Typography sx={sectionTitle}>
              INCOMING — {incoming.length}
            </Typography>

            {incoming.map(req => (
              <Box key={req.id} sx={requestRow}>
                <Avatar>{req.senderUsername[0]}</Avatar>
                <Typography sx={{ flexGrow: 1 }}>
                  {req.senderUsername}
                </Typography>

                <IconButton
                  onClick={() => acceptFriend.mutate(req.id)}
                  color="success"
                >
                  <CheckIcon />
                </IconButton>

                <IconButton
                  onClick={() => rejectFriend.mutate(req.id)}
                  color="error"
                >
                  <CloseIcon />
                </IconButton>
              </Box>
            ))}
          </>
        )}

        {outgoing.length > 0 && (
          <>
            <Typography sx={{ ...sectionTitle, mt: 3 }}>
              OUTGOING — {outgoing.length}
            </Typography>

            {outgoing.map(req => (
              <Box key={req.id} sx={requestRow}>
                <Avatar>{req.receiverUsername[0]}</Avatar>
                <Typography sx={{ flexGrow: 1 }}>
                  {req.receiverUsername}
                </Typography>
                <Typography sx={{ color: "white" }}>
                  Pending
                </Typography>
              </Box>
            ))}
          </>
        )}
      </Box>
    );
  };

  const renderContent = () => {
    if (isLoading) return renderLoading();
    if (isError)
      return (
        <Box sx={{ p: 3, textAlign: "center", color: "#ed4245" }}>
          <Typography>Failed to load friends</Typography>
        </Box>
      );

    if (activeTab === FriendTab.ADD_FRIEND) return <AddFriend />;
    if (activeTab === FriendTab.PENDING) return renderPending();

    return renderFriends();
  };

  return (
    <Box sx={{ height: "100%", display: "flex",color:"white", flexDirection: "column" }}>
      <Box sx={header}>
        <PersonAddIcon fontSize="small" />
        <Typography fontWeight="bold">Friends</Typography>

        <Divider orientation="vertical" flexItem />

        <Tabs sx={{color:"white" }} value={activeTab} onChange={handleTabChange}>
          <Tab sx={{color:"white" }} label="Online" value={FriendTab.ONLINE} />
          <Tab sx={{color:"white" }} label="All" value={FriendTab.ALL} />
          <Tab sx={{color:"white" }}
            label={
              incoming.length > 0 ? (
                <Badge badgeContent={incoming.length} color="error">
                  Pending
                </Badge>
              ) : (
                "Pending"
              )
            }
            value={FriendTab.PENDING}
          />
          <Tab sx={{color:"white" }} label="Add Friend" value={FriendTab.ADD_FRIEND} />
        </Tabs>
      </Box>

      <Box sx={{ flex: 1, overflowY: "auto" }}>{renderContent()}</Box>
    </Box>
  );
}

/* ---------------- Sub Components ---------------- */

const FriendItem = ({
  friend,
  status,
  onChat,
  onRemove
}: {
  friend: Friend;
  status: UserStatus;
  onChat: () => void;
  onRemove: () => void;
}) => (
  <Box sx={friendRow}>
    <Box sx={{ position: "relative" }}>
      <Avatar  sx={avatar} src={friend.avatarUrl}>
        {friend.username[0].toUpperCase()}
       
      </Avatar>
      <StatusIndicator status={status} borderColor="#36393f" />
    </Box>

    <Typography sx={{ flexGrow: 1 }}>
      {friend.username}
    </Typography>

    <Typography sx={{ color: "#b9bbbe" }}>
      {UserStatus[status]}
    </Typography>

    <IconButton onClick={onChat}>
      <ChatIcon fontSize="small" />
    </IconButton>

    <IconButton onClick={onRemove}>
      <MoreHorizIcon fontSize="small" />
    </IconButton>
  </Box>
);

/* ---------------- Styles ---------------- */

const header = {
  height: 48,
  px: 2,
  display: "flex",
  alignItems: "center",
  gap: 2,
  borderBottom: "1px solid #26282c"
};

const sectionTitle = {
  color: "white",
  fontSize: 12,
  fontWeight: "bold",
  mb: 2
};

const friendRow = {
  display: "flex",
  alignItems: "center",
  gap: 1.5,
  p: 1,
  borderRadius: 1,
  "&:hover": { bgcolor: "#32353b" }
};

const requestRow = {
  display: "flex",
  alignItems: "center",
  gap: 1.5,
  p: 1,
  borderRadius: 1,
  "&:hover": { bgcolor: "#32353b" }
};

const avatar ={ 
            width: 32, 
            height: 32, 
            bgcolor: '#ed4245',
            fontSize: '14px',
            cursor: 'pointer'
          }