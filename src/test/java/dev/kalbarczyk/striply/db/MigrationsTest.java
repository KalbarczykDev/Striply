package dev.kalbarczyk.striply.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MigrationsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsAreApplied() {
        var appUserTableCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                        AND table_name = 'app_user'
                        """,
                Integer.class
        );


        var migrationCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '1'
                        """,
                Integer.class
        );



        assertThat(appUserTableCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
    }

}
