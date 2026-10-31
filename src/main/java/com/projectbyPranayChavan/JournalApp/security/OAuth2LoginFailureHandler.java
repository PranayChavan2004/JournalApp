package com.projectbyPranayChavan.JournalApp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final String frontendUrl;

    public OAuth2LoginFailureHandler(@Value("${app.frontend-url:}") String frontendUrl) {
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google sign-in rejected: {}", exception.getClass().getSimpleName());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(frontendUrl + "/?auth_error=failed");
    }
}
