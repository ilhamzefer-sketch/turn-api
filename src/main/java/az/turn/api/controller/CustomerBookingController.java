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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api")
public class CustomerBookingController {
    private final CustomerBookingService bookingService;
    private final RequestAuthenticationService authenticationService;

    public CustomerBookingController(
            CustomerBookingService bookingService,
            RequestAuthenticationService authenticationService
    ) {
        this.bookingService = bookingService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public PlannedBookingDto create(
            @Valid @RequestBody BookingCreateRequestDto request,
            Authentication authentication
    ) {
        return bookingService.create(userId(authentication), request);
    }

    @GetMapping("/bookings/{bookingId}")
    public PlannedBookingDto get(
            @PathVariable @Positive long bookingId,
            Authentication authentication
    ) {
        return bookingService.get(bookingId, userId(authentication));
    }

    @GetMapping("/customers/me/bookings")
    public List<PlannedBookingDto> history(Authentication authentication) {
        return bookingService.history(userId(authentication));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public PlannedBookingDto cancel(
            @PathVariable @Positive long bookingId,
            Authentication authentication
    ) {
        return bookingService.cancel(bookingId, userId(authentication));
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    public PlannedBookingDto reschedule(
            @PathVariable @Positive long bookingId,
            @Valid @RequestBody BookingRescheduleRequestDto request,
            Authentication authentication
    ) {
        return bookingService.reschedule(bookingId, userId(authentication), request);
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
