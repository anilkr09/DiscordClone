import { useParams, Navigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Box } from "@mui/material";
import ChatArea from "./ChatArea";
import api from "../../services/api";
import { useFriends } from "../../hooks/useFriends";

const Centered = ({ children }: { children: React.ReactNode }) => (
  <Box sx={{ flexGrow: 1, display: "flex", alignItems: "center", justifyContent: "center" }}>
    {children}
  </Box>
);

export default function DirectMessage() {
  const { friendId } = useParams();
  const { friends } = useFriends();
  const friend = friends.data?.find((f) => String(f.id) === String(friendId));

  const channel = useQuery({
    queryKey: ["dm-channel", friendId],
    queryFn: async () => String((await api.post<number>(`/dms/${friendId}`)).data),
    enabled: !!friend,
    staleTime: Infinity,
  });

  if (friends.isLoading) return <Centered>Loading...</Centered>;
  if (friends.isError) return <Centered>Could not load your friends.</Centered>;
  // The URL names someone who is not a friend (or is no longer one).
  if (!friend) return <Navigate to="/channels/@me" replace />;
  if (channel.isError) return <Centered>Could not open this conversation.</Centered>;
  if (!channel.data) return <Centered>Loading...</Centered>;

  const title = friend.username.charAt(0).toUpperCase() + friend.username.slice(1);

  return (
    <Box component="main" sx={{ flex: 3 }}>
      <ChatArea id={channel.data} name={title} isDM={true} receiver={friend.username} />
    </Box>
  );
}
