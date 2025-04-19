import { useState } from 'react';
import { Box, TextField, Button } from '@mui/material';
import { MessageRequest } from '../../types/message';
import messageService from '../../services/message.service';
import { useWebSocketTopic } from '../../services/WebSocketProvider';
interface MessageInputProps {
    channelId: string;
    channelName: string;
}

export default function MessageInput({ channelId, channelName }: MessageInputProps) {
    const [message, setMessage] = useState('');
    const [isSending, setIsSending] = useState(false);

    const { sendMessage, connected } = useWebSocketTopic("/app/chat.send");
   

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!message.trim() || isSending) return;

        try {
            setIsSending(true);
            const messageRequest: MessageRequest = {
                content: message,
                channelId:"1"
            };
            
            sendMessage(messageRequest);
            setMessage('');
        } catch (error) {
            console.error('Error sending message:', error);
        } finally {
            setIsSending(false);
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
                    fullWidth
                    value={message}
                    onChange={(e) => setMessage(e.target.value)}
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