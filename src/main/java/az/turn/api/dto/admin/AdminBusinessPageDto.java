package az.turn.api;

import java.util.List;

public record AdminBusinessPageDto(
        List<AdminBusinessDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
