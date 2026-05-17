package com.discordclone.service;

import com.discordclone.model.User;
import com.discordclone.model.UserStatus;
import com.discordclone.model.UserStatusEntity;
import com.discordclone.payload.StatusResponse;

import java.util.List;

public interface UserStatusService {

    // realtime
    void handleHeartbeat(Long userId);
    void handleActivity(Long userId);

    // queries
    UserStatus getUserStatus(Long userId);
    List<StatusResponse> getFriendsStatus(Long userId);

    // custom status (REST)
    User updateCustomStatus(Long userId, UserStatus customStatus);
    User clearCustomStatus(Long userId);
    void setOfflineAndBroadCast(Long userId, UserStatus status);
    // admin/reset
    void resetPresence(Long userId);

    // persistence
    void persistLastSeen(Long userId);

    // ws
    void broadcastStatusChange(Long userId, UserStatus status);

    UserStatusEntity getUserStatusEntity(Long userId);
}