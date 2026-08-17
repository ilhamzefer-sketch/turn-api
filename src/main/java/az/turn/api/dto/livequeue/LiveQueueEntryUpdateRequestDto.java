package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LiveQueueEntryUpdateRequestDto(
        @NotBlank(message = "Ad mütləqdir.")
        @Size(max = 160, message = "Ad maksimum 160 simvol ola bilər.")
        String displayName,
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        String phone,
        @Size(max = 1000, message = "Daxili qeyd maksimum 1000 simvol ola bilər.")
        String internalNote
) {
}
