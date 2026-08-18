package az.turn.api;

import java.time.LocalDateTime;

public record CurrentUserDto(
        long id,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        LocalDateTime createdAt
) {
}
