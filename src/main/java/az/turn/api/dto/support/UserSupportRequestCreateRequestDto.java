package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserSupportRequestCreateRequestDto(
        @NotNull UserSupportRequestType requestType,
        @NotBlank @Size(max = 4000) String message
) {
}
