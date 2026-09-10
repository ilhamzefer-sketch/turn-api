package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class WalletTopUpOptionsService {
    private final WalletProperties properties;
    private final EpointWalletPaymentService epointPaymentService;
    private final WalletTopUpPackageRepository packageRepository;

    public WalletTopUpOptionsService(
            WalletProperties properties,
            EpointWalletPaymentService epointPaymentService,
            WalletTopUpPackageRepository packageRepository
    ) {
        this.properties = properties;
        this.epointPaymentService = epointPaymentService;
        this.packageRepository = packageRepository;
    }

    public WalletTopUpOptionsDto options() {
        return new WalletTopUpOptionsDto(
                properties.coinsPerAzn(),
                properties.minimumTopUpCoins(),
                properties.maximumTopUpCoins(),
                "AZN",
                properties.whatsappUrl().toString(),
                epointPaymentService.isConfigured(),
                packageRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(item -> new WalletTopUpPackageDto(
                                item.getCode(),
                                item.getAmountAzn(),
                                item.getCoinAmount()
                        ))
                        .toList()
        );
    }
}
