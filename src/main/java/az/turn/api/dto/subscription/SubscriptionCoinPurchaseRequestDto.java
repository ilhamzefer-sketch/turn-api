package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SubscriptionCoinPurchaseRequestDto(
        @NotNull ProviderScopeType scopeType,
        @Positive long scopeId,
        @NotBlank @Size(max = 60) String planCode,
        @NotBlank @Size(max = 80) String idempotencyKey
) {
}
