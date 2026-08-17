package az.turn.api;

import java.time.LocalDateTime;

public record UserResponseDto(
        long id,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        LocalDateTime createdAt,
        String accessToken
) {
}
