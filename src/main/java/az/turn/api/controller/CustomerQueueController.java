package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class CustomerQueueController {

    private final QueueService queueService;
    private final RequestAuthenticationService requestAuthenticationService;

    public CustomerQueueController(QueueService queueService, RequestAuthenticationService requestAuthenticationService) {
        this.queueService = queueService;
        this.requestAuthenticationService = requestAuthenticationService;
    }

    @GetMapping("/api/customers/{customerId}/history")
    public List<CustomerQueueHistoryItemResponse> getCustomerHistory(@PathVariable @Positive long customerId, Authentication authentication) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.CUSTOMER);
        if (!user.userId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu tarixceye baxmaq icazeniz yoxdur.");
        }
        return queueService.getCustomerHistory(customerId);
    }

    @PostMapping("/api/customer-queue-entries/{entryId}/rename")
    public CustomerQueueEntryResponse renameCustomerQueueEntry(
            @PathVariable @Positive long entryId,
            @Valid @RequestBody CustomerQueueRenameRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.renameCustomerQueueEntry(entryId, new CustomerQueueRenameRequest(user.userId(), request.displayName()));
    }

    @PostMapping("/api/customer-queue-entries/{entryId}/rating")
    public CustomerQueueEntryResponse rateCustomerQueueEntry(
            @PathVariable @Positive long entryId,
            @Valid @RequestBody CustomerQueueRatingRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = requestAuthenticationService.requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.rateCustomerQueueEntry(entryId, new CustomerQueueRatingRequest(user.userId(), request.rating(), request.note()));
    }
}
