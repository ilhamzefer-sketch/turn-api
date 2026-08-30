package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserPasswordUpdateRequestDto(
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank @Size(min = 3, max = 500) String reason
) {
}
