import { useState, useRef, useEffect } from 'react';
import { Box, TextField, Button } from '@mui/material';
import { MessageRequest } from '../../types/message';
import { useWebSocketTopic } from '../../providers/WebSocketProvider';

interface MessageInputProps {
  channelId: string;
  channelName: string;
  isDM: boolean;
  receiver?: string;
}

export default function MessageInput({
  channelId,
  channelName,
  isDM,
  receiver
}: MessageInputProps) {
  const [message, setMessage] = useState('');
  const [isSending, setIsSending] = useState(false);

  const inputRef = useRef<HTMLInputElement | null>(null);

  const { sendMessage } = useWebSocketTopic("/app/chat.send");

  // ✅ Auto focus on mount / channel change
  useEffect(() => {
    // inputRef.current?.focus();
  }, [channelId]);

  // ✅ Extracted send logic
  const send = async () => {
    if (!message.trim() || isSending) return;

    try {
      setIsSending(true);

      const messageRequest: MessageRequest = {
        dm: isDM,
        content: message,
        channelId,
        receiver: isDM ? receiver : ""
      };

      sendMessage(messageRequest);
      setMessage('');
    } catch (error) {
      console.error('Error sending message:', error);
    } finally {
      setIsSending(false);
    }
  };

  // ✅ Form submit (button + Enter fallback)
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    send();
  };

  // ✅ Enter handling (Discord behavior)
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault(); // prevent newline
      send();
    }
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit}
      sx={{
        p: 2,
        borderTop: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper'
      }}
    >
      <Box sx={{ display: 'flex', gap: 1 }}>
        <TextField
          inputRef={inputRef}   // ✅ autofocus target
          fullWidth
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={`Message #${channelName}`}
          variant="outlined"
          size="small"
          multiline
          maxRows={4}
          disabled={isSending}
        />

        <Button
          type="submit"
          variant="contained"
          disabled={!message.trim() || isSending}
        >
          Send
        </Button>
      </Box>
    </Box>
  );
}