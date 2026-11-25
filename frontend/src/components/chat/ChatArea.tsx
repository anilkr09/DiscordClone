import { useEffect, useRef } from "react";
import { Box, Typography } from "@mui/material";
import TagIcon from "@mui/icons-material/Tag";
import ChatIcon from "@mui/icons-material/Chat";
import MessageList from "./MessageList";
import MessageInput from "./MessageInput";
import { useMessages } from "../../features/messages/useMessages";

interface ChatAreaProps {
  id: string;
  name: string;
  isDM?: boolean;
}

export default function ChatArea({ id, name, isDM = false }: ChatAreaProps) {
  const { messages, send, isLoading, hasOlder, loadingOlder, loadOlder } = useMessages(id);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const newestId = messages.at(-1)?.id;

  // Follow new messages, but stay put when older ones are loaded above.
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [newestId]);

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
        hasOlder={hasOlder}
        loadingOlder={loadingOlder}
        onLoadOlder={() => void loadOlder()}
      />

      {/* Input — pinned to bottom */}
      <MessageInput
        key={id}
        onSend={send}
        channelName={name}
        isDM={isDM}
      />
    </Box>
  );
}