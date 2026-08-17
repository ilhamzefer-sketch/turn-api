package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessMemberInviteRequestDto(
        @NotBlank(message = "Telefon nömrəsi mütləqdir.")
        String phone,
        @Size(max = 80, message = "Ad maksimum 80 simvol ola bilər.")
        String firstName,
        @Size(max = 80, message = "Soyad maksimum 80 simvol ola bilər.")
        String lastName,
        @NotNull(message = "Biznes rolu mütləqdir.")
        BusinessRole role
) {
}
