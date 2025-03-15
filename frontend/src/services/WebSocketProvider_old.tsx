// import React, { createContext, useContext, useEffect, useState, useCallback, ReactNode } from 'react';
// import { Client, StompSubscription } from '@stomp/stompjs';
// import SockJS from 'sockjs-client';

// interface Subscription {
//   subscription: StompSubscription;
//   count: number;
// }

// interface MessageStore {
//   [key: string]: any[];
// }

// interface SubscriptionStore {
//   [key: string]: Subscription;
// }

// interface WebSocketContextValue {
//   connected: boolean;
//   subscribe: (topic: string) => StompSubscription | null;
//   unsubscribe: (topic: string) => void;
//   publish: (destination: string, body: any, headers?: Record<string, string>) => boolean;
//   getMessages: (topic: string) => any[];
//   clearMessages: (topic: string) => void;
// }

// interface WebSocketProviderProps {
//   children: ReactNode;
//   url: string;
//   connectHeaders?: Record<string, string>;
//   reconnectDelay?: number;
//   heartbeatIncoming?: number;
//   heartbeatOutgoing?: number;
//   debug?: boolean;
// }

// const WebSocketContext = createContext<WebSocketContextValue | null>(null);

// export const WebSocketProvider: React.FC<WebSocketProviderProps> = ({
//   children,
//   url,
//   connectHeaders = {},
//   reconnectDelay = 5000,
//   heartbeatIncoming = 4000,
//   heartbeatOutgoing = 4000,
//   debug = false
// }) => {
//   const [client, setClient] = useState<Client | null>(null);
//   const [connected, setConnected] = useState<boolean>(false);
//   const [subscriptions, setSubscriptions] = useState<SubscriptionStore>({});
//   const [messageStore, setMessageStore] = useState<MessageStore>({});

//   useEffect(() => {
//     console.log("Initializing WebSocket connection...");
//     const accessToken = localStorage.getItem("accessToken");
//     const socketUrl = `ws://localhost:8082/ws?access_token=${accessToken}`;

//     const stompClient = new Client({
//       brokerURL: socketUrl,
//       connectHeaders: {
//         Authorization: "Bearer " + accessToken,
//       },
//       onConnect: () => {
//         console.log('STOMP client connected');
//         setConnected(true);
//       },
//       onDisconnect: () => {
//         console.log('STOMP client disconnected');
//         setConnected(false);
//       },
//       onStompError: (frame) => {
//         console.error("Broker error:", frame.headers["message"]);
//       },
//     });

//     stompClient.activate();
//     setClient(stompClient);

//     return () => {
//       console.log("Cleaning up WebSocket connection...");
//       stompClient.deactivate();
//       setConnected(false);
//     };
//   }, [url]);

//   const subscribeToTopic = useCallback((topic: string): StompSubscription | null => {
//     console.log(`Subscribing to topic: ${topic}`);
//     if (!client || !connected) return null;
//     if (subscriptions[topic]?.subscription) {
//       console.log(`Already subscribed to topic: ${topic}`);
//       return subscriptions[topic].subscription;
//     }
//     const subscription = client.subscribe(topic, (message) => {
//       console.log(`Received message on topic ${topic}:`, message.body);
//       try {
//         const payload = JSON.parse(message.body);
//         setMessageStore(prev => ({
//           ...prev,
//           [topic]: [...(prev[topic] || []), payload]
//         }));
//       } catch (e) {
//         console.error('Error parsing message:', e);
//       }
//     });
//     setSubscriptions(prev => ({
//       ...prev,
//       [topic]: { subscription, count: 1 }
//     }));
//     return subscription;
//   }, [client, connected, subscriptions]);

//   const unsubscribeFromTopic = useCallback((topic: string): void => {
//     console.log(`Unsubscribing from topic: ${topic}`);
//     if (!subscriptions[topic]) return;
//     if (subscriptions[topic].count > 1) {
//       setSubscriptions(prev => ({
//         ...prev,
//         [topic]: { ...prev[topic], count: prev[topic].count - 1 }
//       }));
//     } else {
//       subscriptions[topic].subscription.unsubscribe();
//       setSubscriptions(prev => {
//         const newSubs = { ...prev };
//         delete newSubs[topic];
//         return newSubs;
//       });
//     }
//   }, [subscriptions]);

