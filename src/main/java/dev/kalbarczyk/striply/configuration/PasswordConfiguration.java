package dev.kalbarczyk.striply.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

        return new DelegatingPasswordEncoder(
                "bcrypt",
                Map.of("bcrypt", bcrypt)
        );
    }
}
