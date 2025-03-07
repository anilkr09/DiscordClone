export enum UserStatus {
  ONLINE = 'ONLINE',
  OFFLINE = 'OFFLINE',
  IDLE = 'IDLE',
  DO_NOT_DISTURB = 'DO_NOT_DISTURB'
}

export interface StatusColor {
  [key: string]: string;
}

export const STATUS_COLORS: StatusColor = {
  [UserStatus.ONLINE]: '#3ba55d',
  [UserStatus.IDLE]: '#faa81a',
  [UserStatus.DO_NOT_DISTURB]: '#ed4245',
  [UserStatus.OFFLINE]: '#747f8d'
};

export interface CustomStatus {
  text: string;
  emoji?: string;
  expiresAt?: Date;
}

export interface StatusUpdate {
  status: UserStatus;
  customStatus?: CustomStatus;
} 