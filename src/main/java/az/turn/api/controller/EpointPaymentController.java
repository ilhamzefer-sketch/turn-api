package az.turn.api;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EpointPaymentController {
    private final EpointWalletPaymentService paymentService;

    public EpointPaymentController(EpointWalletPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/api/payments/epoint/callback", consumes = "application/x-www-form-urlencoded")
    public String callback(@RequestParam MultiValueMap<String, String> form) {
        paymentService.processCallback(form.getFirst("data"), form.getFirst("signature"));
        return "OK";
    }
}
