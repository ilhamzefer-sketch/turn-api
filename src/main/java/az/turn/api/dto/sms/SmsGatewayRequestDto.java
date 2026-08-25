package az.turn.api;

public record SmsGatewayRequestDto(String to, String message, String sender) {
}
