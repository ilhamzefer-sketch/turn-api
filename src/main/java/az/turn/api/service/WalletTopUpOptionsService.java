package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class WalletTopUpOptionsService {
    private final WalletProperties properties;

    public WalletTopUpOptionsService(WalletProperties properties) {
        this.properties = properties;
    }

    public WalletTopUpOptionsDto options() {
        return new WalletTopUpOptionsDto(
                properties.coinsPerAzn(),
                properties.minimumTopUpCoins(),
                properties.maximumTopUpCoins(),
                "AZN",
                properties.whatsappUrl().toString(),
                false
        );
    }
}
