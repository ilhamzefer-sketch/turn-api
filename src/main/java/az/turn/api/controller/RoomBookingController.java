package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}/bookings")
public class RoomBookingController {
    private final RoomBookingService bookingService;
    private final RequestAuthenticationService authenticationService;

    public RoomBookingController(
            RoomBookingService bookingService,
            RequestAuthenticationService authenticationService
    ) {
        this.bookingService = bookingService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public List<PlannedBookingDto> list(
            @PathVariable @Positive long roomId,
            @RequestParam
            @NotNull(message = "Tarix mütləqdir.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            Authentication authentication
    ) {
        return bookingService.list(roomId, userId(authentication), date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedBookingDto createManual(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody BookingManualCreateRequestDto request,
            Authentication authentication
    ) {
        return bookingService.createManual(roomId, userId(authentication), request);
    }

    @PostMapping("/{bookingId}/cancel")
    public PlannedBookingDto cancel(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long bookingId,
            @Valid @RequestBody BookingOperatorCancelRequestDto request,
            Authentication authentication
    ) {
        return bookingService.cancel(roomId, bookingId, userId(authentication), request);
    }

    @PostMapping("/{bookingId}/no-show")
    public PlannedBookingDto noShow(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long bookingId,
            Authentication authentication
    ) {
        return bookingService.noShow(roomId, bookingId, userId(authentication));
    }

    @PostMapping("/{bookingId}/complete")
    public PlannedBookingDto complete(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long bookingId,
            Authentication authentication
    ) {
        return bookingService.complete(roomId, bookingId, userId(authentication));
    }

    @PostMapping("/{bookingId}/reschedule")
    public PlannedBookingDto reschedule(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long bookingId,
            @Valid @RequestBody BookingOperatorRescheduleRequestDto request,
            Authentication authentication
    ) {
        return bookingService.reschedule(roomId, bookingId, userId(authentication), request);
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
