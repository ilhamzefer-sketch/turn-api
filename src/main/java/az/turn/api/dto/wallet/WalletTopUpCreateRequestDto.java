package az.turn.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record WalletTopUpCreateRequestDto(
        @Pattern(regexp = "AZN_(3|5|10|15|20)", message = "Ödəniş paketi düzgün deyil.")
        String packageCode,
        @DecimalMin(value = "0.10", message = "Minimum məbləğ 0.10 ₼-dir.")
        @DecimalMax(value = "50.00", message = "Maksimum məbləğ 50.00 ₼-dir.")
        @Digits(integer = 8, fraction = 2, message = "Məbləğ ən çox iki onluq rəqəmlə yazılmalıdır.")
        BigDecimal amountAzn
) {
    @AssertTrue(message = "Yalnız məbləğ və ya ödəniş paketi göndərilməlidir.")
    public boolean isSelectionValid() {
        return (packageCode != null) != (amountAzn != null);
    }
}
