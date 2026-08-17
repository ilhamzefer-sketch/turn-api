package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubscriptionCheckoutRequestDto(
        @NotNull ProviderScopeType scopeType,
        @Positive long scopeId,
        @NotBlank @Size(max = 60) String planCode,
        @Size(max = 160) String cardHolder,
        @Size(max = 30) String cardNumber
) {
}
