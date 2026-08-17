package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}/live-queue")
public class LiveQueueController {
    private final LiveQueueSessionService sessionService;
    private final LiveQueueEntryService entryService;
    private final LiveQueueOperationService operationService;
    private final RequestAuthenticationService authenticationService;

    public LiveQueueController(
            LiveQueueSessionService sessionService,
            LiveQueueEntryService entryService,
            LiveQueueOperationService operationService,
            RequestAuthenticationService authenticationService
    ) {
        this.sessionService = sessionService;
        this.entryService = entryService;
        this.operationService = operationService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public LiveQueueSessionDto current(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return sessionService.getOperator(roomId, userId(authentication));
    }

    @PostMapping("/open")
    public LiveQueueSessionDto open(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return sessionService.open(roomId, userId(authentication));
    }

    @PostMapping("/close")
    public LiveQueueSessionDto close(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return sessionService.closeAcceptance(roomId, userId(authentication));
    }

    @PostMapping("/automatic")
    public LiveQueueSessionDto automatic(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return sessionService.useAutomaticAcceptance(roomId, userId(authentication));
    }

    @PostMapping("/reset")
    public LiveQueueSessionDto reset(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return sessionService.reset(roomId, userId(authentication));
    }

    @PostMapping("/join")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveQueueJoinResponseDto join(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return entryService.joinUser(roomId, userId(authentication));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveQueueEntryDto addManual(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody LiveQueueManualEntryRequestDto request,
            Authentication authentication
    ) {
        return entryService.addManual(roomId, userId(authentication), request);
    }

    @PutMapping("/entries/{entryId}")
    public LiveQueueEntryDto updateManual(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long entryId,
            @Valid @RequestBody LiveQueueEntryUpdateRequestDto request,
            Authentication authentication
    ) {
        return operationService.updateManual(roomId, entryId, userId(authentication), request);
    }

    @PostMapping("/call-next")
    public LiveQueueSessionDto callNext(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return operationService.callNext(roomId, userId(authentication));
    }

    @PostMapping("/complete-current")
    public LiveQueueSessionDto completeCurrent(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return operationService.completeCurrent(roomId, userId(authentication));
    }

    @PostMapping("/entries/{entryId}/skip")
    public LiveQueueSessionDto skip(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long entryId,
            Authentication authentication
    ) {
        return operationService.skip(roomId, entryId, userId(authentication));
    }

    @PostMapping("/entries/{entryId}/restore")
    public LiveQueueSessionDto restore(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long entryId,
            Authentication authentication
    ) {
        return operationService.restore(roomId, entryId, userId(authentication));
    }

    @PostMapping("/entries/{entryId}/send-to-end")
    public LiveQueueSessionDto sendToEnd(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long entryId,
            Authentication authentication
    ) {
        return operationService.sendToEnd(roomId, entryId, userId(authentication));
    }

    @PostMapping("/entries/{entryId}/remove")
    public LiveQueueSessionDto remove(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long entryId,
            Authentication authentication
    ) {
        return operationService.remove(roomId, entryId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
