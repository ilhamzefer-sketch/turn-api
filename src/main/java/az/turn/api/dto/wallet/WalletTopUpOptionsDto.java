package az.turn.api;

import java.util.List;

public record WalletTopUpOptionsDto(
        int coinsPerAzn,
        long minimumCoins,
        long maximumCoins,
        String currency,
        String whatsappUrl,
        boolean bankCardEnabled,
        boolean manualTopUpEnabled,
        List<WalletTopUpPackageDto> packages
) {
}
