import { useState, useEffect, useRef, useCallback } from 'react';
import { Box, Typography } from '@mui/material';
import { Message } from '../../types/message';
import messageService from '../../services/message.service';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import {useAuth} from '../../services/AuthProvider';
import { useWebSocketTopic } from '../../services/WebSocketProvider';
interface ChatAreaProps {
  id: string;
  name: string;
  isDM?: boolean;
}

export default function ChatArea({ id, name, isDM = false }: ChatAreaProps) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
const { messages: newMessage ,connected} = useWebSocketTopic("/topic/channels/1/messages");

useEffect(()=>{
  console.log("--- ---  - - --  new msg"+JSON.stringify (newMessage));
},[newMessage])

 const {id:userId}  = useAuth();

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  // useEffect( () =>   {
  //   if (userId) {
  //     // loadMessages();
  //     console.log("new msg --------"+newMessage);
  //     const handleNewMessage = (message) => {
  //       setMessages(prev => [...prev, message]);
  //       scrollToBottom();
  //     };
  //     handleNewMessage(newMessage);


  //     return () => {
  //     };
  //   }
  // }, [userId, scrollToBottom, isDM,newMessage]);
  useEffect(() => {
    if (!newMessage) return;
  

    if (connected  && newMessage.length > 0) {
    const latestMessage = newMessage[newMessage.length - 1] as Message;
        
    setMessages(prev => [...prev, latestMessage]);
    }
    scrollToBottom();
  }, [connected,newMessage, scrollToBottom]);
  
  const loadMessages = async () => {
    try {
      setIsLoading(true);
      const fetchedMessages = 
      
       await messageService.getChannelMessages(parseInt(id));
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
      width:'800px',
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
      />
    </Box>
  );
}
