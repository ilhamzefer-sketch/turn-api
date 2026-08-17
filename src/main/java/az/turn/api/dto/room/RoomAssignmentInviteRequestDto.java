package az.turn.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RoomAssignmentInviteRequestDto(
        @NotNull(message = "İstifadəçi ID-si mütləqdir.")
        @Positive(message = "İstifadəçi ID-si müsbət olmalıdır.")
        Long userId
) {
}
