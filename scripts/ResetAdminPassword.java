import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ResetAdminPassword {
    public static void main(String[] args) throws Exception {
        String url = getenv("DB_URL", "jdbc:postgresql://localhost:5432/turn");
        String username = getenv("DB_USERNAME", "postgres");
        String password = getenv("DB_PASSWORD", "password");
        String adminUsername = getenv("RESET_ADMIN_USERNAME", "admin");
        String adminPasswordHash = getenv(
                "RESET_ADMIN_PASSWORD_HASH",
                "$2y$10$3Ehrpe5CUmnRa79/msUu3O2mQ.qwbdj.e/zefTCP8wfrqhxwHM/LG"
        );

        String sql = """
                do $$
                declare
                  target_admin_id bigint;
                begin
                  select id into target_admin_id
                  from admin_accounts
                  where username = '%s'
                  order by id asc
                  limit 1;

                  if target_admin_id is null then
                    select id into target_admin_id
                    from admin_accounts
                    order by id asc
                    limit 1;
                  end if;

                  if target_admin_id is null then
                    insert into admin_accounts (
                      username,
                      display_name,
                      password_hash,
                      active,
                      must_change_credentials,
                      credentials_changed_at,
                      created_by_username,
                      created_at,
                      updated_at
                    ) values (
                      '%s',
                      'Bas administrator',
                      '%s',
                      true,
                      false,
                      now(),
                      null,
                      now(),
                      now()
                    );
                  else
                    update admin_accounts
                    set username = '%s',
                        display_name = 'Bas administrator',
                        password_hash = '%s',
                        active = true,
                        must_change_credentials = false,
                        credentials_changed_at = now(),
                        updated_at = now()
                    where id = target_admin_id;
                  end if;
                end
                $$;
                """.formatted(
                escapeSql(adminUsername),
                escapeSql(adminUsername),
                escapeSql(adminPasswordHash),
                escapeSql(adminUsername),
                escapeSql(adminPasswordHash)
        );

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String getenv(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String escapeSql(String value) {
        return value.replace("'", "''");
    }
}
