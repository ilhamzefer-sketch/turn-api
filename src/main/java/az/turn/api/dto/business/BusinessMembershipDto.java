package az.turn.api;

import java.time.LocalDateTime;

public record BusinessMembershipDto(
        long id,
        long businessId,
        String businessName,
        long userId,
        String firstName,
        String lastName,
        String phone,
        BusinessRole role,
        BusinessMembershipStatus status,
        long invitedByUserId,
        String invitedFirstName,
        String invitedLastName,
        LocalDateTime invitedAt,
        LocalDateTime acceptedAt
) {
}
