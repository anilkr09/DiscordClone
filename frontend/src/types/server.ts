import type { Schemas } from "../api/client";

export type Server = Schemas["ServerDTO"];
export type ServerPayload = Schemas["ServerPayload"];
export type Channel = Schemas["ChannelDTO"];
export type ChannelPayload = Schemas["ChannelPayload"];

export const ChannelType = {
  TEXT: "TEXT",
  ANNOUNCEMENT: "ANNOUNCEMENT",
  DM: "DM",
} as const satisfies Record<string, Channel["type"]>;
