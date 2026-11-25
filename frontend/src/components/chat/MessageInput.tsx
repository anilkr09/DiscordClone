import { useState, useRef, useEffect } from 'react';
import { Box, TextField, IconButton, Tooltip } from '@mui/material';
import SendIcon from '@mui/icons-material/Send';

interface MessageInputProps {
  /** Returns false if nothing was sent, in which case the text is kept. */
  onSend: (content: string) => boolean;
  channelName: string;
  isDM: boolean;
}

export default function MessageInput({ onSend, channelName, isDM }: MessageInputProps) {
  const [message, setMessage] = useState('');
  const inputRef = useRef<HTMLInputElement | null>(null);

  // Focus input when the channel changes (the component is keyed by channel)
  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  const send = () => {
    if (message.trim() && onSend(message)) setMessage('');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    send();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <Box
      component="form"
      onSubmit={handleSubmit}
      sx={{
        flexShrink: 0,                   // never shrinks — always pinned to bottom
        px: { xs: 1, sm: 2 },
        py: { xs: 1, sm: 1.5 },
        bgcolor: '#313338',
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'flex-end',
        gap: 1,
        bgcolor: '#383a40',
        borderRadius: 2,
        px: 1.5,
        py: 0.5,
      }}>
        <TextField
          inputRef={inputRef}
          fullWidth
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={`Message ${isDM ? '' : '#'}${channelName}`}
          variant="standard"
          multiline
          maxRows={6}
          inputProps={{ maxLength: 4000, "aria-label": `Message ${channelName}` }}
          sx={{
            '& .MuiInputBase-root': {
              color: '#dbdee1',
              fontSize: { xs: 13, sm: 14 },
              py: 1,
            },
            '& .MuiInput-underline:before': { display: 'none' },
            '& .MuiInput-underline:after':  { display: 'none' },
            '& .MuiInputBase-input::placeholder': { color: '#6d6f78', opacity: 1 },
          }}
        />

        {/* Send button — icon only, appears when there's text */}
        <Tooltip title="Send" placement="top">
          <span>
            <IconButton
              type="submit"
              aria-label="Send message"
              disabled={!message.trim()}
              size="small"
              sx={{
                mb: 0.5,
                color: message.trim() ? '#5865f2' : '#4e5058',
                transition: 'color .15s',
                '&:hover': { color: '#7289da', bgcolor: 'transparent' },
                '&.Mui-disabled': { color: '#4e5058' },
              }}
            >
              <SendIcon sx={{ fontSize: { xs: 18, sm: 20 } }} />
            </IconButton>
          </span>
        </Tooltip>
      </Box>
    </Box>
  );
}