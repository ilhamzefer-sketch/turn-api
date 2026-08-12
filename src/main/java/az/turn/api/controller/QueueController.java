package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Validated
public class QueueController {

    private final QueueService queueService;
    private final RequestAuthenticationService requestAuthenticationService;

    public QueueController(QueueService queueService, RequestAuthenticationService requestAuthenticationService) {
        this.queueService = queueService;
        this.requestAuthenticationService = requestAuthenticationService;
    }

    @PostMapping("/api/queues")
    public QueueResponse createQueue(@Valid @RequestBody QueueCreateRequest request, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.REGISTRATION);
        return queueService.createQueue(new QueueCreateRequest(
                user.userId(),
                request.address(),
                request.serviceName(),
                request.categories(),
                request.resetMode(),
                request.resetAt(),
                request.managerUsername(),
                request.managerPassword()
        ));
    }

    @GetMapping("/api/queues/public")
    public List<QueueResponse> getPublicQueues() {
        return queueService.getPublicQueues();
    }

    @PostMapping("/api/queues/scan")
    public QueueScanResponse scanQueue(@Valid @RequestBody QueueScanRequest request) {
        return queueService.scanQueue(request);
    }

    @PostMapping("/api/queues/join")
    public CustomerQueueJoinResponse joinQueue(@Valid @RequestBody CustomerQueueJoinRequest request, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.joinQueue(new CustomerQueueJoinRequest(user.userId(), request.queueId(), request.qrToken(), request.displayName()));
    }

    @PostMapping("/api/queues/{queueId}/next")
    public QueueDetailResponse advanceQueue(@PathVariable @Positive long queueId, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireAuthenticated(authentication);
        return queueService.advanceQueue(queueId, requestAuthenticationService.buildQueueAdvanceRequest(user));
    }

    @PostMapping("/api/queues/{queueId}/reset")
    public QueueDetailResponse resetQueue(@PathVariable @Positive long queueId, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireAuthenticated(authentication);
        return queueService.resetQueue(queueId, requestAuthenticationService.buildQueueResetRequest(user));
    }

    @GetMapping("/api/queues/{queueId}")
    public QueueDetailResponse getQueueDetail(@PathVariable @Positive long queueId, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireAuthenticated(authentication);
        if (user.isRegistration()) {
            return queueService.getQueueDetail(queueId, user.userId(), null);
        }
        if (user.isQueueManager()) {
            return queueService.getQueueDetail(queueId, null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbe detalina baxmaq icazeniz yoxdur.");
    }
}
