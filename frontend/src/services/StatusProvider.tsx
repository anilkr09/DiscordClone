import React, { createContext, useContext, useEffect, useState, useRef, useMemo } from "react";
import { UserStatus } from "../types/status";
import { useWebSocketTopic } from "./WebSocketProvider";
import api from "./api";
import { debounce } from "lodash";

import { useAuth } from "./AuthProvider";

// Define Context Type
interface StatusContextType {
    status: UserStatus;
    setStatus: (status: UserStatus) => void;
    customStatus: CustomStatus;
    updateCustomStatus: (status: UserStatus) => void;
    friendStatuses: FriendStatusMap;
    isInitialized:boolean;
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
    const statusRef = useRef(status);

    const { sendMessage, connected } = useWebSocketTopic("/app/status");
    const { messages, connected: connected2 } = useWebSocketTopic("/topic/status");
    const [friendStatuses, setFriendStatuses] = useState<FriendStatusMap>({});
    const { id ,isLoggedIn} = useAuth();
    const customStatusRef = useRef(customStatus);

   useEffect(()=>{
    console.log("friends status change" + JSON.stringify(friendStatuses));
   },[friendStatuses])
    // Initialize status from server
    useEffect(() => {
        if (!isLoggedIn || !connected || isInitialized) return;

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
    }, [isLoggedIn, connected, isInitialized]);

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
        customStatusRef.current = customStatus; // Keep ref in sync

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
        console.log("friendStatuses updated:", JSON.stringify(friendStatuses));
      }, [friendStatuses]);
      
    useEffect(() => {
        if (!isLoggedIn) return;

        const fetchStatuses = async () => {
            

            try {
                const response = await getAllStatus(); // Assume getStatuses() returns a Promise<StatusUpdate[]>
                const statusMap: FriendStatusMap = {};

                response.forEach((st: StatusUpdate,index) => {
                    console.log("status----map --"+index+" -"+st.userId+" -"+st.status);
                    statusMap[st.userId] = st.status;
                });

                console.log("Initial friends statusMap :", JSON.stringify(statusMap));

                setFriendStatuses(statusMap);
                // console.log("friend statuses inside----"+JSON.stringify(friendStatuses));

            } catch (error) {
                console.error("Error fetching statuses:", error);
            }
        };
        fetchStatuses();

    }, [isLoggedIn]);

    const getStatus = (userId: number): UserStatus => {
        return friendStatuses?.[userId] || UserStatus.OFFLINE;
    };


    useEffect(() => {
        if (!id || !connected) return;
    
        // Debounce the resetIdleTimer function for all events
        const debouncedResetIdleTimer = debounce(resetIdleTimer, 3000); // Adjust delay as needed
    
        // Attach all event listeners with debounced function
        window.addEventListener("mousemove", debouncedResetIdleTimer);
        window.addEventListener("keydown", debouncedResetIdleTimer);
        window.addEventListener("click", debouncedResetIdleTimer);
        window.addEventListener("scroll", debouncedResetIdleTimer);
    
        // Start idle timer on mount
        startIdleTimer();
    
        return () => {
            // Clean up all event listeners
            window.removeEventListener("mousemove", debouncedResetIdleTimer);
            window.removeEventListener("keydown", debouncedResetIdleTimer);
            window.removeEventListener("click", debouncedResetIdleTimer);
            window.removeEventListener("scroll", debouncedResetIdleTimer);
    
            if (idleTimer.current) {
                clearTimeout(idleTimer.current);
            }
    
            // Cancel any pending debounced calls
            debouncedResetIdleTimer.cancel();
        };
    }, [id, connected]);
    

    const startIdleTimer = () => {
        // console.log("starting idle timer");
        
        if (idleTimer.current) {
            clearTimeout(idleTimer.current);
        }
        
        idleTimer.current = setTimeout(() => {
            console.log("idle timer expired, setting status to idle");
            console.log("from idle time  customstatus ", customStatus.status, "currentStatus", status);
            if (
                statusRef.current === UserStatus.ONLINE &&
                customStatusRef.current.status === UserStatus.ONLINE
              ) {
                console.log(
                  "set idle -----true custom status - ",
                  customStatusRef.current.status
                );
                setStatus(UserStatus.IDLE);
              }
              
        }, 60 * 1000); // 5 seconds
    };

    const getAllStatus = async () => {
        try {
            const response = await api.get("/users/status");
            console.log( "initial friends statuses-"+JSON.stringify(response.data));
            return response.data;
        } catch (error) {
            console.error("Error fetching statuses:", error);
            return null;
        }
    };

    const resetIdleTimer = () => {
        // Use ref for immediate access to current status
        
        console.log("resttimer"+"customStatusRef"+customStatusRef.current.status+"statusref"+statusRef.current );
        // Only update if not already ONLINE
        if (statusRef.current != UserStatus.ONLINE && customStatusRef.current.status == UserStatus.ONLINE) {
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
        
        if (connected && newStatus) {
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
        friendStatuses,
        isInitialized
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
    const { status, isInitialized,setStatus,customStatus, updateCustomStatus, friendStatuses } = useStatusContext();
    return { status,isInitialized, customStatus, updateCustomStatus ,friendStatuses};
};