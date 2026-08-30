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
    private final boolean mustChangeCredentials;

    public AdminAccountBootstrap(
            AdminAccountService adminAccountService,
            @Value("${app.admin.bootstrap-username:admin}") String username,
            @Value("${app.admin.bootstrap-password-hash}") String passwordHash,
            @Value("${app.admin.bootstrap-must-change:true}") boolean mustChangeCredentials
    ) {
        this.adminAccountService = adminAccountService;
        this.username = username;
        this.passwordHash = passwordHash;
        this.mustChangeCredentials = mustChangeCredentials;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            adminAccountService.bootstrap(username, passwordHash, mustChangeCredentials);
        } catch (DataIntegrityViolationException exception) {
            if (!adminAccountService.exists(username)) throw exception;
        }
    }
}
