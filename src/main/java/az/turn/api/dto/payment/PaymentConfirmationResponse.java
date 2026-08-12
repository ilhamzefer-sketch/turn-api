package az.turn.api;

public record PaymentConfirmationResponse(
        PaymentSessionResponse payment,
        RegistrationResponse registration
) {
}
