package com.example.DiscordClone.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")  // Base path for all admin endpoints
public class AdminController {
    @GetMapping("")
    public String defaullt() {
        return "Welcome to default page!";
    }
    @GetMapping("/admin")
    public String adminEndpoint() {
        return "Welcome to the Admin Page!";
    }
}
