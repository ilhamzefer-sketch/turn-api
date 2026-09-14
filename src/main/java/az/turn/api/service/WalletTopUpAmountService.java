package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class WalletTopUpAmountService {
    private final WalletProperties properties;

    public WalletTopUpAmountService(WalletProperties properties) {
        this.properties = properties;
    }

    public BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.scale() > 2 || amount.compareTo(minimumAmountAzn()) < 0
                || amount.compareTo(maximumAmountAzn()) > 0 || amount.remainder(amountStepAzn()).signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Məbləğ " + minimumAmountAzn().toPlainString() + "–" + maximumAmountAzn().toPlainString()
                            + " ₼ aralığında, 0.10 ₼ addımlarla daxil edilməlidir.");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public long coins(BigDecimal amount) {
        return normalize(amount).multiply(BigDecimal.valueOf(properties.coinsPerAzn())).longValueExact();
    }

    public BigDecimal minimumAmountAzn() {
        return BigDecimal.valueOf(properties.minimumTopUpCoins(), 1).setScale(2);
    }

    public BigDecimal maximumAmountAzn() {
        return BigDecimal.valueOf(properties.maximumTopUpCoins(), 1).setScale(2);
    }

    public BigDecimal amountStepAzn() {
        return new BigDecimal("0.10");
    }
}