//   const publish = useCallback((destination: string, body: any, headers: Record<string, string> = {}): boolean => {
//     console.log(`Publishing message to ${destination}`);
//     if (!client || !connected) {
//       console.warn('Cannot publish: client not connected');
//       return false;
//     }
//     try {
//       client.publish({
//         destination,
//         body: typeof body === 'string' ? body : JSON.stringify(body),
//         headers
//       });
//       return true;
//     } catch (error) {
//       console.error('Error publishing message:', error);
//       return false;
//     }
//   }, [client, connected]);

//   const getMessages = useCallback((topic: string): any[] => {
//     console.log(`Fetching messages for topic: ${topic}`);
//     return messageStore[topic] || [];
//   }, [messageStore]);

//   const clearMessages = useCallback((topic: string): void => {
//     console.log(`Clearing messages for topic: ${topic}`);
//     setMessageStore(prev => ({
//       ...prev,
//       [topic]: []
//     }));
//   }, []);

//   const contextValue: WebSocketContextValue = {
//     connected,
//     subscribe: subscribeToTopic,
//     unsubscribe: unsubscribeFromTopic,
//     publish,
//     getMessages,
//     clearMessages
//   };

//   return (
//     <WebSocketContext.Provider value={contextValue}>
//       {children}
//     </WebSocketContext.Provider>
//   );
// };

// export const useWebSocket = (): WebSocketContextValue => {
//   const context = useContext(WebSocketContext);
//   if (!context) {
//     throw new Error('useWebSocket must be used within a WebSocketProvider');
//   }
//   return context;
// };
// export const useWebSocketTopic = (topic: string) => {
//   const { 
//     connected, 
//     subscribe, 
//     unsubscribe, 
//     publish, 
//     getMessages,
//     clearMessages 
//   } = useWebSocket();

//   console.log(`useWebSocketTopic initialized for topic: ${topic}`);

//   // Auto-subscribe to the topic
//   useEffect(() => {
//     let subscription: StompSubscription | null = null;
    
//     if (connected) {
//       console.log(`Subscribing to topic: ${topic}`);
//       subscription = subscribe(topic);
//     }
    
//     // Cleanup: unsubscribe when component unmounts
//     return () => {
//       if (subscription) {
//         console.log(`Unsubscribing from topic: ${topic}`);
//         unsubscribe(topic);
//       }
//     };
//   }, [topic, connected, subscribe, unsubscribe]);

//   // Get messages for this specific topic
//   const messages = getMessages(topic);
//   console.log(`Messages for topic ${topic}:`, messages);

//   // Send message specifically to this topic
//   const sendMessage = useCallback((message: any, headers: Record<string, string> = {}) => {
//     console.log(`Publishing message to topic ${topic}:`, message);
//     return publish(topic, message, headers);
//   }, [topic, publish]);

//   // Clear messages for this topic
//   const clearTopicMessages = useCallback(() => {
//     console.log(`Clearing messages for topic: ${topic}`);
//     clearMessages(topic);
//   }, [topic, clearMessages]);

//   return { 
//     messages, 
//     sendMessage, 
//     clearMessages: clearTopicMessages, 
//     isConnected: connected 
//   };
// };

// import { Client, StompSubscription } from '@stomp/stompjs';
// import SockJS from 'sockjs-client';

// interface Subscription {
//   subscription: StompSubscription;
//   count: number;
// }

// interface MessageStore {
//   [key: string]: any[];
// }

// interface SubscriptionStore {
//   [key: string]: Subscription;
// }

// interface WebSocketContextValue {
//   connected: boolean;
//   subscribe: (topic: string) => StompSubscription | null;
//   unsubscribe: (topic: string) => void;
//   publish: (destination: string, body: any, headers?: Record<string, string>) => boolean;
//   getMessages: (topic: string) => any[];
//   clearMessages: (topic: string) => void;
// }

// interface WebSocketProviderProps {
//   children: ReactNode;
//   url: string;
//   connectHeaders?: Record<string, string>;
//   reconnectDelay?: number;
//   heartbeatIncoming?: number;
//   heartbeatOutgoing?: number;
//   debug?: boolean;
// }

// // Create context with a default value
// const WebSocketContext = createContext<WebSocketContextValue | null>(null);

