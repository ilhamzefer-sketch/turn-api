package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CustomerQueueRatingRequest(
        @Positive(message = "Müştəri ID müsbət olmalıdır.") Long customerId,
        @NotNull(message = "Qiymət mütləqdir.") @Min(value = 1, message = "Qiymət 1-5 aralığında olmalıdır.") @Max(value = 5, message = "Qiymət 1-5 aralığında olmalıdır.") Integer rating,
        @Size(max = 1000, message = "Qeyd maksimum 1000 simvol ola bilər.") String note
) {
}
