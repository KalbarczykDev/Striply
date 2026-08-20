package dev.kalbarczyk.striply.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    IdentityErrorResponse handleInvalidCredentials() {
        return new IdentityErrorResponse("INVALID_CREDENTIALS");
    }

    @ExceptionHandler(InvalidRegistrationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    IdentityErrorResponse handleInvalidRegistration() {
        return new IdentityErrorResponse("INVALID_REGISTRATION");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<IdentityErrorResponse> handleInvalidRefreshToken() {
        ResponseCookie clearedCookie = ResponseCookie
                .from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .body(new IdentityErrorResponse("INVALID_REFRESH_TOKEN"));
    }

    private record IdentityErrorResponse(String code) {
    }
}
