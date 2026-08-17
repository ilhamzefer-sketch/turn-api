package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OwnershipDisputeResolveRequestDto(
        @NotNull DisputeResolutionAction action,
        @NotBlank @Size(max = 2000) String resolutionNote,
        boolean reject
) {
}
