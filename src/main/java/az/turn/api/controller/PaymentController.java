package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentSessionService paymentSessionService;
    private final ApiSessionService apiSessionService;

    public PaymentController(PaymentSessionService paymentSessionService, ApiSessionService apiSessionService) {
        this.paymentSessionService = paymentSessionService;
        this.apiSessionService = apiSessionService;
    }

    @PostMapping("/api/payments/registration-sessions")
    public PaymentSessionResponse createRegistrationPaymentSession(
            @Valid @RequestBody RegistrationPaymentSessionRequest request,
            HttpServletResponse response
    ) {
        PaymentSessionResponse paymentSession = paymentSessionService.createRegistrationPaymentSession(request);
        apiSessionService.writePaymentSessionCookie(response, paymentSession.sessionToken());
        return paymentSession;
    }

    @GetMapping("/api/payments/registration-sessions/{paymentSessionId}")
    public PaymentSessionResponse getRegistrationPaymentSession(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request
    ) {
        return paymentSessionService.getPaymentSession(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, sessionToken)
        );
    }

    @PostMapping("/api/payments/registration-sessions/{paymentSessionId}/confirm")
    public PaymentConfirmationResponse confirmRegistrationPayment(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PaymentConfirmationResponse paymentConfirmation = paymentSessionService.confirmRegistrationPayment(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, sessionToken)
        );
        RegistrationResponse registration = paymentConfirmation.registration();
        if (registration == null) {
            if (paymentConfirmation.payment().status() != PaymentStatus.PENDING) {
                apiSessionService.clearPaymentSessionCookie(response);
            }
            return paymentConfirmation;
        }

        apiSessionService.clearPaymentSessionCookie(response);
        RegistrationResponse authenticatedRegistration = apiSessionService.authenticateRegistration(registration, response);
        return new PaymentConfirmationResponse(paymentConfirmation.payment(), authenticatedRegistration);
    }

    @PostMapping("/api/payments/registration-sessions/{paymentSessionId}/cancel")
    public PaymentSessionResponse cancelRegistrationPayment(
            @PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        PaymentSessionResponse paymentSession = paymentSessionService.cancelRegistrationPayment(
                paymentSessionId,
                apiSessionService.resolvePaymentSessionToken(request, sessionToken)
        );
        apiSessionService.clearPaymentSessionCookie(response);
        return paymentSession;
    }
}
