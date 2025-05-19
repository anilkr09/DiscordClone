    package com.discordclone.security;

    import com.fasterxml.jackson.databind.ObjectMapper;
    import jakarta.servlet.FilterChain;
    import jakarta.servlet.ServletException;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.security.core.userdetails.UserDetails;
    import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
    import org.springframework.util.StringUtils;
    import org.springframework.web.filter.OncePerRequestFilter;
    import java.io.IOException;
    import java.time.LocalDateTime;
    import java.util.Map;

    public class JwtAuthenticationFilter extends OncePerRequestFilter {


            private final JwtTokenProvider tokenProvider;
            private final CustomUserDetailsService customUserDetailsService;



        public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, CustomUserDetailsService customUserDetailsService) {

            this.tokenProvider = tokenProvider;
            this.customUserDetailsService = customUserDetailsService;
        }



        @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                    try {
                        String jwt = getJwtFromRequest(request);

                        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                            Long userId = tokenProvider.getUserIdFromJWT(jwt);
                            UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    } catch (Exception ex) {
                        logger.error("JWT authentication failed", ex);

                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                                "timestamp", LocalDateTime.now().toString(),
                                "status", 401,
                                "error", "Unauthorized",
                                "message", ex.getMessage(),
                                "path", request.getRequestURI()
                        )));


                        return;
                    }

                filterChain.doFilter(request, response);
            }

            private String getJwtFromRequest(HttpServletRequest request) {
                String bearerToken = request.getHeader("Authorization");
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                    return bearerToken.substring(7);
                }
                return null;
            }
        }
