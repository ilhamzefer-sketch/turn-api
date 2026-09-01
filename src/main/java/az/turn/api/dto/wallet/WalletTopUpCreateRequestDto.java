package az.turn.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletTopUpCreateRequestDto(
        @NotBlank(message = "Ödəniş paketi seçilməlidir.")
        @Pattern(regexp = "AZN_(3|5|10|15|20)", message = "Ödəniş paketi düzgün deyil.")
        String packageCode
) {
}
