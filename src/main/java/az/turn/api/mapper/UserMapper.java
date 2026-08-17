package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDto toDto(UserEntity user, String accessToken) {
        return new UserResponseDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                user.getStatus(),
                user.getCreatedAt(),
                accessToken
        );
    }
}
