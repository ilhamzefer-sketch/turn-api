package az.turn.api;

public record WalletTopUpOptionsDto(
        int coinsPerAzn,
        long minimumCoins,
        long maximumCoins,
        String currency,
        String whatsappUrl,
        boolean bankCardEnabled
) {
}
