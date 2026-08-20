package dev.kalbarczyk.striply.identity.controller;

import dev.kalbarczyk.striply.identity.exception.InvalidRefreshTokenException;
import dev.kalbarczyk.striply.identity.model.RefreshTokenFailureReason;
import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
import dev.kalbarczyk.striply.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class IdentityController {

    static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;

    @PostMapping("/register")
    public RegisteredUser register(@RequestBody RegisterUserCommand command) {
        return authService.register(command);
    }

    @PostMapping("/login")
    public ResponseEntity<IssuedAccessToken> login(
            @RequestBody LoginUserCommand command
    ) {
        return sessionResponse(authService.login(command));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(
                    value = REFRESH_TOKEN_COOKIE,
                    required = false
            ) String refreshToken
    ) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<IssuedAccessToken> refresh(
            @CookieValue(
                    value = REFRESH_TOKEN_COOKIE,
                    required = false
            ) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    RefreshTokenFailureReason.MALFORMED
            );
        }

        return sessionResponse(authService.refresh(refreshToken));
    }

    private ResponseEntity<IssuedAccessToken> sessionResponse(
            IssuedSession session
    ) {
        ResponseCookie replacementCookie = ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, session.refreshToken().rawValue())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, replacementCookie.toString())
                .body(session.accessToken());
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }
}
