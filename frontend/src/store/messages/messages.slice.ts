import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import { Message }  from "../../types/message.ts";
import { MessagesState } from "./messages.types.ts";
import { fetchMessages } from "./messages.thunks.ts";

const initialState: MessagesState = {
  byChannelId: {},
};

const MAX_MESSAGES_PER_CHANNEL = 200;

/**
 * Deduplicate by message.id and sort by timestamp
 */
const dedupeAndSort = (messages: Message[]) => {
  const map = new Map<string, Message>();

  for (const m of messages) {
    map.set(m.id, m);
  }

  return Array.from(map.values()).sort(
    (a, b) =>
      new Date(a.timestamp).getTime() -
      new Date(b.timestamp).getTime()
  );
};

const messagesSlice = createSlice({
  name: "messages",
  initialState,
  reducers: {
    addMessage(
      state,
      action: PayloadAction<{ channelId: string; message: Message }>
    ) {
      const { channelId, message } = action.payload;

      const channel =
        state.byChannelId[channelId] ??
        (state.byChannelId[channelId] = {
          loaded: false,
          messages: [],
        });

      channel.messages.push(message);
      channel.messages = dedupeAndSort(channel.messages);

      if (channel.messages.length > MAX_MESSAGES_PER_CHANNEL) {
        channel.messages = channel.messages.slice(-MAX_MESSAGES_PER_CHANNEL);
      }
    },

    replaceOptimisticMessage(
      state,
      action: PayloadAction<{
        channelId: string;
        tempId: string;
        message: Message;
      }>
    ) {
      const channel = state.byChannelId[action.payload.channelId];
      if (!channel) return;

      const idx = channel.messages.findIndex(
        (m) => m.id === action.payload.tempId
      );

      if (idx !== -1) {
        channel.messages[idx] = action.payload.message;
      }
    },

    clearChannelMessages(state, action: PayloadAction<string>) {
      delete state.byChannelId[action.payload];
    },
  },

  extraReducers: (builder) => {
    builder.addCase(fetchMessages.fulfilled, (state, action) => {
      const { channelId, messages } = action.payload;

      const channel =
        state.byChannelId[channelId] ??
        (state.byChannelId[channelId] = {
          loaded: false,
          messages: [],
        });

      channel.messages = dedupeAndSort([
        ...messages,
        ...channel.messages, // realtime messages already received
      ]);

      channel.loaded = true;
    });
  },
});

export const {
  addMessage,
  replaceOptimisticMessage,
  clearChannelMessages,
} = messagesSlice.actions;

export default messagesSlice.reducer;
