import { useCallback, useMemo } from "react";
import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getChannelMessages } from "../../services/message.service";
import { useAuth } from "../../providers/AuthProvider";
import { realtime } from "../../realtime/realtime";
import { useStompSubscription } from "../../realtime/hooks";
import { applyEvent } from "../../realtime/events";
import { failIfStillPending, mergeUnconfirmed, messagesKey, receiveMessage } from "./cache";

const DELIVERY_TIMEOUT_MS = 10_000;

const newNonce = () =>
  crypto.randomUUID?.() ?? `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;

/** A channel's history, kept live, and the means to add to it. */
export function useMessages(channelId: string) {
  const queryClient = useQueryClient();
  const { id: userId, username } = useAuth();

  const history = useInfiniteQuery({
    queryKey: messagesKey(channelId),
    queryFn: async ({ pageParam }) => {
      const page = await getChannelMessages(Number(channelId), pageParam);
      return pageParam ? page : mergeUnconfirmed(channelId, page);
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => (page.hasMore ? page.content.at(-1)?.id : undefined),
    refetchOnMount: "always",
  });

  useStompSubscription(`/topic/channels/${channelId}/messages`, (body) => applyEvent(queryClient, body));

  /** Oldest first, as shown. */
  const messages = useMemo(
    () => history.data?.pages.flatMap((page) => page.content).reverse() ?? [],
    [history.data],
  );

  /** @returns false if the message could not be sent, so the caller should keep the text */
  const send = useCallback(
    (content: string): boolean => {
      const nonce = newNonce();
      if (!realtime.publish("/app/chat.send", { content, channelId: Number(channelId), nonce })) {
        toast.error("You are offline. The message was not sent.", { id: "offline" });
        return false;
      }
      receiveMessage(queryClient, {
        id: `pending-${nonce}`,
        nonce,
        pending: true,
        content,
        channelId,
        timestamp: new Date().toISOString(),
        author: { id: Number(userId), username: username ?? "" },
      });
      setTimeout(() => {
        if (failIfStillPending(queryClient, channelId, nonce)) {
          toast.error("A message could not be delivered.", { id: nonce });
        }
      }, DELIVERY_TIMEOUT_MS);
      return true;
    },
    [channelId, queryClient, userId, username],
  );

  return {
    messages,
    send,
    isLoading: history.isLoading,
    hasOlder: history.hasNextPage,
    loadingOlder: history.isFetchingNextPage,
    loadOlder: history.fetchNextPage,
  };
}
