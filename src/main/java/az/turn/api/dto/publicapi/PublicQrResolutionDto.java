package az.turn.api;

public record PublicQrResolutionDto(
        long roomId,
        ReservationMode reservationMode,
        String publicPath
) {
}
