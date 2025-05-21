package com.discordclone.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDTO {
    private Long userId;
    private Long serverId;

    // Include basic user information for convenience
    private String username;
    private String email;

    private String nickname;
    private Role role;
    private LocalDateTime joinedAt;

    public static MemberDTO fromEntity(Member member) {
        if (member == null) {
            return null;
        }

        MemberDTO dto = new MemberDTO();
        dto.setUserId(member.getUserId());
        dto.setServerId(member.getServerId());

        // Include user details if user is loaded
        if (member.getUser() != null) {
            dto.setUsername(member.getUser().getUsername());
            dto.setEmail(member.getUser().getEmail());
        }

        dto.setNickname(member.getNickname());
        dto.setRole(member.getRole());
        dto.setJoinedAt(member.getJoinedAt());

        return dto;
    }
}