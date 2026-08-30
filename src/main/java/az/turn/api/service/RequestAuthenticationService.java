package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RequestAuthenticationService {
    private final AdminAccountRepository adminAccountRepository;

    public RequestAuthenticationService(AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    public AuthenticatedUser requireAuthenticated(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Daxil olun.");
        }
        return user;
    }

    public AuthenticatedUser requireUser(Authentication authentication, AuthUserType userType) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        if (user.userType() != userType) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
        }
        if (userType == AuthUserType.ADMIN && adminAccountRepository.findByUsername(user.username())
                .map(AdminAccountEntity::isMustChangeCredentials)
                .orElse(false)) {
            throw new ResponseStatusException(
                    HttpStatus.PRECONDITION_REQUIRED,
                    "Davam etmək üçün ilkin admin istifadəçi adını və şifrəsini dəyişin."
            );
        }
        return user;
    }

    public AuthenticatedUser requireAdminCredentialChange(Authentication authentication) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
        }
        return user;
    }

    public QueueAdvanceRequest buildQueueAdvanceRequest(AuthenticatedUser user) {
        if (user.isRegistration()) {
            return new QueueAdvanceRequest(user.userId(), null);
        }
        if (user.isQueueManager()) {
            return new QueueAdvanceRequest(null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
    }

    public QueueResetRequest buildQueueResetRequest(AuthenticatedUser user) {
        if (user.isRegistration()) {
            return new QueueResetRequest(user.userId(), null);
        }
        if (user.isQueueManager()) {
            return new QueueResetRequest(null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
    }
}
