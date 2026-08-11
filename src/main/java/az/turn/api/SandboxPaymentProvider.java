package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class SandboxPaymentProvider implements PaymentProvider {

    @Override
    public void initialize(PaymentSessionEntity session) {
        session.setExternalOrderId("sandbox-" + session.getId());
    }

    @Override
    public String providerName() {
        return "sandbox";
    }

    @Override
    public PaymentStatus confirm(PaymentSessionEntity session) {
        return "FAIL".equalsIgnoreCase(session.getSandboxOutcome())
                ? PaymentStatus.FAILED
                : PaymentStatus.COMPLETED;
    }
}
