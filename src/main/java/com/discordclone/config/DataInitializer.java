package com.discordclone.config;

import com.discordclone.model.Channel;
import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.payload.ServerPayload;
import com.discordclone.repository.ChannelRepository;
import com.discordclone.repository.MemberRepository;
import com.discordclone.repository.ServerRepository;
import com.discordclone.repository.UserRepository;
import com.discordclone.service.ServerService;
import com.discordclone.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional development data: a user, a server they own and a channel. Off by default, because it
 * creates an account that can log in; set {@code SEED_PASSWORD} and {@code app.seed.enabled=true}.
 * Each step runs only if its result is missing, so a partly seeded database is repaired.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer {

    static final String USERNAME = "testuser";
    static final String SERVER_NAME = "Default Server";

    @Bean
    CommandLineRunner seedData(UserService userService, UserRepository users, ServerService serverService,
                               ServerRepository servers, MemberRepository members, ChannelRepository channels,
                               @Value("${app.seed.password}") String password) {
        return args -> {
            User user = users.findByUsername(USERNAME).orElseGet(() -> userService.createUser(User.builder()
                    .username(USERNAME).email(USERNAME + "@example.com").password(password).build()));

            Server server = servers.findByName(SERVER_NAME).orElseGet(() -> {
                ServerPayload payload = new ServerPayload();
                payload.setName(SERVER_NAME);
                payload.setDescription("Seeded for local development");
                return serverService.createServer(payload, user.getId());
            });

            Long ownerId = server.getOwner().getId();
            if (!serverService.isUserMember(server.getId(), ownerId)) {
                serverService.addMember(server.getId(), ownerId);
            }
            if (channels.findByServer(server).isEmpty()) {
                channels.save(Channel.builder().name("general").server(server).build());
            }
            log.info("Seed data is in place");
        };
    }
}
