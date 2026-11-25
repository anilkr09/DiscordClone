/** The part of a STOMP client the manager needs. */
export interface StompLike {
  subscribe(destination: string, callback: (frame: { body: string }) => void): { unsubscribe(): void };
  publish(frame: { destination: string; body: string }): void;
}

type Handler = (body: unknown) => void;

interface Entry {
  // Each subscriber gets its own record, so the same function subscribed twice counts twice.
  handlers: Set<{ handle: Handler }>;
  subscription?: { unsubscribe(): void };
}

/**
 * Holds at most one STOMP subscription per destination, however many components ask for it. The
 * subscription opens with the first subscriber, closes after the last, and is restored after every
 * reconnect.
 */
export class SubscriptionManager {
  private readonly entries = new Map<string, Entry>();
  private readonly listeners = new Set<() => void>();
  private readonly client: StompLike;
  private online = false;

  constructor(client: StompLike) {
    this.client = client;
  }

  readonly isConnected = (): boolean => this.online;

  /** Notifies when the connection state changes. */
  readonly onChange = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  connected(): void {
    this.online = true;
    this.entries.forEach((entry, destination) => this.open(destination, entry));
    this.listeners.forEach((listener) => listener());
  }

  disconnected(): void {
    if (!this.online) return;
    this.online = false;
    // The server forgot these with the connection.
    this.entries.forEach((entry) => (entry.subscription = undefined));
    this.listeners.forEach((listener) => listener());
  }

  subscribe(destination: string, handle: Handler): () => void {
    let entry = this.entries.get(destination);
    if (!entry) {
      entry = { handlers: new Set() };
      this.entries.set(destination, entry);
    }
    const record = { handle };
    entry.handlers.add(record);
    if (this.online && !entry.subscription) this.open(destination, entry);

    const owned = entry;
    return () => {
      if (!owned.handlers.delete(record) || owned.handlers.size > 0) return;
      if (this.online) owned.subscription?.unsubscribe();
      this.entries.delete(destination);
    };
  }

  /** @returns false if there is no connection, in which case nothing was sent */
  publish(destination: string, body: unknown = {}): boolean {
    if (!this.online) return false;
    this.client.publish({ destination, body: JSON.stringify(body) });
    return true;
  }

  private open(destination: string, entry: Entry): void {
    entry.subscription = this.client.subscribe(destination, (frame) => {
      let body: unknown;
      try {
        body = JSON.parse(frame.body);
      } catch (error) {
        console.error(`Unreadable frame on ${destination}`, error);
        return;
      }
      entry.handlers.forEach(({ handle }) => handle(body));
    });
  }
}
