package com.discordclone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<?> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Discord Clone API");
        response.put("status", "running");
        response.put("version", "1.0.0");
        
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("auth", "/api/auth/**");
        endpoints.put("servers", "/api/servers/**");
        endpoints.put("channels", "/api/channels/**");
        endpoints.put("messages", "/api/messages/**");
        
        response.put("available_endpoints", endpoints);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api")
    public ResponseEntity<?> apiInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Discord Clone API Documentation");
        
        Map<String, Object> authEndpoints = new HashMap<>();
        authEndpoints.put("register", "POST /api/auth/register");
        authEndpoints.put("login", "POST /api/auth/login");
        authEndpoints.put("refresh", "POST /api/auth/refresh");
        
        Map<String, Object> serverEndpoints = new HashMap<>();
        serverEndpoints.put("create", "POST /api/servers");
        serverEndpoints.put("list", "GET /api/servers");
        serverEndpoints.put("get", "GET /api/servers/{serverId}");
        serverEndpoints.put("addMember", "POST /api/servers/{serverId}/members/{userId}");
        serverEndpoints.put("removeMember", "DELETE /api/servers/{serverId}/members/{userId}");
        
        Map<String, Object> channelEndpoints = new HashMap<>();
        channelEndpoints.put("create", "POST /api/channels/servers/{serverId}");
        channelEndpoints.put("list", "GET /api/channels/servers/{serverId}");
        channelEndpoints.put("update", "PUT /api/channels/{channelId}");
        channelEndpoints.put("delete", "DELETE /api/channels/{channelId}");
        
        Map<String, Object> messageEndpoints = new HashMap<>();
        messageEndpoints.put("send", "POST /api/messages/channels/{channelId}");
        messageEndpoints.put("list", "GET /api/messages/channels/{channelId}");
        messageEndpoints.put("update", "PUT /api/messages/{messageId}");
        messageEndpoints.put("delete", "DELETE /api/messages/{messageId}");
        
        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("auth", authEndpoints);
        endpoints.put("servers", serverEndpoints);
        endpoints.put("channels", channelEndpoints);
        endpoints.put("messages", messageEndpoints);
        
        response.put("endpoints", endpoints);
        response.put("websocket", "ws://localhost:8082/ws");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Discord Clone API");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
} 