package com.discordclone.model;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Setter

public class MemberId implements Serializable {

    private Long userId;
    private Long serverId;
}