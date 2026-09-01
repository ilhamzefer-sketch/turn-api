package az.turn.api;

import java.util.List;

public record AdminSupportRequestPageDto(
        List<AdminSupportRequestDto> items,
        int page,
        int size,
        boolean hasNext
) {
}
