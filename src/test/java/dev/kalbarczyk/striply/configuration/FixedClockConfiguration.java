package dev.kalbarczyk.striply.configuration;

import lombok.AllArgsConstructor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

@TestConfiguration
public class FixedClockConfiguration {

    public static final Instant NOW =
            Instant.parse("2026-08-09T18:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(NOW, ZoneOffset.UTC);
    }

    @AllArgsConstructor
    public static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public void setInstant(Instant instant) {
            this.instant = Objects.requireNonNull(instant);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }
    }
}
