package com.discordclone.controller;

import com.discordclone.exception.ForbiddenException;
import com.discordclone.exception.JwtAuthenticationException;
import com.discordclone.model.User;
import com.discordclone.payload.JwtAuthResponse;
import com.discordclone.payload.LoginRequest;
import com.discordclone.payload.RegistrationRequest;
import com.discordclone.security.*;
import com.discordclone.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;

/**
 * Access tokens travel in response bodies and are kept in memory by the client. Refresh tokens
 * travel only in an HttpOnly cookie that browsers send to {@value #REFRESH_PATH} alone. Calls that
 * rely on that cookie must also carry the {@value #CSRF_HEADER} header, which a cross-origin page
 * cannot add without passing the CORS allowlist.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String COOKIE = "refresh_token";
    static final String REFRESH_PATH = "/api/auth/refresh";
    static final String CSRF_HEADER = "X-CSRF-Protection";

    private final AuthenticationManager authenticationManager;
    private final LoginSessions loginSessions;
    private final UserService userService;
    private final RateLimiter rateLimiter;
    private final CookieProperties cookies;
    private final JwtProperties jwt;

    @PostMapping("/register")
    public ResponseEntity<JwtAuthResponse> registerUser(
            @Valid @RequestBody RegistrationRequest request, HttpServletRequest http) {
        rateLimiter.check(RateLimiter.Action.REGISTER, http.getRemoteAddr());
        log.info("Registering username={}", request.getUsername());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        User created = userService.createUser(user);

        return signedIn(loginSessions.open(created.getId()), created);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> authenticateUser(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Per address and name against password guessing; per address against trying many names.
        rateLimiter.check(RateLimiter.Action.LOGIN_ADDRESS, http.getRemoteAddr());
        rateLimiter.check(RateLimiter.Action.LOGIN,
                http.getRemoteAddr() + ":" + request.getUsername().strip().toLowerCase(Locale.ROOT));

        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        return signedIn(loginSessions.open(principal.getId()), userService.getUserById(principal.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthResponse> refreshToken(
            @Parameter(hidden = true) @CookieValue(name = COOKIE, required = false) String refreshToken,
            @Parameter(hidden = true) @RequestHeader(name = CSRF_HEADER, required = false) String csrf,
            HttpServletRequest http) {
        requireCsrfHeader(csrf);
        rateLimiter.check(RateLimiter.Action.REFRESH, http.getRemoteAddr());
        if (refreshToken == null) {
            throw new JwtAuthenticationException("Not signed in");
        }
        LoginSessions.Tokens tokens = loginSessions.refresh(refreshToken);
        return signedIn(tokens, userService.getUserById(tokens.userId()));
    }

    /** Signs out: revokes the refresh token, closes this login's sockets and clears the cookie. */
    @DeleteMapping("/refresh")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = COOKIE, required = false) String refreshToken,
            @Parameter(hidden = true) @RequestHeader(name = CSRF_HEADER, required = false) String csrf) {
        requireCsrfHeader(csrf);
        if (refreshToken != null) {
            try {
                loginSessions.close(refreshToken);
            } catch (JwtAuthenticationException ex) {
                log.debug("Signing out with an unusable token: {}", ex.getMessage());
            }
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<JwtAuthResponse> signedIn(LoginSessions.Tokens tokens, User user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), jwt.refreshExpiration()).toString())
                .body(JwtAuthResponse.builder()
                        .accessToken(tokens.accessToken())
                        .tokenType("Bearer")
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookies.secure())
                .sameSite(cookies.sameSite())
                .path(REFRESH_PATH)
                .maxAge(maxAge)
                .build();
    }

    private static void requireCsrfHeader(String value) {
        if (value == null) {
            throw new ForbiddenException("Missing " + CSRF_HEADER + " header");
        }
    }
}
