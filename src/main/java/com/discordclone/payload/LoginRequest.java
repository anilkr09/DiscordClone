package com.discordclone.payload;

import lombok.Getter;
import lombok.Setter;

// No @Data: a generated toString would put the password into any log line that prints the request.
@Getter
@Setter
public class LoginRequest {
    private String username;
    private String password;
}
