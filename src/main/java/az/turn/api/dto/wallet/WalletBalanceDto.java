package az.turn.api;

import java.time.LocalDateTime;

public record WalletBalanceDto(
        long userId,
        long balance,
        LocalDateTime updatedAt
) {
}
