package az.turn.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SubscriptionBankRetirementPostgresIntegrationTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void retiresPendingSubscriptionBankSessionsDuringUpgrade() throws Exception {
        migrateToVersionThirtyOne();
        insertPendingSubscriptionBankSession();
        migrateToLatest();
        assertSessionRetired();
    }

    @Test
    void preservesLegacyActiveSubscriptionReceiptAndExistingRoomCapacity() throws Exception {
        String schema = "coin_upgrade_test";
        migrateSchemaToVersion(schema, "28");
        insertLegacyActiveBusiness(schema);
        migrateSchemaToLatest(schema);
        assertLegacyBusinessPreserved(schema);
    }

    private void migrateToVersionThirtyOne() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target(MigrationVersion.fromVersion("31"))
                .load()
                .migrate();
    }

    private void insertPendingSubscriptionBankSession() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("insert into provider_subscriptions "
                    + "(scope_type, scope_id, plan_id, billing_period, status, room_limit, employee_limit, created_at, updated_at) "
                    + "select 'BUSINESS', 999, id, 'MONTHLY', 'PENDING_PAYMENT', 5, 500, current_timestamp, current_timestamp "
                    + "from subscription_plans where code = 'BUSINESS_MONTHLY'");
            statement.executeUpdate("insert into payment_sessions "
                    + "(session_token, provider, payment_mode, status, amount, currency, card_holder, card_last4, "
                    + "sandbox_outcome, payment_reference, created_at, payment_purpose, provider_subscription_id, "
                    + "subscription_plan_id, external_order_id, external_order_password, external_hpp_url) "
                    + "select 'sha256:retired', 'birbank', 'live', 'PENDING', 10, 'AZN', 'Holder', '1234', "
                    + "'SUCCESS', 'LEGACY-SUB', current_timestamp, 'PROVIDER_SUBSCRIPTION', subscription.id, plan.id, "
                    + "'100', 'secret', 'https://pay.example.com' from provider_subscriptions subscription "
                    + "join subscription_plans plan on plan.id = subscription.plan_id where subscription.scope_id = 999");
        }
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    private void migrateSchemaToVersion(String schema, String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private void migrateSchemaToLatest(String schema) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();
    }

    private void insertLegacyActiveBusiness(String schema) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to " + schema);
            statement.executeUpdate("insert into users "
                    + "(first_name, last_name, normalized_phone, password_hash, status) "
                    + "values ('Legacy', 'Business', '+994501590001', 'hash', 'ACTIVE')");
            statement.executeUpdate("insert into businesses "
                    + "(primary_owner_user_id, name, normalized_phone, timezone, status) "
                    + "select id, 'Legacy business', '+994501590001', 'Asia/Baku', 'ACTIVE' "
                    + "from users where normalized_phone = '+994501590001'");
            statement.executeUpdate("insert into branches "
                    + "(business_id, name, address, city, district, timezone, status) "
                    + "select id, 'Main', 'Nizami 1', 'Baku', 'Nasimi', 'Asia/Baku', 'ACTIVE' "
                    + "from businesses where name = 'Legacy business'");
            statement.executeUpdate("insert into rooms "
                    + "(branch_id, created_by_user_id, name, timezone, reservation_mode, "
                    + "default_slot_duration_minutes, status, visibility, "
                    + "live_queue_reset_policy, live_queue_reset_local_time) "
                    + "select branch.id, owner.id, 'Legacy room ' || room_number, 'Asia/Baku', "
                    + "'LIVE_QUEUE', 30, 'PUBLISHED', 'UNLISTED', 'DAILY_AT_TIME', time '00:00:00' "
                    + "from branches branch "
                    + "join businesses business on business.id = branch.business_id "
                    + "join users owner on owner.id = business.primary_owner_user_id "
                    + "cross join generate_series(1, 7) room_number "
                    + "where business.name = 'Legacy business'");
            statement.executeUpdate("insert into provider_subscriptions "
                    + "(scope_type, scope_id, plan_id, billing_period, status, room_limit, employee_limit, "
                    + "starts_at, expires_at, grace_ends_at, created_at, updated_at) "
                    + "select 'BUSINESS', business.id, plan.id, 'MONTHLY', 'ACTIVE', 100, 500, "
                    + "timestamp '2026-08-01 00:00:00', timestamp '2026-09-01 00:00:00', "
                    + "timestamp '2026-09-08 00:00:00', current_timestamp, current_timestamp "
                    + "from businesses business cross join subscription_plans plan "
                    + "where business.name = 'Legacy business' and plan.code = 'STANDARD_MONTHLY'");
            statement.executeUpdate("insert into payment_sessions "
                    + "(session_token, provider, payment_mode, status, amount, currency, card_holder, card_last4, "
                    + "sandbox_outcome, payment_reference, created_at, completed_at, payment_purpose, "
                    + "provider_subscription_id, subscription_plan_id, external_order_id, "
                    + "external_order_password, external_hpp_url) "
                    + "select 'sha256:legacy-completed', 'birbank', 'live', 'COMPLETED', 20, 'AZN', "
                    + "'Legacy Holder', '4321', 'SUCCESS', 'LEGACY-COMPLETED', "
                    + "timestamp '2026-08-01 00:00:00', timestamp '2026-08-01 00:01:00', "
                    + "'PROVIDER_SUBSCRIPTION', subscription.id, plan.id, 'legacy-order', 'legacy-secret', "
                    + "'https://pay.example.com/legacy' from provider_subscriptions subscription "
                    + "join subscription_plans plan on plan.id = subscription.plan_id "
                    + "where subscription.scope_type = 'BUSINESS'");
        }
    }

    private void assertLegacyBusinessPreserved(String schema) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to " + schema);
            try (ResultSet result = statement.executeQuery(
                    "select subscription.status, subscription.room_limit, subscription.starts_at, "
                            + "subscription.expires_at, plan.code from provider_subscriptions subscription "
                            + "join subscription_plans plan on plan.id = subscription.plan_id "
                            + "where subscription.scope_type = 'BUSINESS'"
            )) {
                result.next();
                assertThat(result.getString("status")).isEqualTo("ACTIVE");
                assertThat(result.getInt("room_limit")).isEqualTo(7);
                assertThat(result.getTimestamp("starts_at"))
                        .isEqualTo(Timestamp.valueOf("2026-08-01 00:00:00"));
                assertThat(result.getTimestamp("expires_at"))
                        .isEqualTo(Timestamp.valueOf("2026-09-01 00:00:00"));
                assertThat(result.getString("code")).isEqualTo("BUSINESS_MONTHLY");
            }
            try (ResultSet result = statement.executeQuery(
                    "select status, payment_reference, completed_at from payment_sessions "
                            + "where payment_reference = 'LEGACY-COMPLETED'"
            )) {
                result.next();
                assertThat(result.getString("status")).isEqualTo("COMPLETED");
                assertThat(result.getString("payment_reference")).isEqualTo("LEGACY-COMPLETED");
                assertThat(result.getTimestamp("completed_at")).isNotNull();
            }
        }
    }

    private void assertSessionRetired() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select status, completed_at, external_order_password from payment_sessions "
                             + "where payment_reference = 'LEGACY-SUB'"
             )) {
            result.next();
            assertThat(result.getString("status")).isEqualTo("CANCELLED");
            assertThat(result.getTimestamp("completed_at")).isNotNull();
            assertThat(result.getString("external_order_password")).isNull();
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
