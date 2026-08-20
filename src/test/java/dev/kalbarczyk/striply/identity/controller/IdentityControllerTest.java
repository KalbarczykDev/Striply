package dev.kalbarczyk.striply.identity.controller;

import dev.kalbarczyk.striply.identity.exception.InvalidCredentialsException;
import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.exception.InvalidRefreshTokenException;
import dev.kalbarczyk.striply.identity.model.RefreshTokenFailureReason;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentityController.class)
@AutoConfigureMockMvc(addFilters = false)
class IdentityControllerTest {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTRATION_PATH = "/api/auth/register";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String LOGOUT_PATH = "/api/auth/logout";
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";
    private static final String ORIGINAL_REFRESH_TOKEN =
            "original-refresh-token";
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
    private static final String ACCESS_TOKEN_RESPONSE = """
            {
              "value": "access-token",
              "expiresAt": "2026-08-20T10:05:00Z"
            }
            """;
    private static final String INVALID_REFRESH_TOKEN_RESPONSE = """
            {"code": "INVALID_REFRESH_TOKEN"}
            """;
    private static final String SAME_SITE_STRICT = "SameSite=Strict";
    private static final String EMPTY_STRING = "";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldSetRefreshCookieAndReturnAccessTokenForValidCredentials()
            throws Exception {
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
                        ACCESS_TOKEN_RESPONSE,
                        JsonCompareMode.STRICT
                ))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        REFRESH_TOKEN
                ))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        REFRESH_COOKIE_PATH
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString(SAME_SITE_STRICT)
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

    @Test
    void shouldRotateRefreshCookieAndReturnAccessToken() throws Exception {
        IssuedSession session = new IssuedSession(
                new IssuedAccessToken(ACCESS_TOKEN, ACCESS_TOKEN_EXPIRY),
                new IssuedRefreshToken(REFRESH_TOKEN, REFRESH_TOKEN_EXPIRY)
        );
        when(authService.refresh(ORIGINAL_REFRESH_TOKEN)).thenReturn(session);

        mockMvc.perform(post(REFRESH_PATH)
                        .cookie(new jakarta.servlet.http.Cookie(
                                REFRESH_COOKIE_NAME,
                                ORIGINAL_REFRESH_TOKEN
                        )))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        ACCESS_TOKEN_RESPONSE,
                        JsonCompareMode.STRICT
                ))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        REFRESH_TOKEN
                ))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        REFRESH_COOKIE_PATH
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString(SAME_SITE_STRICT)
                ));
    }

    @Test
    void shouldRejectInvalidRefreshTokenAndClearCookie() throws Exception {
        when(authService.refresh(ORIGINAL_REFRESH_TOKEN)).thenThrow(
                new InvalidRefreshTokenException(
                        RefreshTokenFailureReason.ALREADY_CONSUMED
                )
        );

        mockMvc.perform(post(REFRESH_PATH)
                        .cookie(new jakarta.servlet.http.Cookie(
                                REFRESH_COOKIE_NAME,
                                ORIGINAL_REFRESH_TOKEN
                        )))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        INVALID_REFRESH_TOKEN_RESPONSE,
                        JsonCompareMode.STRICT
                ))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));
    }

    @Test
    void shouldRejectMissingRefreshCookieWithInvalidTokenContract()
            throws Exception {
        mockMvc.perform(post(REFRESH_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(
                        INVALID_REFRESH_TOKEN_RESPONSE,
                        JsonCompareMode.STRICT
                ))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verifyNoInteractions(authService);
    }

    @Test
    void shouldLogoutPresentedTokenAndClearRefreshCookie() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH)
                        .cookie(new jakarta.servlet.http.Cookie(
                                REFRESH_COOKIE_NAME,
                                ORIGINAL_REFRESH_TOKEN
                        )))
                .andExpect(status().isNoContent())
                .andExpect(content().string(EMPTY_STRING))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        EMPTY_STRING
                ))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        REFRESH_COOKIE_PATH
                ));

        verify(authService).logout(ORIGINAL_REFRESH_TOKEN);
    }

    @Test
    void shouldClearRefreshCookieWithoutServiceCallWhenCookieIsMissing()
            throws Exception {
        mockMvc.perform(post(LOGOUT_PATH))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        EMPTY_STRING
                ))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        REFRESH_COOKIE_PATH
                ));

        verifyNoInteractions(authService);
    }
}
