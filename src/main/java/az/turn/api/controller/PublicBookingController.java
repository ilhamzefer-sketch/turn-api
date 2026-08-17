package az.turn.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/public/rooms/{roomId}/available-slots")
public class PublicBookingController {
    private final PlannedBookingAvailabilityService availabilityService;

    public PublicBookingController(PlannedBookingAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailableSlotDto> availableSlots(
            @PathVariable @Positive long roomId,
            @RequestParam
            @NotNull(message = "Tarix mütləqdir.")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return availabilityService.getPublicSlots(roomId, date);
    }
}
