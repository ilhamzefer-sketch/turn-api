package az.turn.api;

public interface PaymentProvider {
    String providerName();
    void initialize(PaymentSessionEntity session);
    PaymentStatus confirm(PaymentSessionEntity session);
}
