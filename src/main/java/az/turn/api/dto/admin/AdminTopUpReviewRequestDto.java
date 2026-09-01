package az.turn.api;

import jakarta.validation.constraints.Size;

public record AdminTopUpReviewRequestDto(
        @Size(max = 1000, message = "Qeyd 1000 simvoldan uzun ola bilməz.")
        String note
) {
}
