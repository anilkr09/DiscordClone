import { forwardRef } from 'react';
import { Box, Typography, Avatar, Paper } from '@mui/material';
import { Message } from '../../types/message';
import { useAuth } from '../../providers/AuthProvider'; 
interface MessageListProps {
    messages: Message[];
    isLoading: boolean;
}

const MessageList = forwardRef<HTMLDivElement, MessageListProps>(({ messages, isLoading }, ref) => {
    const { username } = useAuth(); // Get current user's username from Auth context
    console.log("current message list size: " + messages.length);

    return (
        <Box sx={{ 
            flex: 1, 
            overflow: 'auto', 
            p: 2,
            display: 'flex',
            flexDirection: 'column',
            gap: 2 
        }}>
            {isLoading ? (
                <Typography align="center">Loading messages...</Typography>
            ) : (
                
                messages.map((message) => (
                    <Paper 
                        key={message.id} 
                        elevation={0} 
                        sx={{ 
                            p: 2, 
                            bgcolor: 'background.paper',
                            '&:hover': { bgcolor: 'action.hover' }
                        }}
                    >
                        <Box sx={{ display: 'flex', gap: 2,justifyContent: message.author.username === username ? 'flex-end' : 'flex-start', width: '100%'}}>
                            
                             <Avatar 
                                      src={message.author?.avatarUrl || ""}
                                      sx={{ 
                                        width: 32, 
                                        height: 32, 
                                        bgcolor: '#ed4245',
                                        fontSize: '14px',
                                        cursor: 'pointer'
                                      }}
                                    >
                                      {message.author.username.charAt(0).toUpperCase() || "?"}
                                    </Avatar>
                            <Box sx={{ maxWidth: '70%' }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    <Typography variant="subtitle1" fontWeight="bold">
                                         {message.author.username === username
    ? "You"
    : message.author.username}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                        {new Date(message.timestamp).toLocaleString()}
                                    </Typography>
                                </Box>
                                <Typography>{message.content}</Typography>
                            </Box>
                        </Box>
                    </Paper>
                ))
            
            )}
            <div ref={ref} />
        </Box>
    );
});

MessageList.displayName = 'MessageList';

export default MessageList;