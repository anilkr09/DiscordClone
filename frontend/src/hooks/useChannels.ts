import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import channelService from "../services/channel.service";
import { useStompSubscription } from "../realtime/hooks";
import { applyEvent } from "../realtime/events";
import type { Channel } from "../types/server";

export const useChannels = (serverId: number) => {
  const queryClient = useQueryClient();
  const queryKey = ["channels", serverId];

  const { data: channels = [], isLoading, error } = useQuery({
    queryKey,
    queryFn: () => channelService.getChannels(serverId),
    enabled: !!serverId,
  });

  const createMutation = useMutation({
    mutationFn: channelService.createChannel,
    onSuccess: (created) => {
      queryClient.setQueryData<Channel[]>(queryKey, (old = []) =>
        old.some((c) => c.id === created.id) ? old : [...old, created],
      );
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (channelId: number) => channelService.deleteChannel(serverId, channelId),
    onSuccess: (_, channelId) => {
      queryClient.setQueryData<Channel[]>(queryKey, (old = []) => old.filter((c) => c.id !== channelId));
    },
  });

  useStompSubscription(serverId ? `/topic/servers/${serverId}/channels` : null, (body) =>
    applyEvent(queryClient, body),
  );

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
