import { store } from "../store/store.ts";
import { addMessage } from "../store/messages/messages.slice.ts";
import { Message } from "../types/message.ts";
export const registerMessageSocket = (socket) => {
  socket.subscribe("/user/queue/messages", (msg) => {
      console.log("🟢 WS message received:", msg.body);

    const message: Message = JSON.parse(msg.body);

    store.dispatch(
      addMessage({
        channelId: message.channelId,
        message,
      })
    );
  });
};

export const registerGroupMessageSocket = (socket,channelId) => {
  socket.subscribe(`/topic/channels/${channelId}/messages`, (msg) => {
      console.log("🟢 WS message received:", msg.body);

    const message: Message = JSON.parse(msg.body);

    store.dispatch(
      addMessage({
        channelId: message.channelId,
        message,
      })
    );
  });
};
