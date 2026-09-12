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
import java.math.BigDecimal;

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
            BigDecimal[] amounts = {new BigDecimal("3.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("15.00"), new BigDecimal("20.00")};
            long[] coins = {30, 50, 100, 150, 200};
            for (int index = 0; index < amounts.length; index++) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal("amount_azn")).isEqualByComparingTo(amounts[index]);
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

    @Test
    void forwardMigrationPreservesHistoricalAmountsAndReplacedOrders() throws Exception {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("42").load().migrate();
        long manualUser;
        long replacedUser;
        long originalUser;
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            manualUser = insertUser(statement, "+994509940001");
            replacedUser = insertUser(statement, "+994509940002");
            originalUser = insertUser(statement, "+994509940003");
            insertAwaitingRequest(statement, manualUser, "AZN_3", "2026-08-31 12:00:00");
            insertAwaitingRequest(statement, replacedUser, "AZN_3", "2026-08-31 12:00:00");
            insertAwaitingRequest(statement, originalUser, "AZN_3", "2026-08-31 12:00:00");
            statement.executeUpdate("update wallet_top_up_requests set amount_azn=3.00, coin_amount=30 where user_id=" + originalUser);
            statement.executeUpdate("update wallet_top_up_requests set payment_provider='epoint', "
                    + "external_order_id='wallet-123-456', external_payment_status='replaced_by_new_request', "
                    + "external_payment_reference='REPLACED-123', active_user_id=null, status='PAYMENT_FAILED' where user_id=" + replacedUser);
        }
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select user_id, amount_azn, coin_amount, status, payment_provider, checkout_state "
                     + "from wallet_top_up_requests order by user_id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("user_id")).isEqualTo(manualUser);
            assertThat(result.getBigDecimal("amount_azn")).isEqualByComparingTo("0.10");
            assertThat(result.getLong("coin_amount")).isEqualTo(1);
            assertThat(result.getString("checkout_state")).isEqualTo("NOT_REQUIRED");
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("user_id")).isEqualTo(replacedUser);
            assertThat(result.getBigDecimal("amount_azn")).isEqualByComparingTo("0.10");
            assertThat(result.getString("status")).isEqualTo("SUPERSEDED");
            assertThat(result.getString("checkout_state")).isEqualTo("READY");
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("user_id")).isEqualTo(originalUser);
            assertThat(result.getBigDecimal("amount_azn")).isEqualByComparingTo("3.00");
            assertThat(result.getLong("coin_amount")).isEqualTo(30);
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
                "insert into wallet_accounts (user_id, balance, version, created_at, updated_at) values ("
                        + userId + ", 30, 0, current_timestamp, current_timestamp) returning id"
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
