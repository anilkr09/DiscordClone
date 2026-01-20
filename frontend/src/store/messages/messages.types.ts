// src/store/messages/messages.types.ts
import { Message } from "../../types/message.ts";
export interface ChannelMessages {
  loaded: boolean;
  messages: Message[];
}

export interface MessagesState {
  byChannelId: Record<string, ChannelMessages>;
}
