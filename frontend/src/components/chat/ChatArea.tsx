import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Typography } from '@mui/material';
import { Message } from '../../types/message';
import messageService from '../../services/message.service';
import MessageList from './MessageList';
import MessageInput from './MessageInput';

interface ChatAreaProps {
    channel: {
        id: string;
        name: string;
    };
}

export default function ChatArea({ channel }: ChatAreaProps) {
    const [messages, setMessages] = useState<Message[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    
    const scrollToBottom = useCallback(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, []);

    useEffect(() => {
        if (channel.id) {
            loadMessages();
            const handleNewMessage = (message: Message) => {
                setMessages(prev => [...prev, message]);
                scrollToBottom();
            };
            
            messageService.subscribeToChannel(parseInt(channel.id), handleNewMessage);
            
            return () => {
                messageService.unsubscribeFromChannel(parseInt(channel.id), handleNewMessage);
            };
        }
    }, [channel.id, scrollToBottom]);

    const loadMessages = async () => {
        try {
            setIsLoading(true);
            const fetchedMessages = await messageService.getChannelMessages(parseInt(channel.id));
            setMessages(fetchedMessages);
            scrollToBottom();
        } catch (error) {
            console.error('Error loading messages:', error);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Box sx={{ 
            display: 'flex', 
            flexDirection: 'column', 
            height: '100vh',
            bgcolor: 'background.default' 
        }}>
            <Box sx={{ 
                p: 2, 
                borderBottom: 1, 
                borderColor: 'divider',
                bgcolor: 'background.paper' 
            }}>
                <Typography variant="h6"># {channel.name}</Typography>
            </Box>

            <MessageList 
                ref={messagesEndRef}
                messages={messages}
                isLoading={isLoading}
            />

            <MessageInput 
                channelId={channel.id}
                channelName={channel.name}
            />
        </Box>
    );
}