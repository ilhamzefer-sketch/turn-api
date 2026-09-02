package az.turn.api;

import jakarta.validation.constraints.Size;

public record QrPosterTitleUpdateDto(
        @Size(max = 80, message = "QR afişasının başlığı 80 simvoldan uzun ola bilməz.") String posterTitle
) {
}
