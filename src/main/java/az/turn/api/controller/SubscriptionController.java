package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/subscriptions")
public class SubscriptionController {
    private final SubscriptionPaymentService subscriptionPaymentService;
    private final RequestAuthenticationService authenticationService;
    private final ApiSessionService apiSessionService;

    public SubscriptionController(
            SubscriptionPaymentService subscriptionPaymentService,
            RequestAuthenticationService authenticationService,
            ApiSessionService apiSessionService
    ) {
        this.subscriptionPaymentService = subscriptionPaymentService;
        this.authenticationService = authenticationService;
        this.apiSessionService = apiSessionService;
    }

    @GetMapping("/plans")
    public List<SubscriptionPlanDto> plans() {
        return subscriptionPaymentService.plans();
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
    public SubscriptionPaymentSessionDto checkout(
            @Valid @RequestBody SubscriptionCheckoutRequestDto request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        SubscriptionPaymentSessionDto session = subscriptionPaymentService.checkout(userId, request);
        apiSessionService.writePaymentSessionCookie(response, session.sessionToken());
        return session;
    }

    @GetMapping("/payments/{paymentSessionId}")
    public SubscriptionPaymentSessionDto payment(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String headerToken,
            Authentication authentication,
            HttpServletRequest request
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return subscriptionPaymentService.getSession(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, headerToken),
                userId
        );
    }

    @PostMapping("/payments/{paymentSessionId}/confirm")
    public SubscriptionPaymentSessionDto confirm(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String headerToken,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        SubscriptionPaymentSessionDto result = subscriptionPaymentService.confirm(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, headerToken),
                userId
        );
        if (result.status() != PaymentStatus.PENDING) apiSessionService.clearPaymentSessionCookie(response);
        return result;
    }

    @PostMapping("/payments/{paymentSessionId}/cancel")
    public SubscriptionPaymentSessionDto cancel(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String headerToken,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        SubscriptionPaymentSessionDto result = subscriptionPaymentService.cancel(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, headerToken),
                userId
        );
        apiSessionService.clearPaymentSessionCookie(response);
        return result;
    }
}
