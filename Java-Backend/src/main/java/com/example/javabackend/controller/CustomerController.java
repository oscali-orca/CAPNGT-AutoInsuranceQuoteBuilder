package com.example.javabackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CustomerController {

    private final AuthenticationProvider authenticationProvider;
    private final SecurityContextRepository securityContextRepository;
    private final LogoutHandler sessionLogoutHandler = new SecurityContextLogoutHandler();
    private final LogoutHandler cookieLogoutHandler = new CookieClearingLogoutHandler("JSESSIONID");

    public CustomerController(AuthenticationProvider authenticationProvider,
                              SecurityContextRepository securityContextRepository) {
        this.authenticationProvider = authenticationProvider;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken csrfToken) {
        return Map.of("token", csrfToken.getToken());
    }

    @GetMapping("/me")
    public Map<String, String> currentUser(Principal principal) {
        return Map.of("username", principal.getName());
    }

    @GetMapping("/session")
    public Map<String, String> currentSession(Principal principal) {
        return Map.of("username", principal.getName());
    }

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession(
            @RequestBody SessionLoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (login.username() == null || login.username().isBlank()
                || login.password() == null || login.password().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_CREDENTIALS",
                    "message", "Username and password are required"));
        }

        try {
            Authentication authentication = authenticationProvider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));

            // Rotate an existing session ID to protect against session fixation.
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            return ResponseEntity.ok(Map.of(
                    "username", authentication.getName(),
                    "message", "Signed in"));
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_CREDENTIALS",
                    "message", "Username or password is incorrect"));
        }
    }

    @DeleteMapping("/session")
    public ResponseEntity<Map<String, String>> deleteSession(
            HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        sessionLogoutHandler.logout(request, response, authentication);
        cookieLogoutHandler.logout(request, response, authentication);
        return ResponseEntity.ok(Map.of("message", "Signed out"));
    }

    public record SessionLoginRequest(String username, String password) {
    }

}
