package az.turn.api;

import java.util.List;

public record AdminTopUpRequestPageDto(
        List<AdminTopUpRequestDto> items,
        int page,
        int size,
        boolean hasNext
) {
}
