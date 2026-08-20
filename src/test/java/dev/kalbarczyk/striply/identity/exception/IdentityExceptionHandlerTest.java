package dev.kalbarczyk.striply.identity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdentityExceptionHandlerTest {

    private static final String INVALID_CREDENTIALS_PATH =
            "/invalid-credentials";
    private static final String ERROR_CODE_PATH = "$.code";
    private static final String INVALID_CREDENTIALS_CODE =
            "INVALID_CREDENTIALS";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new IdentityExceptionHandler())
            .build();

    @Test
    void invalidCredentialsReturnSafeUnauthorizedResponse() throws Exception {
        mockMvc.perform(get(INVALID_CREDENTIALS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(ERROR_CODE_PATH)
                        .value(INVALID_CREDENTIALS_CODE));
    }

    @RestController
    private static class ThrowingController {

        @GetMapping(INVALID_CREDENTIALS_PATH)
        void invalidCredentials() {
            throw new InvalidCredentialsException();
        }
    }
}
