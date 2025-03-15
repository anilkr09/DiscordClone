package com.discordclone.security;

import com.discordclone.model.User;
import com.discordclone.service.UserService;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtTokenProvider tokenProvider;
    private final UserService userService;

    @Autowired
    public JwtHandshakeInterceptor(JwtTokenProvider tokenProvider, UserService userService) {
        this.tokenProvider = tokenProvider;
        this.userService = userService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

            logger.debug("Before handshake - Checking JWT");

            String jwt = getJwtFromRequest(httpServletRequest);
            logger.debug("Before handshake - Checking JWT  {}",jwt);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromJWT(jwt);
                logger.debug("Handshake validation result: userId={}", userId);

                User user = userService.getUserById(userId);
                UserPrincipal userPrincipal = UserPrincipal.create(user);
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userPrincipal, null, userPrincipal.getAuthorities());

                // ❌ DO NOT SET SecurityContextHolder HERE
                // ✅ Store authentication in attributes for later use
                attributes.put("USER_AUTH", authentication);
                return true;
            }
        }
        return false; // Reject handshake if no valid token
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nothing to do after handshake
    }

    private String getJwtFromRequest(HttpServletRequest request) {
//         First try to get from query parameter
//        String token = request.getParameter("access_token");
//        if (StringUtils.hasText(token)) {
//            logger.info("bearer token fount {}",token);
//            return token;
//        }
        logger.info("before bearer token ");

        // Then try header
        String bearerToken = request.getHeader("Authorization");
        logger.info("bearer token {}",bearerToken);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
