import { useParams } from "react-router-dom";
import ChatArea from "./ChatArea";

export default function DirectMessage() {
  const { friendId } = useParams();

  return (
    <ChatArea 
      id={friendId!} 
      name={`Friend #${friendId}`} // Ideally fetch the friend's name
      isDM={true} 
    />
  );
}