// export const WebSocketProvider: React.FC<WebSocketProviderProps> = ({ 
//   children, 
//   url, 
//   connectHeaders = {},
//   reconnectDelay = 5000,
//   heartbeatIncoming = 4000,
//   heartbeatOutgoing = 4000,
//   debug = false
// }) => {
//   const [client, setClient] = useState<Client | null>(null);
//   const [connected, setConnected] = useState<boolean>(false);
//   const [subscriptions, setSubscriptions] = useState<SubscriptionStore>({});
//   const [messageStore, setMessageStore] = useState<MessageStore>({});

//   // Initialize STOMP client
//   useEffect(() => {

//     console.log("trying to connect");


    
//     // const token = localStorage.getItem('accessToken'); // Or fetch it from your auth service

//     // const connectUrl = `${url}?access_token=${token}`;

//     // const stompClient = new Client({
//     //   connectHeaders: {
//     //     Authorization: `Bearer ${token}`, // Add JWT token in headers
//     //   },
//     //   debug: debug ? console.log : () => {},
//     //   reconnectDelay,
//     //   heartbeatIncoming,
//     //   heartbeatOutgoing,
    
//     //   // Use SockJS as WebSocket fallback option
//     //   webSocketFactory: () => new SockJS(connectUrl),
    
//       // onConnect: () => {
//       //   console.log('STOMP client connected');
//       //   setConnected(true);
    
//       //   // Resubscribe to topics after reconnection
//       //   Object.keys(subscriptions).forEach(topic => {
//       //     if (subscriptions[topic]?.count > 0) {
//       //       subscribeToTopic(topic);
//       //     }
//       //   });
//       // },
    
//       // onDisconnect: () => {
//       //   console.log('STOMP client disconnected');
//       //   setConnected(false);
//       // },
    
//     //   onStompError: (frame) => {
//     //     console.error('STOMP protocol error:', frame.headers['message']);
//     //   }
//     // });
    
//     // // Activate the client
//     // // stompClient.activate();
    
//     // setClient(stompClient);
//     const accessToken = localStorage.getItem("accessToken");
//     const socketUrl = `ws://localhost:8082/ws?access_token=${accessToken}`; // ✅ Using ws://
    
//     const stompClient = new Client({
//       brokerURL: socketUrl, // ✅ Use brokerURL instead of webSocketFactory
//       connectHeaders: {
//         Authorization: "Bearer " + accessToken, // ✅ Now headers will work
//       },
//       onConnect: () => {
//         console.log('STOMP client connected');
//         setConnected(true);
    
//         // Resubscribe to topics after reconnection
//         Object.keys(subscriptions).forEach(topic => {
//           if (subscriptions[topic]?.count > 0) {
//             subscribeToTopic(topic);
//           }
//         });
//       },
//       onDisconnect: () => {
//         console.log('STOMP client disconnected');
//         setConnected(false);
//       },
//       onStompError: (frame) => {
//         console.error("🚨 Broker error:", frame.headers["message"]);
//       },
//     });
    
//     stompClient.activate();
//         setClient(stompClient);
// // 
   
//     // Cleanup function
//     return () => {
//       if (stompClient) {
//         Object.values(subscriptions).forEach(sub => {
//           if (sub.subscription) {
//             sub.subscription.unsubscribe();
//           }
//         });
//         stompClient.deactivate();
//         setConnected(false);
//       }
//     };





//   }, [url]); // eslint-disable-line react-hooks/exhaustive-deps

//   // Function to subscribe to a topic
//   const subscribeToTopic = useCallback((topic: string): StompSubscription | null => {
//     if (!client || !connected) return null;

//     // If already subscribed, just update reference count
//     if (subscriptions[topic]?.subscription) {
//       // setSubscriptions(prev => ({
//       //   ...prev,
//       //   [topic]: {
//       //     ...prev[topic],
//       //     count: prev[topic].count + 1
//       //   }
//       // }));

//       setSubscriptions(prev => ({
//         ...prev,
//         [topic]: {
//           ...prev[topic],  // ✅ If prev[topic] is undefined, default to an empty object
//           count: (prev[topic]?.count || 0) + 1 // ✅ Use optional chaining and default to 0
//         }
//       }));
      
//       return subscriptions[topic].subscription;
//     }

