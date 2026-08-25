package az.turn.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledSmsSender implements SmsSender {
    @Override
    public void send(String phone, String message) {
    }
}
