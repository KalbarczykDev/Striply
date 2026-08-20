package dev.kalbarczyk.striply.identity.exception;

import org.springframework.http.HttpStatus;
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

    private record IdentityErrorResponse(String code) {
    }
}
