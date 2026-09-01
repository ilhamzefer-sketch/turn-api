package az.turn.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSupportRequestReviewRequestDto(
        @NotNull SupportRequestStatus status,
        @Size(max = 4000) String response
) {
}
