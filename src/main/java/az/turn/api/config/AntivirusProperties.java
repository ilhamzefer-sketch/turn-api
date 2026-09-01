package az.turn.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

public record AntivirusProperties(
        boolean enabled,
        @NotBlank String host,
        @Min(1) @Max(65535) int port,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1024) @Max(1048576) int chunkBytes
) {
    @AssertTrue(message = "Antivirus timeout dəyərləri sıfırdan böyük olmalıdır.")
    public boolean isTimeoutConfigurationValid() {
        return !connectTimeout.isNegative()
                && !connectTimeout.isZero()
                && !readTimeout.isNegative()
                && !readTimeout.isZero();
    }
}
