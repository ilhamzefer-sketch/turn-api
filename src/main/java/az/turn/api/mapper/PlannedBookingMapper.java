package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class PlannedBookingMapper {
    public PlannedBookingDto toDto(PlannedBookingEntity booking) {
        return toDto(booking, true);
    }

    public PlannedBookingDto toCustomerDto(PlannedBookingEntity booking) {
        return toDto(booking, false);
    }

    private PlannedBookingDto toDto(PlannedBookingEntity booking, boolean includeInternalNote) {
        UserEntity user = booking.getUser();
        GuestContactEntity guest = booking.getGuestContact();
        return new PlannedBookingDto(
                booking.getId(),
                booking.getBookingReference(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getStatus(),
                user == null ? guest.getDisplayName() : user.getFirstName() + " " + user.getLastName(),
                user == null ? guest.getNormalizedPhone() : user.getNormalizedPhone(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getRoom().getTimezone(),
                booking.getCustomerNote(),
                includeInternalNote ? booking.getInternalNote() : null,
                booking.getSource(),
                booking.getCancellationReason(),
                booking.getCancellationDetail(),
                booking.getCreatedByUser() == null ? null : booking.getCreatedByUser().getId(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getCompletedAt(),
                booking.getCancelledAt()
        );
    }
}
