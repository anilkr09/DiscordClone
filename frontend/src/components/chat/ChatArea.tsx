import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Typography } from '@mui/material';
import { Message } from '../../types/message';
import messageService from '../../services/message.service';
import MessageList from './MessageList';
import MessageInput from './MessageInput';

interface ChatAreaProps {
  id: string;
  name: string;
  isDM?: boolean;
}

export default function ChatArea({ id, name, isDM = false }: ChatAreaProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    if (id) {
      loadMessages();
      const handleNewMessage = (message: Message) => {
        setMessages(prev => [...prev, message]);
        scrollToBottom();
      };

      messageService.subscribeToChannel(parseInt(id), handleNewMessage, isDM);

      return () => {
        messageService.unsubscribeFromChannel(parseInt(id), handleNewMessage, isDM);
      };
    }
  }, [id, scrollToBottom, isDM]);

  const loadMessages = async () => {
    try {
      setIsLoading(true);
      const fetchedMessages = isDM
        ? await messageService.getDMHistory(parseInt(id))
        : await messageService.getChannelMessages(parseInt(id));
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
        <Typography variant="h6">
          {isDM ? `@${name}` : `# ${name}`}
        </Typography>
      </Box>

      <MessageList
        ref={messagesEndRef}
        messages={messages}
        isLoading={isLoading}
      />

      <MessageInput
        channelId={id}
        channelName={name}
        isDM={isDM}
      />
    </Box>
  );
}
