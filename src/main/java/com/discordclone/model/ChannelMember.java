package com.discordclone.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** A participant of a DM channel. Server channels derive access from server membership instead. */
@Entity
@Table(name = "channel_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelMember {

    @EmbeddedId
    private Key id;

    public ChannelMember(Long channelId, Long userId) {
        this.id = new Key(channelId, userId);
    }

    @Embeddable
    public record Key(
            @Column(name = "channel_id") Long channelId,
            @Column(name = "user_id") Long userId) implements Serializable {
    }
}
