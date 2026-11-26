package com.discordclone.security;

import com.discordclone.config.LogContext;
import org.slf4j.MDC;
import com.discordclone.exception.JwtAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt)) {
            try {
                Authentication authentication = jwtService.authentication(
                        jwtService.verify(jwt, TokenType.ACCESS));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                MDC.put(LogContext.USER_ID, String.valueOf(((UserPrincipal) authentication.getPrincipal()).getId()));
            } catch (JwtAuthenticationException | UsernameNotFoundException ex) {
                // Left anonymous: the authorization rules decide whether that is enough.
                logger.debug("Ignoring an unusable token: " + ex.getMessage());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LogContext.USER_ID);
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken)
                && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}
