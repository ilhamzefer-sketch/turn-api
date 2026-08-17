package az.turn.api;

import jakarta.validation.constraints.Positive;

public record OwnershipTransferCreateRequestDto(@Positive long toAdminUserId) {
}
