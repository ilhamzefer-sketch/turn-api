package az.turn.api;

public record QrPosterSpecification(
        long credentialId,
        String posterTitle,
        String publicUrl,
        ReservationMode reservationMode,
        String roomCode,
        int durationMinutes,
        String description
) {
}
