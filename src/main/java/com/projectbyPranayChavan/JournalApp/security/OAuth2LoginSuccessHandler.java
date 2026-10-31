package com.projectbyPranayChavan.JournalApp.security;

import com.projectbyPranayChavan.JournalApp.entities.User;
import com.projectbyPranayChavan.JournalApp.exception.ApiException;
import com.projectbyPranayChavan.JournalApp.service.UserService;
import com.projectbyPranayChavan.JournalApp.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final AuthCookieService cookieService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(UserService userService,
                                     JwtUtils jwtUtils,
                                     AuthCookieService cookieService,
                                     @Value("${app.frontend-url:}") String frontendUrl) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.cookieService = cookieService;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = frontendUrl + "/";
        try {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            String email = principal.getAttribute("email");
            Object verified = principal.getAttribute("email_verified");

            if (email == null || email.isBlank() || !"true".equalsIgnoreCase(String.valueOf(verified))) {
                target = frontendUrl + "/?auth_error=unverified";
            } else {
                User user = userService.findOrCreateGoogleUser(email);
                cookieService.addAuthCookie(response, jwtUtils.generateToken(user));
            }
        } catch (ApiException e) {
            target = frontendUrl + "/?auth_error=account_exists";
        } catch (Exception e) {
            log.error("Google sign-in failed", e);
            target = frontendUrl + "/?auth_error=failed";
        } finally {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        response.sendRedirect(target);
    }
}
