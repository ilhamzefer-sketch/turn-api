package az.turn.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.wallet.manual-top-up-enabled=false", "app.payment.epoint.public-key=incomplete",
        "app.payment.epoint.private-key=", "app.security.rate-limit.auth-per-minute=1000"})
@AutoConfigureMockMvc
class WalletPaymentAvailabilityTests extends WalletPaymentTestSupport {
    @Autowired WalletTopUpCheckoutService checkout;
    @Autowired WalletTopUpRequestStateService state;
    @Autowired EpointPaymentProvider legacyProvider;

    @BeforeEach
    void setup() throws Exception { register(); }

    @Test
    void missingConfigurationCannotSilentlyCreateAManualPayment() throws Exception {
        long before = repository.count();
        mvc.perform(get("/api/users/me/wallet/top-up-options").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bankCardEnabled").value(false))
                .andExpect(jsonPath("$.manualTopUpEnabled").value(false));
        mvc.perform(post("/api/users/me/wallet/top-up-requests").cookie(csrf.cookie())
                .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"packageCode\":\"AZN_3\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void existingManualRequestsRemainReadableAndReceiptEligible() throws Exception {
        WalletTopUpRequestDto existing = checkout.prepare(userId, "AZN_3", false).request();
        assertThat(existing.paymentProvider()).isEqualTo("manual");
        assertThat(existing.checkoutState()).isEqualTo(WalletCheckoutState.NOT_REQUIRED);
        assertThat(existing.receiptUploadOpen()).isTrue();
        assertThat(existing.amountAzn()).isEqualByComparingTo("3.00");
        assertThat(existing.coinAmount()).isEqualTo(30);
        assertThat(state.beginReceiptUpload(userId, existing.id()).getId()).isEqualTo(existing.id());
        expectLookup(existing.id(), 200);
        assertThat(requests.active(userId).id()).isEqualTo(existing.id());
    }

    @Test
    void retiredRegistrationProviderCannotCreateOrPretendToConfirmPayment() {
        assertThatThrownBy(() -> legacyProvider.initialize(new PaymentSessionEntity())).hasMessageContaining("410");
        assertThatThrownBy(() -> legacyProvider.confirm(new PaymentSessionEntity())).hasMessageContaining("410");
    }
}
