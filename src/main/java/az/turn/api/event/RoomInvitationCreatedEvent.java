package az.turn.api;

public record RoomInvitationCreatedEvent(long assignmentId, String phone, String roomName) {
}
