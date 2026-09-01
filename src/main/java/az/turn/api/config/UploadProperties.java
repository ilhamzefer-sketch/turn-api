package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(
        @NotNull Path storageRoot,
        @Min(1) @Max(5242880) int maxFileBytes,
        @Min(1) @Max(10000) int maxDimensionPixels,
        @Min(1) @Max(25000000) long maxImagePixels,
        @Valid @NotNull AntivirusProperties antivirus
) {
    @AssertTrue(message = "Şəkil piksel limiti ölçü limitinə uyğun olmalıdır.")
    public boolean isImageLimitValid() {
        return maxImagePixels <= Math.multiplyExact(
                (long) maxDimensionPixels,
                maxDimensionPixels
        );
    }
}
