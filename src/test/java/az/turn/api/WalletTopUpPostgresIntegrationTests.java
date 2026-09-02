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
class WalletTopUpPostgresIntegrationTests {
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
    void migratesTheFixedPackageCatalog() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select amount_azn, coin_amount from wallet_top_up_packages "
                             + "where active = true order by display_order"
             )) {
            int[] amounts = {3, 5, 10, 15, 20};
            long[] coins = {30, 50, 100, 150, 200};
            for (int index = 0; index < amounts.length; index++) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("amount_azn")).isEqualTo(amounts[index]);
                assertThat(result.getLong("coin_amount")).isEqualTo(coins[index]);
            }
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void enforcesOneActiveRequestAndValidStateTransitions() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long userId = insertUser(statement, "+994501293404");
            insertAwaitingRequest(statement, userId, "AZN_3", "2026-08-31 12:00:00");

            assertThrows(
                    SQLException.class,
                    () -> insertAwaitingRequest(statement, userId, "AZN_5", "2026-08-31 12:01:00")
            );
            assertThrows(
                    SQLException.class,
                    () -> statement.executeUpdate(
                            "update wallet_top_up_requests set status = 'PENDING_REVIEW' where user_id = " + userId
                    )
            );
        }
    }

    @Test
    void rejectsAmountsAndLinksOutsideTheFixedCatalog() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "insert into wallet_top_up_packages "
                            + "(code, amount_azn, coin_amount, payment_url, display_order, active) values "
                            + "('AZN_7', 7, 70, 'https://example.com/7', 6, true)"
            ));
        }
    }

    @Test
    void preparesFraudCountAndTopUpReversalConstraints() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long userId = insertUser(statement, "+994501293405");
            assertThat(readFraudCount(statement, userId)).isZero();
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "update users set confirmed_wallet_fraud_count = -1 where id = " + userId
            ));

            long walletAccountId = insertWalletAccount(statement, userId);
            statement.executeUpdate(
                    "insert into wallet_transactions "
                            + "(wallet_account_id, transaction_type, direction, amount, balance_before, balance_after, "
                            + "actor_type, actor_reference, reference_key, created_at) values ("
                            + walletAccountId + ", 'TOP_UP_REVERSAL', 'DEBIT', 30, 30, 0, "
                            + "'SYSTEM', 'fraud-review-test', 'top-up-reversal:valid', current_timestamp)"
            );
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "insert into wallet_transactions "
                            + "(wallet_account_id, transaction_type, direction, amount, balance_before, balance_after, "
                            + "actor_type, actor_reference, reference_key, created_at) values ("
                            + walletAccountId + ", 'TOP_UP_REVERSAL', 'CREDIT', 30, 0, 30, "
                            + "'SYSTEM', 'fraud-review-test', 'top-up-reversal:invalid', current_timestamp)"
            ));
        }
    }

    private long insertUser(Statement statement, String phone) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "insert into users (first_name, last_name, normalized_phone, password_hash, status) "
                        + "values ('Top up', 'Postgres', '" + phone + "', 'hash', 'ACTIVE') returning id"
        )) {
            result.next();
            return result.getLong(1);
        }
    }

    private int readFraudCount(Statement statement, long userId) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "select confirmed_wallet_fraud_count from users where id = " + userId
        )) {
            result.next();
            return result.getInt(1);
        }
    }

    private long insertWalletAccount(Statement statement, long userId) throws SQLException {
        try (ResultSet result = statement.executeQuery(
                "insert into wallet_accounts (user_id, balance, currency, version, created_at, updated_at) values ("
                        + userId + ", 30, 'COIN', 0, current_timestamp, current_timestamp) returning id"
        )) {
            result.next();
            return result.getLong(1);
        }
    }

    private void insertAwaitingRequest(
            Statement statement,
            long userId,
            String packageCode,
            String clickedAt
    ) throws SQLException {
        statement.executeUpdate(
                "insert into wallet_top_up_requests "
                        + "(user_id, active_user_id, package_code, amount_azn, coin_amount, currency, payment_url, "
                        + "status, clicked_at, receipt_deadline_at, created_at, updated_at) "
                        + "select " + userId + ", " + userId + ", code, amount_azn, coin_amount, 'AZN', payment_url, "
                        + "'AWAITING_RECEIPT', timestamp '" + clickedAt + "', "
                        + "timestamp '" + clickedAt + "' + interval '30 minutes', "
                        + "timestamp '" + clickedAt + "', timestamp '" + clickedAt + "' "
                        + "from wallet_top_up_packages where code = '" + packageCode + "'"
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
