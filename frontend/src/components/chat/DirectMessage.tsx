import { useParams } from "react-router-dom";
import ChatArea from "./ChatArea";
import { useLocation } from 'react-router-dom';

import { useEffect, useState } from 'react';
import api from "../../services/api"; // Adjust the import path as necessary

export default function DirectMessage() {
  const { friendId } = useParams();
const location = useLocation();
  const friendNameFromState = location.state?.name;

 
  const [channelId, setChannelId] = useState<null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchChannelId = async () => {
      if (!friendId) return;
      
      setIsLoading(true);
      try {
        const response = await api.post(`/servers/1/channels/dm/${friendId}`);
        console.log("Fetched DM channel ID:", response.data);
        setChannelId(response.data);
      } catch (error) {
        console.error('Error fetching channel ID:', error);
      } finally {
        setIsLoading(false);
      }
    };  

    fetchChannelId();
  }, [friendId]);

  if (isLoading) {
    return <div>Loading...</div>; // You can replace this with a spinner component
  }

  return (
    <ChatArea
      id={channelId||""}
      name={`Friend #${friendNameFromState}`} // Ideally fetch the friend's name
      isDM={true}     
    />
  );
}
