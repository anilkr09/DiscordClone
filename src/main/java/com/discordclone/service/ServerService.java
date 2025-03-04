package com.discordclone.service;

import com.discordclone.model.Server;
import com.discordclone.model.User;
import com.discordclone.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {
    
    private final ServerRepository serverRepository;
    private final UserService userService;

    @Transactional
    public Server createServer(Server server, Long ownerId) {
        User owner = userService.getUserById(ownerId);
        server.setOwner(owner);
        server.getMembers().add(owner);
        return serverRepository.save(server);
    }

    @Transactional(readOnly = true)
    public Server getServerById(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Server not found"));
    }

    @Transactional(readOnly = true)
    public List<Server> getUserServers(Long userId) {
        User user = userService.getUserById(userId);
        return serverRepository.findByMembersContaining(user);
    }

    @Transactional
    public Server addMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);
        
        if (server.getMembers().contains(user)) {
            throw new RuntimeException("User is already a member of this server");
        }
        
        server.getMembers().add(user);
        return serverRepository.save(server);
    }

    @Transactional
    public Server removeMember(Long serverId, Long userId) {
        Server server = getServerById(serverId);
        User user = userService.getUserById(userId);
        
        if (server.getOwner().equals(user)) {
            throw new RuntimeException("Server owner cannot be removed");
        }
        
        if (!server.getMembers().contains(user)) {
            throw new RuntimeException("User is not a member of this server");
        }
        
        server.getMembers().remove(user);
        return serverRepository.save(server);
    }
} 