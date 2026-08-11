package az.turn.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

public record PaymentSessionResponse(
        long id,
        @JsonIgnore
        String sessionToken,
        String provider,
        String paymentMode,
        PaymentStatus status,
        RegistrationType registrationType,
        long amount,
        String currency,
        String cardHolder,
        String cardLast4,
        String paymentReference,
        String externalOrderId,
        String checkoutUrl,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
    @Override
    public String toString() {
        return "PaymentSessionResponse[id=" + id + ", provider=" + provider + ", status=" + status + "]";
    }
}
