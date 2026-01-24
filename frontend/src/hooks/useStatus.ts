import { usePresence } from "../providers/PresenceProvider";
import { useFriendStatus } from "../providers/FriendStatusProvider";

export const useStatus = () => {
  const {
    status,
    setStatus,
    customStatus,
    updateCustomStatus,
    isInitialized,
  } = usePresence();

  const { friendStatuses, getStatus } = useFriendStatus();

  return {
    // own status
    status,
    setStatus,
    customStatus,
    updateCustomStatus,
    isInitialized,

    // friends
    friendStatuses,
    getFriendStatus: getStatus,
  };
};
