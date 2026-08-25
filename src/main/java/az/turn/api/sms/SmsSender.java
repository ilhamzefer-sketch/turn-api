package az.turn.api;

public interface SmsSender {
    void send(String phone, String message);
}
