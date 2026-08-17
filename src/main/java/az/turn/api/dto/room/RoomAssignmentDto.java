package az.turn.api;

import java.time.LocalDateTime;

public record RoomAssignmentDto(
        long id,
        long roomId,
        String roomName,
        long userId,
        String firstName,
        String lastName,
        String phone,
        RoomRole role,
        RoomAssignmentStatus status,
        boolean showPhonePublicly,
        long invitedByUserId,
        LocalDateTime invitedAt,
        LocalDateTime respondedAt
) {
}
