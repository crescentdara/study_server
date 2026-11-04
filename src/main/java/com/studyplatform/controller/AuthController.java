package com.studyplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String AUTHENTICATED = "lobbyAuthenticated";
    private final byte[] password;

    public AuthController(@Value("${lobby.password:${LOBBY_PASSWORD:}}") String password) {
        if (password.isBlank()) throw new IllegalStateException("LOBBY_PASSWORD must be configured");
        this.password = password.getBytes(StandardCharsets.UTF_8);
    }

    public record LoginRequest(String password) {}

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        if (request.password() == null || !MessageDigest.isEqual(password, request.password().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        HttpSession oldSession = http.getSession(false);
        if (oldSession != null) oldSession.invalidate();
        http.getSession(true).setAttribute(AUTHENTICATED, Boolean.TRUE);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<Void> session(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(AUTHENTICATED))
                ? ResponseEntity.noContent().build() : ResponseEntity.status(401).build();
    }
}
