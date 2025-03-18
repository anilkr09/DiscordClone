import { useWebSocket } from "./WebSocketProvider.tsx";
import { UserStatus } from "../types/status";
import api from "./api";
import { useAuth } from "./AuthProvider.tsx";

export const useStatusService = () => {

  const { stompClient, isConnected } = useWebSocket(); // WebSocket instance
  const { id } = useAuth();

  // Fetch user statuses via HTTP
 const getStatus = async () => {
    try {
      const response = await api.get("/users/status");
      console.log(response.data);
      return response.data;
    } catch (error) {
      console.error("Error fetching statuses:", error);
      return null;
    }
  };

  // Update user custom status (via HTTP or WebSocket)
   const updateCustomStatus = (status: UserStatus) => {
    console.log("updating status called--  ");
    if (isConnected && stompClient) {
      stompClient.publish({
        destination: "/app/status",
        body: JSON.stringify({ currentStatus:"ONLINE", customStatus: status }),
      });
    } else {
      // Fallback to HTTP if WebSocket is not available
      api.post("/users/status/update", { status })
        .then(() => console.log("Status updated via HTTP"))
        .catch((error) => console.error("HTTP update failed:", error));
    }
  };

  return { getStatus, updateCustomStatus };

}