package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {
    private final AdminAccountService adminAccountService;
    private final String username;
    private final String passwordHash;

    public AdminAccountBootstrap(
            AdminAccountService adminAccountService,
            @Value("${app.admin.username:admin}") String username,
            @Value("${app.admin.password-hash}") String passwordHash
    ) {
        this.adminAccountService = adminAccountService;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            adminAccountService.bootstrap(username, passwordHash);
        } catch (DataIntegrityViolationException exception) {
            if (!adminAccountService.exists(username)) throw exception;
        }
    }
}
