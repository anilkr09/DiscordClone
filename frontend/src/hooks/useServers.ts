import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import ServerService from "../services/server.service";
import { Server, ServerPayload } from "../types/server";

export function useServers() {
  const queryClient = useQueryClient();

  /* ---------------- Queries ---------------- */

  const servers = useQuery({
    queryKey: ["servers"],
    queryFn: () => ServerService.getUserServers(),
    initialData: []
  });

  /* ---------------- Mutations ---------------- */

  // Create server
  const createServer = useMutation({
    mutationFn: (server: ServerPayload) =>
      ServerService.createServer(server),

    onSuccess: (server) => {
      queryClient.setQueryData<Server[]>(["servers"], old => {
        if (!old) return [server];
        if (old.some(s => s.id === server.id)) return old;
        return [...old, server];
      });
    }
  });

  // Join server
  const joinServer = useMutation({
    mutationFn: (inviteCode: string) =>
      ServerService.joinServer(inviteCode),

    onSuccess: (server) => {
      queryClient.setQueryData<Server[]>(["servers"], old => {
        if (!old) return [server];
        if (old.some(s => s.id === server.id)) return old;
        return [...old, server];
      });
    }
  });

  // Leave server
  const leaveServer = useMutation({
    mutationFn: (serverId: number) =>
      ServerService.leaveServer(serverId),

    onSuccess: (_, serverId) => {
      queryClient.setQueryData<Server[]>(["servers"], old =>
        old ? old.filter(s => s.id !== serverId) : []
      );
    }
  });

  // Delete server (owner)
  const deleteServer = useMutation({
    mutationFn: (serverId: number) =>
      ServerService.deleteServer(serverId),

    onSuccess: (_, serverId) => {
      queryClient.setQueryData<Server[]>(["servers"], old =>
        old ? old.filter(s => s.id !== serverId) : []
      );
    }
  });

  /* ---------------- Public API ---------------- */

  return {
    servers,

    createServer,
    joinServer,
    leaveServer,
    deleteServer
  };
}