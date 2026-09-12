package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminTopUpSummaryDto(
        long total,
        long paid,
        long failed,
        long waiting,
        BigDecimal paidTodayAmount,
        LocalDate businessDate,
        String timezone
) {
}
