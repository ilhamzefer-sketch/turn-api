package az.turn.api;

import java.util.List;

public record AdminUserPageDto(
        List<AdminUserDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
