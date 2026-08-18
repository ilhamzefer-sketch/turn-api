package az.turn.api;

import java.util.List;

public record PublicRoomSearchPageDto(
        List<PublicRoomSummaryDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
