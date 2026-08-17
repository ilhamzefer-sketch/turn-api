package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/public")
public class PublicLiveQueueController {
    private final LiveQueueSessionService sessionService;
    private final LiveQueueEntryService entryService;
    private final QrCredentialService qrCredentialService;

    public PublicLiveQueueController(
            LiveQueueSessionService sessionService,
            LiveQueueEntryService entryService,
            QrCredentialService qrCredentialService
    ) {
        this.sessionService = sessionService;
        this.entryService = entryService;
        this.qrCredentialService = qrCredentialService;
    }

    @GetMapping("/rooms/{roomId}/live-queue")
    public LiveQueuePublicDto room(@PathVariable @Positive long roomId) {
        return sessionService.getPublic(roomId);
    }

    @PostMapping("/rooms/{roomId}/live-queue/join")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveQueueJoinResponseDto joinRoom(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody LiveQueueJoinRequestDto request
    ) {
        return entryService.joinGuest(roomId, request, LiveQueueEntrySource.WEB);
    }

    @GetMapping("/qr/{token}/live-queue")
    public LiveQueuePublicDto qr(@PathVariable @NotBlank String token) {
        return sessionService.getPublic(qrCredentialService.resolveActiveRoom(token).getId());
    }

    @PostMapping("/qr/{token}/live-queue/join")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveQueueJoinResponseDto joinQr(
            @PathVariable @NotBlank String token,
            @Valid @RequestBody LiveQueueJoinRequestDto request
    ) {
        RoomEntity room = qrCredentialService.resolveActiveRoom(token);
        return entryService.joinGuest(room.getId(), request, LiveQueueEntrySource.QR);
    }

    @GetMapping("/live-queue/entries/{publicReference}")
    public LiveQueueParticipantStatusDto participant(
            @PathVariable @NotBlank String publicReference
    ) {
        return entryService.getParticipantStatus(publicReference);
    }
}
