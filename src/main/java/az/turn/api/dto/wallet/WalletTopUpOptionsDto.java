package az.turn.api;

import java.math.BigDecimal;
import java.util.List;

public record WalletTopUpOptionsDto(
        int coinsPerAzn,
        long minimumCoins,
        long maximumCoins,
        String currency,
        String whatsappUrl,
        boolean bankCardEnabled,
        boolean manualTopUpEnabled,
        List<WalletTopUpPackageDto> packages,
        boolean customAmountEnabled,
        BigDecimal minimumAmountAzn,
        BigDecimal maximumAmountAzn,
        BigDecimal amountStepAzn
) {
}
