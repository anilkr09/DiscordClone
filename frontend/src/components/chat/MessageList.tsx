import { forwardRef } from 'react';
import { Box, Typography, Avatar, Skeleton } from '@mui/material';
import { Message } from '../../types/message';
import { useAuth } from '../../providers/AuthProvider';

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
}

const MessageList = forwardRef<HTMLDivElement, MessageListProps>(
  ({ messages, isLoading }, ref) => {
    const { username } = useAuth();

    return (
      <Box sx={{
        flex: 1,
        minHeight: 0,        // critical — lets flex child scroll instead of overflow
        overflowY: "auto",
        overflowX: "hidden",
        display: "flex",
        flexDirection: "column",
        px: { xs: 1, sm: 2 },
        py: 1,
        gap: 0.5,
        // Custom scrollbar
        "&::-webkit-scrollbar": { width: 8 },
        "&::-webkit-scrollbar-track": { bgcolor: "transparent" },
        "&::-webkit-scrollbar-thumb": {
          bgcolor: "#1e1f22",
          borderRadius: 4,
        },
      }}>

        {isLoading ? (
          // Skeleton loading state
          <Box sx={{ p: 1, display: "flex", flexDirection: "column", gap: 2 }}>
            {[1, 2, 3].map((i) => (
              <Box key={i} sx={{ display: "flex", gap: 1.5, alignItems: "flex-start" }}>
                <Skeleton variant="circular" width={36} height={36} sx={{ flexShrink: 0 }} />
                <Box sx={{ flex: 1 }}>
                  <Skeleton variant="text" width="20%" sx={{ mb: 0.5 }} />
                  <Skeleton variant="text" width="60%" />
                </Box>
              </Box>
            ))}
          </Box>
        ) : messages.length === 0 ? (
          <Box sx={{
            flex: 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}>
            <Typography sx={{ color: "#5c5f66", fontSize: 14 }}>
              No messages yet. Say hello!
            </Typography>
          </Box>
        ) : (
          messages.map((message) => {
            const isOwn = message.author.username === username;
            return (
              <Box
                key={message.id}
                sx={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: { xs: 1, sm: 1.5 },
                  px: { xs: 0.5, sm: 1 },
                  py: 0.75,
                  borderRadius: 1,
                  transition: "background .1s",
                  "&:hover": { bgcolor: "#2e3035" },
                }}
              >
                {/* Avatar */}
                <Avatar
                  src={message.author?.avatarUrl || ""}
                  sx={{
                    width: { xs: 28, sm: 36 },
                    height: { xs: 28, sm: 36 },
                    flexShrink: 0,
                    bgcolor: isOwn ? "#5865f2" : "#ed4245",
                    fontSize: { xs: 11, sm: 14 },
                    cursor: "pointer",
                    mt: 0.25,
                  }}
                >
                  {message.author.username.charAt(0).toUpperCase() || "?"}
                </Avatar>

                {/* Content */}
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  {/* Author + timestamp */}
                  <Box sx={{
                    display: "flex",
                    alignItems: "baseline",
                    gap: 1,
                    flexWrap: "wrap",
                  }}>
                    <Typography sx={{
                      fontSize: { xs: 13, sm: 14 },
                      fontWeight: 600,
                      color: isOwn ? "#c9cdfb" : "#f2f3f5",
                      lineHeight: 1.3,
                    }}>
                      {isOwn ? "You" : message.author.username}
                    </Typography>
                    <Typography sx={{
                      fontSize: { xs: 10, sm: 11 },
                      color: "#5c5f66",
                      lineHeight: 1.3,
                    }}>
                      {new Date(message.timestamp).toLocaleString()}
                    </Typography>
                  </Box>

                  {/* Message text */}
                  <Typography sx={{
                    fontSize: { xs: 13, sm: 14 },
                    color: "#dbdee1",
                    lineHeight: 1.5,
                    wordBreak: "break-word",   // wraps long URLs / words
                    whiteSpace: "pre-wrap",    // preserves newlines
                    mt: 0.25,
                  }}>
                    {message.content}
                  </Typography>
                </Box>
              </Box>
            );
          })
        )}

        {/* Scroll anchor */}
        <div ref={ref} />
      </Box>
    );
  }
);

MessageList.displayName = "MessageList";
export default MessageList;