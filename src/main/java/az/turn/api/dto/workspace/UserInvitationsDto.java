package az.turn.api;

import java.util.List;

public record UserInvitationsDto(
        List<BusinessMembershipDto> businessInvitations,
        List<RoomAssignmentDto> roomInvitations
) {
}
