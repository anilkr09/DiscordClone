import { usePresence } from "../providers/PresenceProvider";
import { useFriendStatus } from "../providers/FriendStatusProvider";

export const useStatus = () => {
  const {
    status,
    updateCustomStatus,
    clearCustomStatus
  } = usePresence();

  const { friendStatuses, getStatus } = useFriendStatus();

  return {
    status,
    updateCustomStatus,
    clearCustomStatus,
    friendStatuses,
    getFriendStatus: getStatus,
  };
};
