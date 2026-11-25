import { describe, expect, it, vi } from "vitest";
import { SubscriptionManager, StompLike } from "./subscriptionManager";

const fakeClient = () => {
  const open = new Map<string, Set<(frame: { body: string }) => void>>();
  const client: StompLike & { count(d: string): number; deliver(d: string, body: unknown): void; drop(): void } = {
    subscribe: vi.fn((destination, callback) => {
      const callbacks = open.get(destination) ?? new Set();
      open.set(destination, callbacks.add(callback));
      return { unsubscribe: () => void callbacks.delete(callback) };
    }),
    publish: vi.fn(),
    count: (destination) => open.get(destination)?.size ?? 0,
    deliver: (destination, body) =>
      open.get(destination)?.forEach((callback) => callback({ body: JSON.stringify(body) })),
    drop: () => open.clear(),
  };
  return client;
};

describe("SubscriptionManager", () => {
  it("holds one subscription per destination across re-renders and remounts", () => {
    const client = fakeClient();
    const manager = new SubscriptionManager(client);
    manager.connected();

    // Two components, each mounted, unmounted and mounted again.
    for (let render = 0; render < 5; render++) {
      const a = manager.subscribe("/topic/x", () => {});
      const b = manager.subscribe("/topic/x", () => {});
      expect(client.count("/topic/x")).toBe(1);
      a();
      expect(client.count("/topic/x")).toBe(1);
      b();
      expect(client.count("/topic/x")).toBe(0);
    }
  });

  it("delivers every frame once to each subscriber", () => {
    const client = fakeClient();
    const manager = new SubscriptionManager(client);
    const first = vi.fn();
    const second = vi.fn();
    manager.subscribe("/topic/x", first);
    const stop = manager.subscribe("/topic/x", second);
    manager.connected();

    client.deliver("/topic/x", { n: 1 });
    stop();
    client.deliver("/topic/x", { n: 2 });

    expect(first.mock.calls).toEqual([[{ n: 1 }], [{ n: 2 }]]);
    expect(second.mock.calls).toEqual([[{ n: 1 }]]);
  });

  it("restores subscriptions after a reconnect without duplicating them", () => {
    const client = fakeClient();
    const manager = new SubscriptionManager(client);
    const handler = vi.fn();
    manager.subscribe("/topic/x", handler);
    manager.connected();

    manager.disconnected();
    client.drop();
    manager.connected();
    client.deliver("/topic/x", 1);

    expect(client.count("/topic/x")).toBe(1);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("does not send while disconnected, and says so", () => {
    const client = fakeClient();
    const manager = new SubscriptionManager(client);

    expect(manager.publish("/app/chat.send", {})).toBe(false);
    manager.connected();
    expect(manager.publish("/app/chat.send", {})).toBe(true);
    expect(client.publish).toHaveBeenCalledTimes(1);
  });

  it("ignores a second call of the same unsubscribe function", () => {
    const client = fakeClient();
    const manager = new SubscriptionManager(client);
    manager.connected();
    const a = manager.subscribe("/topic/x", () => {});
    manager.subscribe("/topic/x", () => {});

    a();
    a();

    expect(client.count("/topic/x")).toBe(1);
  });
});
