import type { InfiniteData, QueryClient } from "@tanstack/react-query";
import type { Message } from "../../types/message";

/** One page of history, newest first. */
export interface MessagePage {
  content: Message[];
  hasMore: boolean;
}

export type MessageHistory = InfiniteData<MessagePage, string | undefined>;

export const messagesKey = (channelId: string) => ["messages", channelId] as const;

/*
 * Messages seen live, or being sent, that history has not returned yet. Under the Kafka path a
 * message is delivered before it is stored, so a fetch must not make it disappear.
 */
const unconfirmed = new Map<string, Map<string, Message>>();

export const forgetUnconfirmed = () => unconfirmed.clear();

const pendingFor = (channelId: string) => {
  let messages = unconfirmed.get(channelId);
  if (!messages) unconfirmed.set(channelId, (messages = new Map()));
  return messages;
};

const newestFirst = (a: Message, b: Message) =>
  Date.parse(b.timestamp) - Date.parse(a.timestamp) || b.id.localeCompare(a.id);

/** Adds what history does not know about to the newest page, and forgets what it does. */
export function mergeUnconfirmed(channelId: string, page: MessagePage): MessagePage {
  const waiting = unconfirmed.get(channelId);
  if (!waiting?.size) return page;
  const stored = new Set(page.content.map((message) => message.id));
  const oldest = page.content.at(-1);
  const extra: Message[] = [];
  for (const message of waiting.values()) {
    if (stored.has(message.id)) waiting.delete(message.id);
    // Older than this page: it belongs to a later page, which history will supply.
    else if (!oldest || !page.hasMore || newestFirst(message, oldest) < 0) extra.push(message);
  }
  return extra.length ? { ...page, content: [...extra, ...page.content].sort(newestFirst) } : page;
}

const mapPages = (
  queryClient: QueryClient,
  channelId: string,
  change: (content: Message[], index: number) => Message[],
) =>
  queryClient.setQueryData<MessageHistory>(messagesKey(channelId), (history) =>
    history && {
      ...history,
      pages: history.pages.map((page, index) => ({ ...page, content: change(page.content, index) })),
    },
  );

/** A message arrived, or one is being sent (with {@code pending} set). */
export function receiveMessage(queryClient: QueryClient, message: Message): void {
  const channelId = String(message.channelId);
  const waiting = pendingFor(channelId);
  // The delivered copy replaces the one shown while sending.
  const placeholder = message.nonce && !message.pending ? `pending-${message.nonce}` : undefined;
  if (placeholder) waiting.delete(placeholder);
  waiting.set(message.id, message);
  mapPages(queryClient, channelId, (content, index) => {
    const others = content.filter((m) => m.id !== message.id && m.id !== placeholder);
    return index === 0 ? [message, ...others].sort(newestFirst) : others;
  });
}

export function updateMessage(queryClient: QueryClient, message: Message): void {
  const channelId = String(message.channelId);
  const waiting = unconfirmed.get(channelId);
  if (waiting?.has(message.id)) waiting.set(message.id, message);
  mapPages(queryClient, channelId, (content) => content.map((m) => (m.id === message.id ? message : m)));
}

export function removeMessage(queryClient: QueryClient, channelId: string, id: string): void {
  unconfirmed.get(channelId)?.delete(id);
  mapPages(queryClient, channelId, (content) => content.filter((m) => m.id !== id));
}

/** Marks a message still waiting for delivery as failed. */
export function failIfStillPending(queryClient: QueryClient, channelId: string, nonce: string): boolean {
  const message = unconfirmed.get(channelId)?.get(`pending-${nonce}`);
  if (!message) return false;
  receiveMessage(queryClient, { ...message, failed: true });
  return true;
}