//     // Create new subscription
//     const subscription = client.subscribe(topic, (message) => {
//       try {
//         const payload = JSON.parse(message.body);
//         setMessageStore(prev => ({
//           ...prev,
//           [topic]: [...(prev[topic] || []), payload]
//         }));
//       } catch (e) {
//         console.error('Error parsing message:', e);
//       }
//     });

//     // Store subscription
//     setSubscriptions(prev => ({
//       ...prev,
//       [topic]: {
//         subscription,
//         count: 1
//       }
//     }));

//     // Initialize message store for this topic if it doesn't exist
//     setMessageStore(prev => ({
//       ...prev,
//       [topic]: prev[topic] || []
//     }));

//     return subscription;
//   }, [client, connected, subscriptions]);

//   // Function to unsubscribe from a topic
//   const unsubscribeFromTopic = useCallback((topic: string): void => {
//     if (!subscriptions[topic]) return;

//     // Decrease reference count
//     if (subscriptions[topic].count > 1) {
//       setSubscriptions(prev => ({
//         ...prev,
//         [topic]: {
//           ...prev[topic],
//           count: prev[topic].count - 1
//         }
//       }));
//     } else {
//       // If last subscriber, unsubscribe completely
//       if (subscriptions[topic].subscription) {
//         subscriptions[topic].subscription.unsubscribe();
//       }
      
//       // Remove subscription from state
//       setSubscriptions(prev => {
//         const newSubs = { ...prev };
//         delete newSubs[topic];
//         return newSubs;
//       });
//     }
//   }, [subscriptions]);

//   // Function to publish a message to a topic
//   const publish = useCallback((destination: string, body: any, headers: Record<string, string> = {}): boolean => {
//     if (!client || !connected) {
//       console.warn('Cannot publish: client not connected');
//       return false;
//     }

//     try {
//       client.publish({
//         destination,
//         body: typeof body === 'string' ? body : JSON.stringify(body),
//         headers
//       });
//       return true;
//     } catch (error) {
//       console.error('Error publishing message:', error);
//       return false;
//     }
//   }, [client, connected]);

//   // Function to get messages for a specific topic
//   const getMessages = useCallback((topic: string): any[] => {
//     return messageStore[topic] || [];
//   }, [messageStore]);

//   // Function to clear messages for a specific topic
//   const clearMessages = useCallback((topic: string): void => {
//     setMessageStore(prev => ({
//       ...prev,
//       [topic]: []
//     }));
//   }, []);

//   // Context value
//   const contextValue: WebSocketContextValue = {
//     connected,
//     subscribe: subscribeToTopic,
//     unsubscribe: unsubscribeFromTopic,
//     publish,
//     getMessages,
//     clearMessages
//   };

//   return (
//     <WebSocketContext.Provider value={contextValue}>
//       {children}
//     </WebSocketContext.Provider>
//   );
// };

// // // Custom hook to use the WebSocket context
// export const useWebSocket = (): WebSocketContextValue => {
//   const context = useContext(WebSocketContext);
//   if (!context) {
//     throw new Error('useWebSocket must be used within a WebSocketProvider');
//   }
//   return context;
// };

// // Topic-specific hook
// export const useWebSocketTopic = (topic: string) => {
//   const { 
//     connected, 
//     subscribe, 
//     unsubscribe, 
//     publish, 
//     getMessages,
//     clearMessages 
//   } = useWebSocket();
  
//   // Auto-subscribe to the topic
//   useEffect(() => {
//     let subscription: StompSubscription | null = null;
    
//     if (connected) {
//       subscription = subscribe(topic);
//     }
    
//     // Cleanup: unsubscribe when component unmounts
//     return () => {
//       if (subscription) {
//         unsubscribe(topic);
//       }
//     };
//   }, [topic, connected, subscribe, unsubscribe]);
  
//   // Get messages for this specific topic
//   const messages = getMessages(topic);
  
//   // Send message specifically to this topic
//   const sendMessage = useCallback((message: any, headers: Record<string, string> = {}) => {
//     return publish(topic, message, headers);
//   }, [topic, publish]);
  
//   // Clear messages for this topic
//   const clearTopicMessages = useCallback(() => {
//     clearMessages(topic);
//   }, [topic, clearMessages]);
  
//   return { 
//     messages, 
//     sendMessage, 
//     clearMessages: clearTopicMessages, 
//     isConnected: connected 
//   };
// }; 