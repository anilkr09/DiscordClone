import React, { createContext, useContext, useEffect, useState, useRef, useMemo } from "react";
import { UserStatus } from "../types/status";
import { useWebSocketTopic } from "./WebSocketProvider";
import api from "./api";
import { useAuth } from "./AuthProvider";

// Define Context Type
interface StatusContextType {
    status: UserStatus;
    setStatus: (status: UserStatus) => void;
    customStatus: CustomStatus;
    updateCustomStatus: (status: UserStatus) => void;
    friendStatuses: FriendStatusMap;
}

interface CustomStatus {
  status: UserStatus;
  expiresAt: Date;
}

  interface StatusUpdate {
    userId: number;
    status: UserStatus;
  }


interface FriendStatusMap {
  [userId: number]: UserStatus;
}

// Create Context
const StatusContext = createContext<StatusContextType | undefined>(undefined);

// Provider Component
export const StatusProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [isInitialized, setIsInitialized] = useState(false);
    const [status, setStatus] = useState<UserStatus>(UserStatus.ONLINE); // Start with ONLINE
    const [customStatus, setCustomStatus] = useState<CustomStatus>({ 
        status: UserStatus.ONLINE, 
        expiresAt: new Date(new Date().getTime() + 24 * 60 * 60 * 1000) // Correct date calculation
    });
    const idleTimer = useRef<NodeJS.Timeout | null>(null);
    const statusRef = useRef<UserStatus>(UserStatus.ONLINE);

    const { sendMessage, connected } = useWebSocketTopic("/app/status");
    const { messages, connected: connected2 } = useWebSocketTopic("/topic/status");
    const [friendStatuses, setFriendStatuses] = useState<FriendStatusMap>({});
    const { id } = useAuth();

    // Initialize status from server
    useEffect(() => {
        if (!id || !connected || isInitialized) return;

        const initializeStatus = async () => {
            try {
                const { data } = await api.get(`/users/${id}/status`);
                const currentTime = new Date();
                const expiryTime = new Date(data.expiresAt);

                if (currentTime > expiryTime) {
                    setCustomStatus({
                        status: UserStatus.ONLINE,
                        expiresAt: new Date(currentTime.getTime() + 24 * 60 * 60 * 1000)
                    });
                    
                } else {
                    setCustomStatus({
                        status: data.customStatus,
                        expiresAt: new Date(data.expiresAt)
                    });
                  
                }
                setIsInitialized(true);
            } catch (error) {
                console.error("Error fetching initial status:", error);
                setIsInitialized(true);
            }
        };

        initializeStatus();
    }, [id, connected, isInitialized]);

    // Status sync effect
    useEffect(() => {
        if (!isInitialized) return;
        
        statusRef.current = status;
       

        // Only send updates when connected and initialized
        if (connected && id) {
            sendMessage({
                currentStatus: status
            });
        }
    }, [status, connected, id, isInitialized]);

    // Custom status effect
    useEffect(() => {
        if (!isInitialized) return;
        console.log("Status changed:", status);
        console.log("customStatus changed", customStatus.status, "currentStatus", status);
        if (customStatus.status !== status) {
            setStatus(customStatus.status);
        }
    }, [customStatus, isInitialized]);

    // Status expiry check
    useEffect(() => {
        const checkCustomStatusExpiry = () => {
            const currentTime = new Date();
            const expiryTime = new Date(customStatusRef.current.expiresAt);

            if (currentTime > expiryTime) {
                setCustomStatus({ 
                    status: UserStatus.ONLINE, 
                    expiresAt: new Date(currentTime.getTime() + 24 * 60 * 60 * 1000)
                });
            }
        };

        const intervalId = setInterval(checkCustomStatusExpiry, 60 * 1000);
        return () => clearInterval(intervalId);
    }, []);

    const customStatusRef = useRef(customStatus);

    useEffect(() => {
        customStatusRef.current = customStatus; // Keep ref in sync
    }, [customStatus]);

    useEffect(() => {
        if (!id) return;

        if (connected2 && messages.length > 0) {
            console.log("messages", messages);
            const latestMessage = messages[messages.length - 1] as StatusUpdate;
            setFriendStatuses(prev => ({
                ...prev,
                [latestMessage.userId]: latestMessage.status
            }));
        }
    }, [messages, connected2, id]);

    useEffect(() => {
        const fetchStatuses = async () => {
            try {
                const response = await getAllStatus(); // Assume getStatuses() returns a Promise<StatusUpdate[]>
                const statusMap: FriendStatusMap = {};

                response.forEach((st: StatusUpdate) => {
                    statusMap[st.userId] = st.status;
                });

                setFriendStatuses(statusMap);
            } catch (error) {
                console.error("Error fetching statuses:", error);
            }
        };
        fetchStatuses();
    }, [id]);

    const getStatus = (userId: number): UserStatus => {
        return friendStatuses?.[userId] || UserStatus.OFFLINE;
    };

    useEffect(() => {
        if (!id || !connected) return;

        // console.log("status provider mounted or connection established");
        
        // const fetchStatus = async () => {
        //     try {
        //         const { data } = await api.get(`/users/${id}/status`);
        //         console.log("fetched user status", data);
                
        //         const currentTime = new Date();
        //         const expiryTime = new Date(data.expiresAt);

        //         if (currentTime > expiryTime) {
        //             console.log("status expired, setting to online");
        //             setCustomStatus({
        //                 status: UserStatus.ONLINE,
        //                 expiresAt: new Date(currentTime.getTime() + 60 * 1000) // 1 minute from now
        //             });
        //         } else {
        //             console.log("status not expired, setting to", data.customStatus);
        //             setCustomStatus({
        //                 status: data.customStatus,
        //                 expiresAt: new Date(data.expiresAt)
        //             });
        //             setStatus(data.customStatus);
        //         }
        //     } catch (error) {
        //         console.error("Error fetching status:", error);
        //         setStatus(UserStatus.ONLINE);
        //     }
        // };

        // fetchStatus();

        // Set up activity listeners
        window.addEventListener("mousemove", resetIdleTimer);
        window.addEventListener("keydown", resetIdleTimer);
        window.addEventListener("click", resetIdleTimer);
        window.addEventListener("scroll", resetIdleTimer);

        // Start idle timer on mount
        startIdleTimer();

        return () => {
            window.removeEventListener("mousemove", resetIdleTimer);
            window.removeEventListener("keydown", resetIdleTimer);
            window.removeEventListener("click", resetIdleTimer);
            window.removeEventListener("scroll", resetIdleTimer);
            
            if (idleTimer.current) {
                clearTimeout(idleTimer.current);
            }
        };
    }, [id, connected]);

    const startIdleTimer = () => {
        // console.log("starting idle timer");
        
        if (idleTimer.current) {
            clearTimeout(idleTimer.current);
        }
        
        idleTimer.current = setTimeout(() => {
            console.log("idle timer expired, setting status to idle");
            
            if(customStatusRef.current.status === UserStatus.ONLINE && status != UserStatus.IDLE)
              {console.log("set idle -----true"+"custom status - "+ customStatus.status); setStatus(UserStatus.IDLE);}
        }, 5 * 1000); // 5 seconds
    };

    const getAllStatus = async () => {
        try {
            const response = await api.get("/users/status");
            console.log(response.data);
            return response.data;
        } catch (error) {
            console.error("Error fetching statuses:", error);
            return null;
        }
    };

    const resetIdleTimer = () => {
        // Use ref for immediate access to current status
        const currentStatus = statusRef.current;
        
        // Only update if not already ONLINE
        if (currentStatus == UserStatus.ONLINE && currentStatus != UserStatus.ONLINE) {
            setStatus(UserStatus.ONLINE);
        }
        
        // Always restart the idle timer
        startIdleTimer();
    };

    const updateCustomStatus = (newStatus: UserStatus) => {
        setCustomStatus({
            status: newStatus,
            expiresAt: new Date(new Date().getTime() + 24 *60 * 60 * 1000) // 1 day from now
        });
        
        if (connected ) {
            sendMessage({
                customStatus: newStatus
            });
        }
    };

    const contextValue = useMemo(() => ({ 
        status, 
        setStatus, 
        customStatus, 
        updateCustomStatus,
        friendStatuses
    }), [status, customStatus, friendStatuses ]);
    
    return (
        <StatusContext.Provider value={contextValue}>
            {children}
        </StatusContext.Provider>
    );
};

// Custom Hook to use StatusContext
export const useStatusContext = () => {
    const context = useContext(StatusContext);
    if (!context) {
        throw new Error("useStatusContext must be used within a StatusProvider");
    }
    return context;
};

export const useStatus = () => {
    const { status, setStatus, customStatus, updateCustomStatus, friendStatuses } = useStatusContext();
    return { status, customStatus, updateCustomStatus ,friendStatuses};
};