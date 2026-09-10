package az.turn.api;

import java.math.BigDecimal;

public record WalletTopUpPackageDto(
        String code,
        BigDecimal amountAzn,
        long coinAmount
) {
}
