package dev.kalbarczyk.striply.identity.controller;

import dev.kalbarczyk.striply.identity.exception.InvalidCredentialsException;
import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
import dev.kalbarczyk.striply.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityController.class)
@AutoConfigureMockMvc(addFilters = false)
class IdentityControllerTest {

    private static final String LOGIN_PATH = "/api/login";
    private static final String REGISTRATION_PATH = "/api";
    private static final String USER_EMAIL = "test@test.com";
    private static final String USER_PASSWORD = "password1234";
    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final Instant ACCESS_TOKEN_EXPIRY =
            Instant.parse("2026-08-20T10:05:00Z");
    private static final Instant REFRESH_TOKEN_EXPIRY =
            Instant.parse("2026-08-27T10:00:00Z");
    private static final String LOGIN_REQUEST = """
            {
              "email": "test@test.com",
              "password": "password1234"
            }
            """;
    private static final String ISSUED_SESSION_RESPONSE = """
            {
              "accessToken": {
                "value": "access-token",
                "expiresAt": "2026-08-20T10:05:00Z"
              },
              "refreshToken": {
                "rawValue": "refresh-token",
                "expiresAt": "2026-08-27T10:00:00Z"
              }
            }
            """;
    private static final String INVALID_CREDENTIALS_RESPONSE = """
            {"code": "INVALID_CREDENTIALS"}
            """;
    private static final String REGISTRATION_REQUEST = """
            {
              "email": "test@test.com",
              "password": "password1234"
            }
            """;
    private static final String REGISTERED_USER_RESPONSE = """
            {
              "id": "10000000-0000-0000-0000-000000000001",
              "email": "test@test.com"
            }
            """;
    private static final String INVALID_REGISTRATION_RESPONSE = """
            {"code": "INVALID_REGISTRATION"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldReturnIssuedSessionForValidCredentials() throws Exception {
        LoginUserCommand command = new LoginUserCommand(USER_EMAIL, USER_PASSWORD);
        IssuedSession session = new IssuedSession(
                new IssuedAccessToken(
                        ACCESS_TOKEN,
                        ACCESS_TOKEN_EXPIRY
                ),
                new IssuedRefreshToken(
                        REFRESH_TOKEN,
                        REFRESH_TOKEN_EXPIRY
                )
        );
        when(authService.login(command)).thenReturn(session);

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_REQUEST))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        ISSUED_SESSION_RESPONSE,
                        JsonCompareMode.STRICT
                ));
    }

    @Test
    void shouldReturnUnauthorizedWithoutExposingCredentialDetails() throws Exception {
        LoginUserCommand command = new LoginUserCommand(USER_EMAIL, USER_PASSWORD);
        when(authService.login(command))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        INVALID_CREDENTIALS_RESPONSE,
                        JsonCompareMode.STRICT
                ));
    }

    @Test
    void shouldReturnRegisteredUserForValidRegistration() throws Exception {
        RegisterUserCommand command = new RegisterUserCommand(
                USER_EMAIL,
                USER_PASSWORD
        );
        when(authService.register(command))
                .thenReturn(new RegisteredUser(USER_ID, USER_EMAIL));

        mockMvc.perform(post(REGISTRATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION_REQUEST))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        REGISTERED_USER_RESPONSE,
                        JsonCompareMode.STRICT
                ));
    }

    @Test
    void shouldReturnBadRequestWithoutExposingRegistrationDetails()
            throws Exception {
        RegisterUserCommand command = new RegisterUserCommand(
                USER_EMAIL,
                USER_PASSWORD
        );
        when(authService.register(command)).thenThrow(
                new InvalidRegistrationException(
                        RegistrationFailureReason.EMAIL_ALREADY_EXISTS
                )
        );

        mockMvc.perform(post(REGISTRATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRATION_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        INVALID_REGISTRATION_RESPONSE,
                        JsonCompareMode.STRICT
                ));
    }
}
