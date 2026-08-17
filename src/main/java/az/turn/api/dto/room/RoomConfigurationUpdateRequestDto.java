package az.turn.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;

public record RoomConfigurationUpdateRequestDto(
        @NotNull(message = "Standart müddət mütləqdir.")
        @Min(value = 1, message = "Standart müddət ən azı 1 dəqiqə olmalıdır.")
        @Max(value = 1440, message = "Standart müddət maksimum 1440 dəqiqə ola bilər.")
        Integer defaultSlotDurationMinutes,
        @NotNull(message = "Görüşdən sonrakı fasilə mütləqdir.")
        @PositiveOrZero(message = "Görüşdən sonrakı fasilə mənfi ola bilməz.")
        @Max(value = 1440, message = "Görüşdən sonrakı fasilə maksimum 1440 dəqiqə ola bilər.")
        Integer appointmentBufferMinutes,
        @NotNull(message = "Rezervasiya pəncərəsi mütləqdir.")
        @Min(value = 1, message = "Rezervasiya pəncərəsi ən azı 1 gün olmalıdır.")
        @Max(value = 90, message = "Rezervasiya pəncərəsi maksimum 90 gün ola bilər.")
        Integer bookingWindowDays,
        @NotNull(message = "Minimum əvvəlcədən bildiriş mütləqdir.")
        @PositiveOrZero(message = "Minimum əvvəlcədən bildiriş mənfi ola bilməz.")
        @Max(value = 10080, message = "Minimum əvvəlcədən bildiriş maksimum 7 gün ola bilər.")
        Integer minimumAdvanceMinutes,
        @NotNull(message = "Ləğv müddəti mütləqdir.")
        @PositiveOrZero(message = "Ləğv müddəti mənfi ola bilməz.")
        Integer cancellationCutoffMinutes,
        LiveQueueResetPolicy liveQueueResetPolicy,
        LocalTime liveQueueResetLocalTime,
        @Positive(message = "Canlı növbə reset intervalı müsbət olmalıdır.")
        Integer liveQueueResetIntervalMinutes,
        @Positive(message = "Canlı növbə iştirakçı limiti müsbət olmalıdır.")
        Integer liveQueueMaxParticipants,
        @NotNull(message = "Canlı növbənin qəbul vəziyyəti mütləqdir.")
        Boolean liveQueueAcceptingNewEntries
) {
}
