package com.discordclone.config;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.repository.ServerRepository;
import com.discordclone.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(ServerRepository serverRepository, UserRepository userRepository) {
        return args -> {


            // Create test user if not already present
            if (userRepository.findByUsername("testuser").isEmpty()) {
                User user = new User();
                user.setUsername("testuser");
                user.setEmail("testuser@example.com");
                user.setPassword("password123"); // Optional: hash it
                userRepository.save(user);
                System.out.println("✅ Test user created!");

            // Create default server if none exists
            if (serverRepository.count() == 0) {
                Server server = Server.builder()
                        .name("Default Server")
                        .owner(user)
                        .description("This is a default server on first run")
                        .build();
                serverRepository.save(server);
                System.out.println("✅ Default server created!");
            }
            }
        };
    }
}
