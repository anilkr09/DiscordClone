package com.discordclone.payload;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
    public class RegistrationResponse {
        private Long id;
        private String username;
        private String email;
        private String message;  // optional - success message or other info


    }
