import { beforeEach, describe, expect, it } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import type { Message } from "../../types/message";
import {
  failIfStillPending, forgetUnconfirmed, mergeUnconfirmed, messagesKey, MessageHistory, receiveMessage,
} from "./cache";

const message = (id: string, second: number, extra: Partial<Message> = {}): Message => ({
  id,
  content: id,
  channelId: 1,
  timestamp: new Date(Date.UTC(2026, 0, 1, 0, 0, second)).toISOString(),
  author: { id: 1, username: "a" },
  ...extra,
});

const loaded = (content: Message[]) => {
  const client = new QueryClient();
  client.setQueryData<MessageHistory>(messagesKey("1"), {
    pages: [{ content, hasMore: false }],
    pageParams: [undefined],
  });
  return client;
};

const shown = (client: QueryClient) =>
  client.getQueryData<MessageHistory>(messagesKey("1"))!.pages.flatMap((p) => p.content.map((m) => m.id));

describe("message cache", () => {
  beforeEach(forgetUnconfirmed);

  it("adds live messages newest first, once", () => {
    const client = loaded([message("a", 1)]);

    receiveMessage(client, message("b", 2));
    receiveMessage(client, message("b", 2));

    expect(shown(client)).toEqual(["b", "a"]);
  });

  it("keeps a live message until history confirms it", () => {
    receiveMessage(loaded([]), message("b", 2));

    const before = mergeUnconfirmed("1", { content: [message("a", 1)], hasMore: false });
    const after = mergeUnconfirmed("1", { content: [message("b", 2), message("a", 1)], hasMore: false });
    const later = mergeUnconfirmed("1", { content: [message("a", 1)], hasMore: false });

    expect(before.content.map((m) => m.id)).toEqual(["b", "a"]);
    expect(after.content.map((m) => m.id)).toEqual(["b", "a"]);
    // Once confirmed it is no longer held back, for example after being deleted.
    expect(later.content.map((m) => m.id)).toEqual(["a"]);
  });

  it("replaces a message being sent with the delivered one", () => {
    const client = loaded([]);
    receiveMessage(client, message("pending-n1", 1, { nonce: "n1", pending: true }));

    receiveMessage(client, message("real", 1, { nonce: "n1" }));

    expect(shown(client)).toEqual(["real"]);
    expect(failIfStillPending(client, "1", "n1")).toBe(false);
  });

  it("marks an undelivered message as failed", () => {
    const client = loaded([]);
    receiveMessage(client, message("pending-n2", 1, { nonce: "n2", pending: true }));

    expect(failIfStillPending(client, "1", "n2")).toBe(true);
    expect(client.getQueryData<MessageHistory>(messagesKey("1"))!.pages[0].content[0].failed).toBe(true);
  });
});
