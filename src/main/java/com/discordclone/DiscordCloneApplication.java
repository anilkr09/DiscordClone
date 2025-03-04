package com.discordclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.discordclone.model")
@EnableJpaRepositories("com.discordclone.repository")
public class DiscordCloneApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscordCloneApplication.class, args);
    }
} 