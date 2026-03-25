import { useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import channelService from "../services/channel.service";
import { useWebSocket } from "../providers/WebSocketProvider";

export const useChannels = (serverId: number) => {
  const queryClient = useQueryClient();
    const {connected,client} = useWebSocket();
   
  const queryKey = ["channels", serverId];

  // 📥 Fetch channels
  const {
    data: channels = [],
    isLoading,
    error,
  } = useQuery({
    queryKey,
    queryFn: () => channelService.getChannels(serverId),
    enabled: !!serverId,
  });

  // ➕ Create channel
  const createMutation = useMutation({
    mutationFn: channelService.createChannel,
    onSuccess: (newChannel) => {
      queryClient.setQueryData(queryKey, (old: any[] = []) => {
         if (old.some(c => c.id === newChannel.id)) return old;
        return [
        ...old,
        newChannel,
      ]});
    },
  });

  // ❌ Delete channel
  const deleteMutation = useMutation({
    mutationFn: channelService.deleteChannel,
    onSuccess: (_, channelId) => {
      queryClient.setQueryData(queryKey, (old: any[] = []) =>
        old.filter((c) => c.id !== channelId)
      );
    },
  });

  // 🔌 WebSocket event handler
  useEffect(() => {
    if (!connected) return;
    if (!client) return;
    const handler = (event: any) => {

      switch (event.type) {
        case "CHANNEL_CREATED":
          console.log("channel created event called");
          if (event.payload.serverId !== serverId) return;

          queryClient.setQueryData(queryKey, (old: any[] = []) => {
            
           
            return [
            ...old,
            event.payload,
          ]});
          break;

        case "CHANNEL_DELETED":
          if (event.payload.serverId !== serverId) return;

          queryClient.setQueryData(queryKey, (old: any[] = []) =>
            old.filter((c) => c.id !== event.payload.id)
          );
          break;

        case "CHANNEL_UPDATED":
          if (event.payload.serverId !== serverId) return;

          queryClient.setQueryData(queryKey, (old: any[] = []) =>
            old.map((c) =>
              c.id === event.payload.id ? event.payload : c
            )
          );
          break;
      }
    };

    // client.subscribe(`SERVER_${serverId}_CHANNEL`, handler);
        client.subscribe(`/topic/channels`, (msg)=>handler(JSON.parse(msg.body)));

  }, [serverId, queryClient, connected, client]);

  // 🎯 exposed API
  return {
    channels,
    isLoading,
    error,

    createChannel: createMutation.mutate,
    deleteChannel: deleteMutation.mutate,

    isCreating: createMutation.isPending,
    isDeleting: deleteMutation.isPending,
  };
};