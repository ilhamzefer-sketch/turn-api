import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class ListWalletPayments {
    public static void main(String[] args) throws Exception {
        String url = getenv("DB_URL", "jdbc:postgresql://localhost:5432/turn");
        String username = getenv("DB_USERNAME", "postgres");
        String password = getenv("DB_PASSWORD", "password");

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            printCount(statement, "wallet_top_up_requests");
            printCount(statement, "wallet_transactions");

            System.out.println();
            System.out.println("Last wallet top-ups:");
            try (ResultSet rows = statement.executeQuery("""
                    select id, user_id, package_code, amount_azn, coins, status,
                           payment_provider, external_order_id, external_payment_reference,
                           created_at, paid_at
                    from wallet_top_up_requests
                    order by id desc
                    limit 20
                    """)) {
                while (rows.next()) {
                    System.out.printf(
                            "#%s user=%s package=%s amount=%s coins=%s status=%s provider=%s order=%s ref=%s created=%s paid=%s%n",
                            rows.getLong("id"),
                            rows.getLong("user_id"),
                            rows.getString("package_code"),
                            rows.getString("amount_azn"),
                            rows.getLong("coins"),
                            rows.getString("status"),
                            rows.getString("payment_provider"),
                            rows.getString("external_order_id"),
                            rows.getString("external_payment_reference"),
                            rows.getTimestamp("created_at"),
                            rows.getTimestamp("paid_at")
                    );
                }
            }

            System.out.println();
            System.out.println("Last wallet transactions:");
            try (ResultSet rows = statement.executeQuery("""
                    select wt.id, wa.user_id, wt.transaction_type, wt.direction, wt.amount,
                           wt.balance_before, wt.balance_after, wt.reference_key, wt.created_at
                    from wallet_transactions wt
                    join wallet_accounts wa on wa.id = wt.wallet_account_id
                    order by wt.id desc
                    limit 20
                    """)) {
                while (rows.next()) {
                    System.out.printf(
                            "#%s user=%s type=%s direction=%s amount=%s before=%s after=%s ref=%s created=%s%n",
                            rows.getLong("id"),
                            rows.getLong("user_id"),
                            rows.getString("transaction_type"),
                            rows.getString("direction"),
                            rows.getLong("amount"),
                            rows.getLong("balance_before"),
                            rows.getLong("balance_after"),
                            rows.getString("reference_key"),
                            rows.getTimestamp("created_at")
                    );
                }
            }
        }
    }

    private static void printCount(Statement statement, String table) throws Exception {
        try (ResultSet rows = statement.executeQuery("select count(*) from " + table)) {
            rows.next();
            System.out.println(table + ": " + rows.getLong(1));
        }
    }

    private static String getenv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
