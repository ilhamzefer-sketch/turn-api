package az.turn.api;

import java.time.LocalDateTime;

public record AdminUserDto(
        long id,
        String firstName,
        String lastName,
        String phone,
        UserStatus status,
        long coinBalance,
        int confirmedWalletFraudCount,
        LocalDateTime createdAt
) {
}
