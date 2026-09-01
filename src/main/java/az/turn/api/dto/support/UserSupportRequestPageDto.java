package az.turn.api;

import java.util.List;

public record UserSupportRequestPageDto<T>(List<T> items, int page, int size, boolean hasNext) {
}
