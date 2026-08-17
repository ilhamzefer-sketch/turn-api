package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportResolutionRequestDto(
        boolean approve,
        @NotBlank @Size(max = 2000) String resolutionNote
) {
}
