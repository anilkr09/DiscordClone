package com.discordclone.model;

import java.io.Serializable;
import java.util.Objects;

public class MemberId implements Serializable {
    private Long userId;
    private Long serverId;

    public MemberId() {}

    public MemberId(Long userId, Long serverId) {
        this.userId = userId;
        this.serverId = serverId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberId)) return false;
        MemberId that = (MemberId) o;
        return Objects.equals(userId, that.userId) &&
                Objects.equals(serverId, that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, serverId);
    }
}
