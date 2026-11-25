import { useCallback } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, unwrap } from "../api/client";
import { useAuth } from "../providers/AuthProvider";
import { UserStatus } from "../types/status";

type StatusMap = Record<number, UserStatus>;

/** The user's own status and their friends', fetched once and then kept current by events. */
export const useStatus = () => {
  const { isLoggedIn, id } = useAuth();

  const { data } = useQuery({
    queryKey: ["presence"],
    enabled: isLoggedIn,
    staleTime: Infinity,
    queryFn: async (): Promise<StatusMap> => {
      const [friends, self] = await Promise.all([
        unwrap(api.GET("/api/users/friends/status")),
        unwrap(api.GET("/api/users/me/status")),
      ]);
      const map: StatusMap = {};
      friends.forEach((s) => (map[s.userId] = s.status as UserStatus));
      map[Number(id)] = self.status as UserStatus;
      return map;
    },
  });

  const friendStatuses: StatusMap = data ?? {};
  const getFriendStatus = (userId: number) => friendStatuses[userId] ?? UserStatus.OFFLINE;

  const updateCustomStatus = useCallback(async (status: UserStatus) => {
    await unwrap(api.POST("/api/users/status/custom", { params: { query: { status } } }));
  }, []);

  const clearCustomStatus = useCallback(async () => {
    await unwrap(api.DELETE("/api/users/status/custom"));
  }, []);

  return {
    status: friendStatuses[Number(id)] ?? UserStatus.OFFLINE,
    friendStatuses,
    getFriendStatus,
    updateCustomStatus,
    clearCustomStatus,
  };
};
