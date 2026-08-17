package az.turn.api;

public record WorkspaceContextDto(
        WorkspaceContextType type,
        Long contextId,
        String name,
        String role
) {
}
