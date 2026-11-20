package com.example.demo.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("application", "Redis Token Management", "status", "running");
    }

    @GetMapping("/api/users/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        return Map.of("username", authentication.getName(), "authorities", authentication.getAuthorities());
    }

    @GetMapping("/api/test")
    public Map<String, String> protectedTest() {
        return Map.of("message", "Authenticated request accepted");
    }
}
