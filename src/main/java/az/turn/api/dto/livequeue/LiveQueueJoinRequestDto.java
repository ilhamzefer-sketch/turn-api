package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveQueueJoinRequestDto(
        @NotBlank(message = "Ad mütləqdir.")
        @Size(max = 160, message = "Ad maksimum 160 simvol ola bilər.")
        String displayName,
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        String phone
) {
}
