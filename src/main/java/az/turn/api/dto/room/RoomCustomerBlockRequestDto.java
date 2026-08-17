package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RoomCustomerBlockRequestDto(
        @Positive long customerUserId,
        @NotBlank @Size(max = 1000) String reason
) {
}
