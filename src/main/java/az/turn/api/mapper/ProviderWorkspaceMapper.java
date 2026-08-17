package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class ProviderWorkspaceMapper {

    public IndividualWorkspaceResponseDto toDto(IndividualWorkspaceEntity workspace) {
        return new IndividualWorkspaceResponseDto(
                workspace.getId(),
                workspace.getOwnerUser().getId(),
                workspace.getName(),
                workspace.getTimezone(),
                workspace.getStatus(),
                workspace.getCreatedAt(),
                workspace.getArchivedAt()
        );
    }

    public BusinessResponseDto toDto(BusinessEntity business) {
        return new BusinessResponseDto(
                business.getId(),
                business.getPrimaryOwnerUser().getId(),
                business.getName(),
                business.getLegalName(),
                business.getDescription(),
                business.getTaxId(),
                business.getLogoUrl(),
                business.getNormalizedPhone(),
                business.getTimezone(),
                business.getStatus(),
                business.getCreatedAt(),
                business.getArchivedAt()
        );
    }

    public BusinessMembershipDto toDto(BusinessMembershipEntity membership) {
        UserEntity user = membership.getUser();
        return new BusinessMembershipDto(
                membership.getId(),
                membership.getBusiness().getId(),
                membership.getBusiness().getName(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                membership.getRole(),
                membership.getStatus(),
                membership.getInvitedByUser().getId(),
                membership.getInvitedFirstName(),
                membership.getInvitedLastName(),
                membership.getInvitedAt(),
                membership.getAcceptedAt()
        );
    }

    public BranchResponseDto toDto(BranchEntity branch) {
        String effectivePhone = branch.getNormalizedPhone() == null
                ? branch.getBusiness().getNormalizedPhone()
                : branch.getNormalizedPhone();
        return new BranchResponseDto(
                branch.getId(),
                branch.getBusiness().getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getCity(),
                branch.getDistrict(),
                branch.getLatitude(),
                branch.getLongitude(),
                branch.getNormalizedPhone(),
                effectivePhone,
                branch.getNotes(),
                branch.getTimezone(),
                branch.getStatus(),
                branch.getCreatedAt(),
                branch.getArchivedAt()
        );
    }

    public RoomResponseDto toDto(RoomEntity room) {
        BranchEntity branch = room.getBranch();
        IndividualWorkspaceEntity workspace = room.getIndividualWorkspace();
        return new RoomResponseDto(
                room.getId(),
                branch == null ? null : branch.getBusiness().getId(),
                branch == null ? null : branch.getId(),
                workspace == null ? null : workspace.getId(),
                room.getCreatedByUser().getId(),
                room.getName(),
                room.getRoomNumberOrCode(),
                room.getDescription(),
                room.getNotes(),
                room.getTimezone(),
                room.getReservationMode(),
                room.getDefaultSlotDurationMinutes(),
                room.getAppointmentBufferMinutes(),
                room.getBookingWindowDays(),
                room.getMinimumAdvanceMinutes(),
                room.getCancellationCutoffMinutes(),
                room.getLiveQueueResetPolicy(),
                room.getLiveQueueResetLocalTime(),
                room.getLiveQueueResetIntervalMinutes(),
                room.getLiveQueueMaxParticipants(),
                room.isLiveQueueAcceptingNewEntries(),
                room.getStatus(),
                room.getVisibility(),
                room.getPersonalPublicAddress(),
                room.getPersonalLatitude(),
                room.getPersonalLongitude(),
                room.getCreatedAt(),
                room.getArchivedAt()
        );
    }

    public RoomAssignmentDto toDto(RoomAssignmentEntity assignment) {
        UserEntity user = assignment.getUser();
        return new RoomAssignmentDto(
                assignment.getId(),
                assignment.getRoom().getId(),
                assignment.getRoom().getName(),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getNormalizedPhone(),
                assignment.getRole(),
                assignment.getStatus(),
                assignment.isShowPhonePublicly(),
                assignment.getInvitedByUser().getId(),
                assignment.getInvitedAt(),
                assignment.getRespondedAt()
        );
    }
}
