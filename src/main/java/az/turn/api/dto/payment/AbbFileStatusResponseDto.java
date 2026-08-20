package az.turn.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbbFileStatusResponseDto(
        String externalReference,
        String batchNumber,
        AbbStatusDto status
) {
}
