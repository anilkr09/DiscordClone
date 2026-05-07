import { useEffect, useRef, useCallback } from "react";
import { Box, Typography } from "@mui/material";
import TagIcon from "@mui/icons-material/Tag";
import ChatIcon from "@mui/icons-material/Chat";
import { useWebSocket } from "../../providers/WebSocketProvider";
import MessageList from "./MessageList";
import MessageInput from "./MessageInput";
import { registerGroupMessageSocket } from "../../websocket/message.socket";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { fetchMessages } from "../../store/messages/messages.thunks";

interface ChatAreaProps {
  id: string;
  name: string;
  isDM?: boolean;
}

export default function ChatArea({ id, name, isDM = false }: ChatAreaProps) {
  const { connected, client } = useWebSocket();
  if (!isDM && connected) registerGroupMessageSocket(client, id);

  const dispatch = useAppDispatch();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const channel = useAppSelector((s) => s.messages.byChannelId[id]);
  const messages = channel?.messages || [];
  const isLoading = !channel?.loaded;

  useEffect(() => {
    if (!channel?.loaded) dispatch(fetchMessages(id));
  }, [id]);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages.length]);

  return (
    <Box sx={{
      flex: 1,               // fills whatever width is left in the flex row
      minWidth: 0,           // prevents flex overflow
      display: "flex",
      flexDirection: "column",
      height: "100%",        // fills parent height (controlled by MainLayout)
      overflow: "hidden",
      bgcolor: "#313338",
    }}>

      {/* Header */}
      <Box sx={{
        height: 48,
        flexShrink: 0,
        px: { xs: 1.5, sm: 2 },
        display: "flex",
        alignItems: "center",
        gap: 1,
        borderBottom: "1px solid #1e1f22",
        bgcolor: "#313338",
      }}>
        {isDM
          ? <ChatIcon sx={{ fontSize: 18, color: "#80848e" }} />
          : <TagIcon  sx={{ fontSize: 18, color: "#80848e" }} />
        }
        <Typography sx={{
          fontSize: { xs: 13, sm: 15 },
          fontWeight: 600,
          color: "#f2f3f5",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}>
          {name}
        </Typography>
      </Box>

      {/* Messages — flex: 1 so it fills remaining height */}
      <MessageList
        ref={messagesEndRef}
        messages={messages}
        isLoading={isLoading}
      />

      {/* Input — pinned to bottom */}
      <MessageInput
        channelId={id}
        channelName={name}
        isDM={isDM}
        receiver={name.toLowerCase().trim()}
      />
    </Box>
  );
}