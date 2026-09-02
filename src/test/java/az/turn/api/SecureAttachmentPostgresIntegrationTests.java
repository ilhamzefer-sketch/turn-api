package az.turn.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class SecureAttachmentPostgresIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeEach
    void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    @Test
    void persistsOnlyCleanJpegAndPngMetadataWithinSecurityLimits() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long userId = insertUser(statement);
            insertAttachment(statement, userId, "image/png", "png", 128, 100, 100, "CLEAN");

            try (ResultSet result = statement.executeQuery(
                    "select purpose, scan_status from secure_attachments where owner_user_id = " + userId
            )) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("purpose")).isEqualTo("PAYMENT_RECEIPT");
                assertThat(result.getString("scan_status")).isEqualTo("CLEAN");
            }

            assertThrows(
                    SQLException.class,
                    () -> insertAttachment(statement, userId, "image/svg+xml", "svg", 128, 100, 100, "CLEAN")
            );
            assertThrows(
                    SQLException.class,
                    () -> insertAttachment(statement, userId, "image/png", "png", 128, 10001, 1, "CLEAN")
            );
            assertThrows(
                    SQLException.class,
                    () -> insertAttachment(statement, userId, "image/png", "png", 128, 100, 100, "PENDING")
            );
        }
    }

    @Test
    void allowsPdfOnlyForPaymentReceiptsWithoutImageDimensions() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long userId = insertUser(statement);
            insertAttachment(statement, userId, "PAYMENT_RECEIPT", "application/pdf", "pdf", 256, 0, 0, "CLEAN");

            assertThrows(
                    SQLException.class,
                    () -> insertAttachment(
                            statement, userId, "SUPPORT_REQUEST", "application/pdf", "pdf", 256, 0, 0, "CLEAN"
                    )
            );
            assertThrows(
                    SQLException.class,
                    () -> insertAttachment(
                            statement, userId, "PAYMENT_RECEIPT", "application/pdf", "pdf", 256, 1, 1, "CLEAN"
                    )
            );
        }
    }

    private long insertUser(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "insert into users (first_name, last_name, normalized_phone, password_hash, status) "
                        + "values ('Secure', 'Attachment', '+994501293502', 'hash', 'ACTIVE') returning id"
        )) {
            result.next();
            return result.getLong(1);
        }
    }

    private void insertAttachment(
            Statement statement,
            long userId,
            String mediaType,
            String extension,
            long size,
            int width,
            int height,
            String scanStatus
    ) throws SQLException {
        insertAttachment(
                statement,
                userId,
                "PAYMENT_RECEIPT",
                mediaType,
                extension,
                size,
                width,
                height,
                scanStatus
        );
    }

    private void insertAttachment(
            Statement statement,
            long userId,
            String purpose,
            String mediaType,
            String extension,
            long size,
            int width,
            int height,
            String scanStatus
    ) throws SQLException {
        String key = String.format("ab/ab123456-1234-1234-1234-%012d.%s", System.nanoTime() % 1_000_000_000_000L, extension);
        statement.executeUpdate(
                "insert into secure_attachments "
                        + "(owner_user_id, purpose, storage_key, original_filename, media_type, file_extension, "
                        + "size_bytes, width_pixels, height_pixels, sha256, scan_status, scanned_at, created_at) values ("
                        + userId + ", '" + purpose + "', '" + key + "', 'receipt." + extension + "', '"
                        + mediaType + "', '" + extension + "', " + size + ", " + width + ", " + height + ", '"
                        + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', '"
                        + scanStatus + "', current_timestamp, current_timestamp)"
        );
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
