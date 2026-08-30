package az.turn.api;

import java.util.List;

public record WalletTransactionPageDto(
        List<WalletTransactionDto> items,
        int page,
        int size,
        boolean hasNext
) {
}
