// import { useState, useEffect } from 'react';
// import { useWebSocketTopic } from '../services/WebSocketProvider';
// import { UserStatus } from '../types/status';

// interface StatusUpdate {
//   userId: number;
//   status: UserStatus;
// }

// interface FriendStatusMap {
//   [userId: number]: UserStatus;
// }

// export const useFriendStatus = (initialStatuses: FriendStatusMap = {}) => {
//   const [friendStatuses, setFriendStatuses] = useState<FriendStatusMap>(initialStatuses);
//   const { messages, isConnected } = useWebSocketTopic('/topic/status');

//   useEffect(() => {
//     if (messages.length > 0) {
//       const latestMessage = messages[messages.length - 1] as StatusUpdate;
//       setFriendStatuses(prev => ({
//         ...prev,
//         [latestMessage.userId]: latestMessage.status
//       }));
//     }
//   }, [messages]);

//   const getStatus = (userId: number): UserStatus => {
//     return friendStatuses[userId] || UserStatus.OFFLINE;
//   };

//   return {
//     getStatus,
//     isConnected
//   };
// }; 