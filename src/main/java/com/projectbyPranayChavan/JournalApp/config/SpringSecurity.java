package com.projectbyPranayChavan.JournalApp.config;

import com.projectbyPranayChavan.JournalApp.filter.JwtFilter;
import com.projectbyPranayChavan.JournalApp.security.OAuth2LoginFailureHandler;
import com.projectbyPranayChavan.JournalApp.security.OAuth2LoginSuccessHandler;
import com.projectbyPranayChavan.JournalApp.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // lets @PreAuthorize work (used on AdminController)
public class SpringSecurity {

    /* Strict Content-Security-Policy for the web UI: only our own scripts, styles, fonts and
       API calls are allowed, so an injected <script> from a stored XSS payload would not run. */
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsServiceImpl userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtFilter jwtFilter,
                                                   OAuth2LoginSuccessHandler oauthSuccess,
                                                   OAuth2LoginFailureHandler oauthFailure) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)

                /* SECURITY: HTTP Basic used to be switched on, which let anyone send the real password
                   with every request and bypassed the JWT/rate-limit logic entirely. */
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))

                .headers(h -> h.addHeaderWriter(extraSecurityHeaders()))

                .authorizeHttpRequests(auth -> auth
                        // the web UI itself (static files)
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg",
                                "/css/**", "/js/**", "/fonts/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // sign-up, login, logout, health check
                        .requestMatchers("/public/**").permitAll()
                        // Google sign-in
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // API docs (only exist when SWAGGER_ENABLED=true; off in prod)
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/user/**", "/journal/**").authenticated()
                        // secure by default: anything not listed above needs a valid login
                        .anyRequest().authenticated()
                )

                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeJson(res, 401, "Please sign in to continue"))
                        .accessDeniedHandler((req, res, ex) -> writeJson(res, 403, "You do not have permission to do this")))

                .oauth2Login(o -> o
                        .loginPage("/")
                        .successHandler(oauthSuccess)
                        .failureHandler(oauthFailure))

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Browser hardening headers (the defaults already add X-Content-Type-Options, X-Frame-Options, HSTS on HTTPS). */
    private HeaderWriter extraSecurityHeaders() {
        return (HttpServletRequest request, HttpServletResponse response) -> {
            String path = request.getRequestURI();
            // Swagger UI needs inline scripts, so it is exempt (and is disabled in production anyway)
            boolean swagger = path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs");
            if (!swagger) {
                response.setHeader("Content-Security-Policy", CSP);
            }
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        };
    }

    /** Only needed if the UI is hosted on a different domain. Empty list = no cross-origin access at all. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*"))   // a wildcard with credentials would defeat the point
                .toList();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/**", config);
        }
        return source;
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"status\":" + status + ",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
