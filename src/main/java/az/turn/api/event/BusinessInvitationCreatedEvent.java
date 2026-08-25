package az.turn.api;

public record BusinessInvitationCreatedEvent(long membershipId, String phone, String businessName) {
}
