import { forwardRef } from 'react';
import { Box, Typography, Avatar, Paper } from '@mui/material';
import { Message } from '../../types/message';

interface MessageListProps {
    messages: Message[];
    isLoading: boolean;
}

const MessageList = forwardRef<HTMLDivElement, MessageListProps>(({ messages, isLoading }, ref) => {

    console.log("current message list"+ JSON.stringify(messages));

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
                        <Box sx={{ display: 'flex', gap: 2 }}>
                            <Avatar 
                                src={message.author.avatarUrl} 
                                alt={message.author.username}
                            />
                            <Box sx={{ flex: 1 }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                    <Typography variant="subtitle1" fontWeight="bold">
                                        {message.author.username}
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