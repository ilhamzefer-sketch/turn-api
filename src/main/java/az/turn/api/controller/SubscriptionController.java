package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionPaymentService subscriptionPaymentService;
    private final SubscriptionCoinPaymentService coinPaymentService;
    private final RequestAuthenticationService authenticationService;

    public SubscriptionController(
            SubscriptionPaymentService subscriptionPaymentService,
            SubscriptionCoinPaymentService coinPaymentService,
            RequestAuthenticationService authenticationService
    ) {
        this.subscriptionPaymentService = subscriptionPaymentService;
        this.coinPaymentService = coinPaymentService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/plans")
    public List<SubscriptionPlanDto> plans(
            @RequestParam(required = false) ProviderScopeType scopeType
    ) {
        return subscriptionPaymentService.plans(scopeType);
    }

    @GetMapping("/current")
    public ProviderSubscriptionDto current(
            @RequestParam ProviderScopeType scopeType,
            @RequestParam @Positive long scopeId,
            Authentication authentication
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return subscriptionPaymentService.get(scopeType, scopeId, userId);
    }

    @GetMapping("/receipts")
    public List<SubscriptionReceiptDto> receipts(
            @RequestParam ProviderScopeType scopeType,
            @RequestParam @Positive long scopeId,
            Authentication authentication
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return subscriptionPaymentService.receipts(scopeType, scopeId, userId);
    }

    @PostMapping("/checkout")
    public void checkout(Authentication authentication) {
        authenticationService.requireUser(authentication, AuthUserType.USER);
        throw retiredBankPayment();
    }

    @PostMapping("/purchase")
    public SubscriptionCoinPurchaseDto purchase(
            @Valid @RequestBody SubscriptionCoinPurchaseRequestDto request,
            Authentication authentication
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return coinPaymentService.purchase(userId, request);
    }

    @GetMapping("/payments/{paymentSessionId}")
    public void payment(
            @PathVariable @Positive long paymentSessionId,
            Authentication authentication
    ) {
        authenticationService.requireUser(authentication, AuthUserType.USER);
        throw retiredBankPayment();
    }

    @PostMapping("/payments/{paymentSessionId}/confirm")
    public void confirm(
            @PathVariable @Positive long paymentSessionId,
            Authentication authentication
    ) {
        authenticationService.requireUser(authentication, AuthUserType.USER);
        throw retiredBankPayment();
    }

    @PostMapping("/payments/{paymentSessionId}/cancel")
    public void cancel(
            @PathVariable @Positive long paymentSessionId,
            Authentication authentication
    ) {
        authenticationService.requireUser(authentication, AuthUserType.USER);
        throw retiredBankPayment();
    }

    private ResponseStatusException retiredBankPayment() {
        return new ResponseStatusException(
                HttpStatus.GONE,
                "Abunəlik üçün bank ödənişi dayandırılıb. Ödənişi coin balansı ilə edin."
        );
    }
}
