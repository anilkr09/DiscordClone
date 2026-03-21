import { useParams, useNavigate } from "react-router-dom";
import ChatArea from "./ChatArea";
import { useEffect, useState, useRef } from "react";
import { Friend } from "../../types/friend";
import api from "../../services/api";
import { useFriends } from "../../hooks/useFriends";
import { Box } from "@mui/material";
export default function DirectMessage() {
  const { friendId } = useParams();
  const navigate = useNavigate();

  const { friends } = useFriends();

  const [friend, setFriend] = useState<Friend | undefined>();
  const [channelId, setChannelId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // ✅ Prevent duplicate API calls (Strict Mode safe)
  // const hasFetched = useRef(false);

  useEffect(() => {
  if (!friendId) return;
  if (friends.isLoading || !friends.data || friends.data.length === 0) return;
  

  console.log("friend List", friends.data);

  // hasFetched.current = true;

  const fetchChannelId = async () => {
    setIsLoading(true);
    try {
      const response = await api.post(`/channels/1/dm/${friendId}`);
      setChannelId(response.data);

      const selectedFriend = friends.data.find(
        (f) => String(f.id) === String(friendId)
      );

      console.log("Selected friend:", selectedFriend);

      setFriend(selectedFriend);
    } catch (error) {
      navigate(`/channels/@me`);
    } finally {
      setIsLoading(false);
    }
  };

  fetchChannelId();
}, [friendId, friends.isLoading, friends.data , navigate]);
  // ✅ Safe utility
  const capitalizeFirst = (str?: string) =>
    str ? str.charAt(0).toUpperCase() + str.slice(1) : "";

  // ✅ Loading state
  if (isLoading || !channelId) {
    return <Box sx={{ flexGrow:1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><div>Loading...</div></Box>;
  }

  return (
    
     <Box component="main" sx={{ flex:3 }}>
    <ChatArea
      id={channelId}
      name={friend?.username ? capitalizeFirst(friend.username) : "Unknown User"}
      isDM={true}
    />
    </Box>
  );
}