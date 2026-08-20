package dev.kalbarczyk.striply.configuration;

import dev.kalbarczyk.striply.identity.controller.IdentityController;
import dev.kalbarczyk.striply.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SecurityTestController.class, IdentityController.class})
@Import(SecurityEncodingConfiguration.class)
class SecurityConfigurationTest {

    private static final String LOGOUT_PATH = "/api/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private AuthService authService;

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAuthenticatedRequest() throws Exception {
        mockMvc.perform(get("/test").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(content().string("test"));
    }

    @Test
    void shouldAllowLoginWithoutAuthenticationOrCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldAllowLogoutWithoutAuthenticationOrCsrfToken() throws Exception {
        mockMvc.perform(post(LOGOUT_PATH))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectTokenWithWrongIssuer() {
        String token = encodeToken("another-issuer", "striply-api");

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectTokenWithWrongAudience() {
        String token = encodeToken("striply", "another-api");

        assertThatThrownBy(() -> jwtDecoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }


    private String encodeToken(String issuer, String audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("10000000-0000-0000-0000-000000000001")
                .audience(List.of(audience))
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(claims)
        ).getTokenValue();
    }
}
