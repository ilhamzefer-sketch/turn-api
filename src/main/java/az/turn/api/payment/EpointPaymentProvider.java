package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EpointPaymentProvider implements PaymentProvider {
    @Override
    public String providerName() { return "epoint"; }

    @Override
    public void initialize(PaymentSessionEntity session) {
        throw new ResponseStatusException(HttpStatus.GONE, "Epoint yalnız balans artırmaq üçün istifadə olunur.");
    }

    @Override
    public PaymentStatus confirm(PaymentSessionEntity session) {
        throw new ResponseStatusException(HttpStatus.GONE, "Epoint qeydiyyat ödənişləri dəstəklənmir.");
    }
}
