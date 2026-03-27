import { useEffect, useRef, useCallback } from "react";
import { Box, Typography } from "@mui/material";
import { useParams } from "react-router-dom";
import { useWebSocket } from "../../providers/WebSocketProvider"; 
import MessageList from "./MessageList";
import MessageInput from "./MessageInput";
import { registerGroupMessageSocket } from "../../websocket/message.socket";
import { useAppDispatch, useAppSelector } from "../../store/hooks";
import { fetchMessages } from "../../store/messages/messages.thunks";
import TagIcon from '@mui/icons-material/Tag';
interface ChatAreaProps {
  id: string;
  name: string;
  isDM?: boolean;
}

export default function ChatArea({ id, name, isDM = false }: ChatAreaProps) {
  const {connected,client} = useWebSocket();
  if(isDM==false)
  if(connected)
  registerGroupMessageSocket(client,id);
  const dispatch = useAppDispatch();
  const messagesEndRef = useRef<HTMLDivElement>(null);
        console.log("isDM inside chat area"+isDM);

  // 🔥 Redux: get channel messages
  const channel = useAppSelector(
    (s) => s.messages.byChannelId[id]
  );

  const messages = channel?.messages || [];
  const isLoading = !channel?.loaded;

  // 🔥 Load history ONLY ONCE per channel
  useEffect(() => {
    if (!channel?.loaded) {
      dispatch(fetchMessages(id));
    }
  }, [id]);

  // 🔥 Auto-scroll on new message
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages.length]);

  return (
    <Box
      sx={{
        width: "800px",
        display: "flex",
        flexDirection: "column",
        height: "100vh",
        bgcolor: "background.default",
      }}
    >
      {/* Header */}
      <Box
        sx={{
          p: 2,
          borderBottom: 1,
          borderColor: "divider",
          color: "white",
          bgcolor: "#62636484",
        }}
      >
        {/* <Typography variant="h6">
        </Typography> */}
        <Box sx={{ display: 'flex', alignItems: 'center',  flex: 1, px: 1 }}>
                      {/* <TagIcon sx={{ fontSize: 18, color: '#80848e' }} /> */}
                      <Typography sx={{ fontSize: 14, fontWeight: 600, color: '#f2f3f5' }}>
                                 {isDM ? `Friend: ${name}` : `Channel: ${name}`}

                      </Typography>
                    </Box>
      </Box>
      {/* Messages */}
      <MessageList
        ref={messagesEndRef}
        messages={messages}
        isLoading={isLoading}
      />

      {/* Input */}
      <MessageInput channelId={id} channelName={name} isDM={isDM} receiver={name.toLowerCase().trim()}/>
    </Box>
  );
}
