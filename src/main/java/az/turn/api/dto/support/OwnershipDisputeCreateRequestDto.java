package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnershipDisputeCreateRequestDto(
        @NotBlank @Size(max = 30) String disputedPhone,
        @NotBlank @Size(max = 160) String claimantName,
        @NotBlank @Size(max = 30) String claimantContactPhone,
        @NotBlank @Size(max = 2000) String description
) {
}
