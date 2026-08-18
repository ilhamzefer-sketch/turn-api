package az.turn.api;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/users/me")
public class UserAccountController {

    private final RequestAuthenticationService requestAuthenticationService;
    private final UserSessionService userSessionService;
    private final QueueService queueService;
    private final LiveQueueEntryService liveQueueEntryService;
    private final CurrentUserQueryService currentUserQueryService;

    public UserAccountController(
            RequestAuthenticationService requestAuthenticationService,
            UserSessionService userSessionService,
            QueueService queueService,
            LiveQueueEntryService liveQueueEntryService,
            CurrentUserQueryService currentUserQueryService
    ) {
        this.requestAuthenticationService = requestAuthenticationService;
        this.userSessionService = userSessionService;
        this.queueService = queueService;
        this.liveQueueEntryService = liveQueueEntryService;
        this.currentUserQueryService = currentUserQueryService;
    }

    @GetMapping
    public CurrentUserDto getCurrentUser(Authentication authentication) {
        return currentUserQueryService.get(requireUser(authentication).userId());
    }

    @GetMapping("/sessions")
    public List<UserSessionDto> getSessions(Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);
        return userSessionService.getActiveSessions(user.userId(), user.sessionId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @PathVariable @Positive long sessionId,
            Authentication authentication
    ) {
        AuthenticatedUser user = requireUser(authentication);
        userSessionService.revokeSession(user.userId(), sessionId);
    }

    @DeleteMapping("/sessions/others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOtherSessions(Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);
        userSessionService.revokeOtherSessions(user.userId(), user.sessionId());
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);
        userSessionService.revokeAllSessions(user.userId());
    }

    @GetMapping("/queue-history")
    public List<UserQueueHistoryItemDto> getQueueHistory(Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);
        return queueService.getUserHistory(user.userId());
    }

    @GetMapping("/live-queue-history")
    public List<LiveQueueHistoryItemDto> getLiveQueueHistory(Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication);
        return liveQueueEntryService.getUserHistory(user.userId());
    }

    private AuthenticatedUser requireUser(Authentication authentication) {
        return requestAuthenticationService.requireUser(authentication, AuthUserType.USER);
    }
}
