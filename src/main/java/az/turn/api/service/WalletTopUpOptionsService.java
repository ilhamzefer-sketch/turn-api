package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class WalletTopUpOptionsService {
    private final WalletProperties properties;
    private final EpointWalletPaymentService epointPaymentService;

    public WalletTopUpOptionsService(WalletProperties properties, EpointWalletPaymentService epointPaymentService) {
        this.properties = properties;
        this.epointPaymentService = epointPaymentService;
    }

    public WalletTopUpOptionsDto options() {
        return new WalletTopUpOptionsDto(
                properties.coinsPerAzn(),
                properties.minimumTopUpCoins(),
                properties.maximumTopUpCoins(),
                "AZN",
                properties.whatsappUrl().toString(),
                epointPaymentService.isConfigured()
        );
    }
}
