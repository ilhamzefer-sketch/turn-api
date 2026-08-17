package az.turn.api;

import jakarta.validation.constraints.NotNull;

public record BusinessMembershipUpdateRequestDto(
        @NotNull(message = "Biznes rolu mütləqdir.")
        BusinessRole role
) {
}
