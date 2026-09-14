package az.turn.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "app.wallet")
public record WalletProperties(
        @Min(10) @Max(10) int coinsPerAzn,
        @Min(1) long minimumTopUpCoins,
        @Min(1) @Max(999999999) long maximumTopUpCoins,
        @NotNull URI whatsappUrl,
        boolean manualTopUpEnabled
) {
    @AssertTrue(message = "Maksimum coin məbləği minimum məbləğdən az ola bilməz.")
    public boolean isTopUpRangeValid() {
        return maximumTopUpCoins >= minimumTopUpCoins;
    }
}
