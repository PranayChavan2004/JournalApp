package com.projectbyPranayChavan.JournalApp.filter;

import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.repository.UserRepository;
import com.projectbyPranayChavan.JournalApp.security.AuthCookieService;
import com.projectbyPranayChavan.JournalApp.service.UserDetailsServiceImpl;
import com.projectbyPranayChavan.JournalApp.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final AuthCookieService cookieService;

    public JwtFilter(JwtUtils jwtUtils, UserRepository userRepository, AuthCookieService cookieService) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.cookieService = cookieService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/public/") || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = null;
        boolean fromCookie = false;

        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        } else {
            Optional<String> cookieToken = cookieService.readToken(request);
            if (cookieToken.isPresent()) {
                token = cookieToken.get();
                fromCookie = true;
            }
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<User> user = jwtUtils.parse(token)
                    .flatMap(parsed -> userRepository.findById(parsed.userId())
                            // SECURITY: a token issued before a password change / revocation is rejected
                            .filter(u -> u.getTokenVersion() == parsed.version()));

            if (user.isPresent()) {

                if (fromCookie && !SAFE_METHODS.contains(request.getMethod())
                        && !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getOutputStream().write(
                            "{\"status\":403,\"message\":\"Missing X-Requested-With header\"}".getBytes(StandardCharsets.UTF_8));
                    return;
                }

                UserDetails userDetails = UserDetailsServiceImpl.toUserDetails(user.get());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        chain.doFilter(request, response);
    }
}
