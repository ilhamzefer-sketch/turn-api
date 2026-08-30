package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class WalletMapper {
    public WalletBalanceDto toBalanceDto(WalletAccountEntity wallet) {
        return new WalletBalanceDto(
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getUpdatedAt()
        );
    }

    public WalletTransactionDto toTransactionDto(WalletTransactionEntity transaction) {
        return new WalletTransactionDto(
                transaction.getId(),
                transaction.getType(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getBalanceBefore(),
                transaction.getBalanceAfter(),
                transaction.getActorType(),
                transaction.getReferenceKey(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
