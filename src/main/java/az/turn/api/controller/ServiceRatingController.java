package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api")
public class ServiceRatingController {
    private final ServiceRatingService ratingService;
    private final RequestAuthenticationService authenticationService;

    public ServiceRatingController(
            ServiceRatingService ratingService,
            RequestAuthenticationService authenticationService
    ) {
        this.ratingService = ratingService;
        this.authenticationService = authenticationService;
    }

    @PutMapping("/users/me/ratings/live-queue/{entryId}")
    public ServiceRatingDto rateLive(
            @PathVariable @Positive long entryId,
            @Valid @RequestBody RatingUpsertRequestDto request,
            Authentication authentication
    ) {
        return ratingService.upsertLive(userId(authentication), entryId, request);
    }

    @PutMapping("/users/me/ratings/planned-bookings/{bookingId}")
    public ServiceRatingDto rateBooking(
            @PathVariable @Positive long bookingId,
            @Valid @RequestBody RatingUpsertRequestDto request,
            Authentication authentication
    ) {
        return ratingService.upsertBooking(userId(authentication), bookingId, request);
    }

    @GetMapping("/public/rooms/{roomId}/rating-summary")
    public RoomRatingSummaryDto summary(@PathVariable @Positive long roomId) {
        return ratingService.summary(roomId);
    }

    @GetMapping("/rooms/{roomId}/ratings")
    public List<ServiceRatingDto> roomRatings(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return ratingService.roomRatings(roomId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
