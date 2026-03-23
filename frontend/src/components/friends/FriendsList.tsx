import { useState } from "react";
import {
  Box, Typography, Avatar, IconButton,
  Tabs, Tab, Badge, Skeleton, Divider, useMediaQuery, useTheme
} from "@mui/material";
import ChatIcon from "@mui/icons-material/Chat";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import CloseIcon from "@mui/icons-material/Close";
import CheckIcon from "@mui/icons-material/Check";
import StatusIndicator from "../user/StatusIndicator";
import AddFriend from "./AddFriend";
import { Friend } from "../../types/friend";
import { UserStatus } from "../../types/status";
import { useFriends } from "../../hooks/useFriends";
import { useStatus } from "../../hooks/useStatus";
import { useNavigate } from "react-router-dom";

enum FriendTab {
  ONLINE = "online",
  ALL = "all",
  PENDING = "pending",
  ADD_FRIEND = "add",
}

export default function FriendsList() {
  const [activeTab, setActiveTab] = useState<FriendTab>(FriendTab.ONLINE);
  const { friendStatuses } = useStatus();
  const navigate = useNavigate();
  const theme = useTheme();
  const isXs = useMediaQuery(theme.breakpoints.down('sm'));

  const getUserStatus = (userId: number) =>
    friendStatuses[userId] || UserStatus.OFFLINE;

  const { friends, incomingRequests, outgoingRequests, acceptFriend, rejectFriend, removeFriend } = useFriends();

  const isLoading = friends.isLoading || incomingRequests.isLoading || outgoingRequests.isLoading;
  const isError = friends.isError || incomingRequests.isError || outgoingRequests.isError;

  const allFriends = friends.data ?? [];
  const incoming = incomingRequests.data ?? [];
  const outgoing = outgoingRequests.data ?? [];

  const visibleFriends =
    activeTab === FriendTab.ONLINE
      ? allFriends.filter(f => getUserStatus(f.id) === UserStatus.ONLINE)
      : allFriends;

  const openDM = (friendId: number) => navigate(`/channels/@me/${friendId}`);

  const renderLoading = () => (
    <Box sx={{ p: 2 }}>
      {[1, 2, 3].map(i => (
        <Box key={i} sx={{ display: "flex", gap: 2, mb: 2, alignItems: 'center' }}>
          <Skeleton variant="circular" width={32} height={32} />
          <Skeleton variant="text" width="60%" />
        </Box>
      ))}
    </Box>
  );

  const renderFriends = () => {
    if (visibleFriends.length === 0) {
      return (
        <Box sx={{ p: 3, textAlign: "center", color: "#949ba4" }}>
          <Typography sx={{ fontSize: 14 }}>
            {activeTab === FriendTab.ONLINE
              ? "No one is around to play with..."
              : "You don't have any friends yet."}
          </Typography>
        </Box>
      );
    }

    return (
      <Box sx={{ px: { xs: 1, sm: 2 }, py: 1 }}>
        <Typography sx={sectionTitle}>
          {activeTab === FriendTab.ONLINE ? "ONLINE" : "ALL FRIENDS"} — {visibleFriends.length}
        </Typography>
        {visibleFriends.map(friend => (
          <FriendItem
            key={friend.id}
            friend={friend}
            status={getUserStatus(friend.id)}
            onChat={() => openDM(friend.id)}
            onRemove={() => removeFriend.mutate(friend.id)}
            compact={isXs}
          />
        ))}
      </Box>
    );
  };

  const renderPending = () => {
    if (incoming.length === 0 && outgoing.length === 0) {
      return (
        <Box sx={{ p: 3, textAlign: "center", color: "#949ba4" }}>
          <Typography sx={{ fontSize: 14 }}>No pending requests</Typography>
        </Box>
      );
    }

    return (
      <Box sx={{ px: { xs: 1, sm: 2 }, py: 1 }}>
        {incoming.length > 0 && (
          <>
            <Typography sx={sectionTitle}>INCOMING — {incoming.length}</Typography>
            {incoming.map(req => (
              <Box key={req.id} sx={requestRow}>
                <Avatar sx={{ width: 32, height: 32, bgcolor: '#5865f2', fontSize: 13 }}>
                  {req.senderUsername[0]}
                </Avatar>
                <Typography sx={{ flexGrow: 1, fontSize: 14, color: '#f2f3f5' }}>
                  {req.senderUsername}
                </Typography>
                <IconButton size="small" onClick={() => acceptFriend.mutate(req.id)} sx={{ color: '#23a55a' }}>
                  <CheckIcon fontSize="small" />
                </IconButton>
                <IconButton size="small" onClick={() => rejectFriend.mutate(req.id)} sx={{ color: '#f23f43' }}>
                  <CloseIcon fontSize="small" />
                </IconButton>
              </Box>
            ))}
          </>
        )}

        {outgoing.length > 0 && (
          <>
            <Typography sx={{ ...sectionTitle, mt: 3 }}>OUTGOING — {outgoing.length}</Typography>
            {outgoing.map(req => (
              <Box key={req.id} sx={requestRow}>
                <Avatar sx={{ width: 32, height: 32, bgcolor: '#5865f2', fontSize: 13 }}>
                  {req.receiverUsername[0]}
                </Avatar>
                <Typography sx={{ flexGrow: 1, fontSize: 14, color: '#f2f3f5' }}>
                  {req.receiverUsername}
                </Typography>
                <Typography sx={{ color: '#949ba4', fontSize: 13 }}>Pending</Typography>
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
        <Box sx={{ p: 3, textAlign: "center", color: "#f23f43" }}>
          <Typography sx={{ fontSize: 14 }}>Failed to load friends</Typography>
        </Box>
      );
    if (activeTab === FriendTab.ADD_FRIEND) return <AddFriend />;
    if (activeTab === FriendTab.PENDING) return renderPending();
    return renderFriends();
  };

  return (
    <Box sx={{ height: "100%", display: "flex", flexDirection: "column", color: "#f2f3f5" }}>

      {/* Header with tabs */}
      <Box sx={{
        height: 48,
        flexShrink: 0,
        px: { xs: 1, sm: 2 },
        display: "flex",
        alignItems: "center",
        gap: { xs: 1, sm: 2 },
        borderBottom: "1px solid #1e1f22",
        overflowX: 'auto',
      }}>
        <Typography fontWeight="bold" sx={{ flexShrink: 0, fontSize: { xs: 13, sm: 15 } }}>
          Friends
        </Typography>

        <Divider orientation="vertical" flexItem sx={{ bgcolor: '#3f4147' }} />

        <Tabs
          value={activeTab}
          onChange={(_, v) => setActiveTab(v)}
          sx={{
            minHeight: 'unset',
            '& .MuiTab-root': {
              color: '#b5bac1',
              minHeight: 'unset',
              py: 0.5,
              px: { xs: 1, sm: 1.5 },
              fontSize: { xs: 12, sm: 13 },
              textTransform: 'none',
              '&:hover': { color: '#f2f3f5', bgcolor: '#35373c', borderRadius: 1 },
            },
            '& .Mui-selected': { color: '#f2f3f5 !important' },
            '& .MuiTabs-indicator': { bgcolor: '#5865f2' },
          }}
        >
          <Tab label="Online" value={FriendTab.ONLINE} />
          <Tab label="All" value={FriendTab.ALL} />
          <Tab
            label={
              incoming.length > 0
                ? <Badge badgeContent={incoming.length} color="error">Pending</Badge>
                : "Pending"
            }
            value={FriendTab.PENDING}
          />
          {/* Hide "Add Friend" text on xs, show icon */}
          <Tab
            label={isXs ? <PersonAddIcon sx={{ fontSize: 16 }} /> : "Add Friend"}
            value={FriendTab.ADD_FRIEND}
          />
        </Tabs>
      </Box>

      {/* Content */}
      <Box sx={{ flex: 1, minHeight: 0, overflowY: "auto" }}>
        {renderContent()}
      </Box>
    </Box>
  );
}

/* ── Sub-component ── */
const FriendItem = ({
  friend, status, onChat, onRemove, compact
}: {
  friend: Friend; status: UserStatus;
  onChat: () => void; onRemove: () => void; compact?: boolean;
}) => (
  <Box sx={friendRow}>
    <Box sx={{ position: "relative", flexShrink: 0 }}>
      <Avatar
        src={friend.avatarUrl}
        sx={{ width: 32, height: 32, bgcolor: '#ed4245', fontSize: 13 }}
      >
        {friend.username[0].toUpperCase()}
      </Avatar>
      <StatusIndicator status={status} borderColor="#313338" />
    </Box>

    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography sx={{ fontSize: 14, color: '#f2f3f5', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
        {friend.username}
      </Typography>
      {!compact && (
        <Typography sx={{ fontSize: 12, color: '#949ba4' }}>
          {UserStatus[status]}
        </Typography>
      )}
    </Box>

    <IconButton size="small" onClick={onChat} sx={{ color: '#b5bac1', '&:hover': { color: '#f2f3f5' } }}>
      <ChatIcon fontSize="small" />
    </IconButton>
    <IconButton size="small" onClick={onRemove} sx={{ color: '#b5bac1', '&:hover': { color: '#f2f3f5' } }}>
      <MoreHorizIcon fontSize="small" />
    </IconButton>
  </Box>
);

/* ── Styles ── */
const sectionTitle = {
  color: "#949ba4",
  fontSize: 11,
  fontWeight: 700,
  letterSpacing: '.05em',
  mb: 1,
  textTransform: 'uppercase',
};

const friendRow = {
  display: "flex",
  alignItems: "center",
  gap: 1.5,
  p: 1,
  borderRadius: 1,
  cursor: 'pointer',
  transition: 'background .1s',
  borderTop: '1px solid transparent',
  '&:hover': { bgcolor: '#35373c', borderColor: '#2e3035' },
};

const requestRow = {
  display: "flex",
  alignItems: "center",
  gap: 1.5,
  p: 1,
  borderRadius: 1,
  '&:hover': { bgcolor: '#35373c' },
};