package az.turn.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserPostgresIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesUnifiedAccountMigrationAndEnforcesUniquePhone() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
        userRepository.saveAndFlush(activeUser("+994505556677"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(activeUser("+994505556677"))
        );
    }

    @Test
    void upgradesPopulatedVersionTwelveSchemaWithoutDeletingLegacyRows() throws Exception {
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test")
                .defaultSchema("upgrade_test")
                .target(MigrationVersion.fromVersion("12"))
                .load();
        legacyFlyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to upgrade_test");
            statement.executeUpdate("insert into registrations "
                    + "(first_name, last_name, email, password_hash, paid, payment_reference, registration_type, created_at, status) "
                    + "values ('Legacy', 'Owner', 'legacy@example.com', 'hash', true, 'REF', 'FERDI', current_timestamp, 'ACTIVE')");
            statement.executeUpdate("insert into queues "
                    + "(registration_id, address, service_name, qr_token, current_queue_number) "
                    + "values (1, 'Baku', 'Legacy queue', 'legacy-qr', 0)");
            statement.executeUpdate("insert into guest_queue_entries (queue_id, queue_number, first_name, last_name) "
                    + "values (1, 1, 'Legacy', 'Guest')");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test")
                .defaultSchema("upgrade_test")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to upgrade_test");
            try (ResultSet result = statement.executeQuery(
                    "select count(*) from guest_queue_entries where normalized_phone is null"
            )) {
                result.next();
                assertThat(result.getLong(1)).isEqualTo(1);
            }
        }
    }

    private UserEntity activeUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Postgres");
        user.setLastName("Test");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("{bcrypt-sha256}$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
