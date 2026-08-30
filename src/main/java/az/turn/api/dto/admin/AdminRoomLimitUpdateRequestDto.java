package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRoomLimitUpdateRequestDto(
        @Min(value = 5, message = "Biznes otaq limiti ən azı 5 olmalıdır.")
        @Max(value = 1000, message = "Biznes otaq limiti maksimum 1000 ola bilər.")
        int roomLimit,
        @NotBlank(message = "Limit dəyişikliyinin səbəbi mütləqdir.")
        @Size(min = 3, max = 500, message = "Səbəb 3-500 simvol olmalıdır.")
        String reason
) {
}
