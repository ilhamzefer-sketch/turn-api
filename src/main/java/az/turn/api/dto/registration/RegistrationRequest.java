package az.turn.api;

public record RegistrationRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        String cardHolder,
        String cardNumber,
        String expireDate,
        String cvv,
        boolean paid,
        RegistrationType registrationType
) {
}
