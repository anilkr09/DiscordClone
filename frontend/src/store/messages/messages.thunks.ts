import { createAsyncThunk } from "@reduxjs/toolkit";
import { Message } from "../../types/message.ts";
import MessageService from "../../services/message.service.ts";
export const fetchMessages = createAsyncThunk<
  { channelId: string; messages: Message[] },
  string
>("messages/fetch", async (channelId) => {
  // const res = await fetch(
  //   `/api/messages?channelId=${channelId}&limit=50`
  // );

  // const messages: Message[] = await res.json();
  const messages = await MessageService.getChannelMessages(parseInt(channelId));
  return {
    channelId,
    messages,
  };
});
