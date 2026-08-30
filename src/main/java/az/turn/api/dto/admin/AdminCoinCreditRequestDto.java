package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCoinCreditRequestDto(
        @Min(value = 1, message = "Əlavə edilən coin ən azı 1 olmalıdır.")
        @Max(value = 1000000, message = "Bir əməliyyatda maksimum 1 000 000 coin əlavə edilə bilər.")
        long amount,
        @NotBlank(message = "Coin əlavəsinin səbəbi mütləqdir.")
        @Size(min = 3, max = 500, message = "Səbəb 3-500 simvol olmalıdır.")
        String reason,
        @NotBlank(message = "Əməliyyat istinadı mütləqdir.")
        @Size(min = 8, max = 80, message = "Əməliyyat istinadı 8-80 simvol olmalıdır.")
        String idempotencyKey
) {
}
