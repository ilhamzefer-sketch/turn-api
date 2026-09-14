package az.turn.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTopUpAmountServiceTests {
    private final WalletTopUpAmountService amounts = new WalletTopUpAmountService(
            new WalletProperties(10, 1, 500, URI.create("https://example.com"), false));

    @ParameterizedTest
    @CsvSource({"0.10,1", "0.20,2", "1,10", "7.3,73", "49.90,499", "50,500"})
    void convertsAllowedAmountsExactly(String amount, long coins) {
        assertThat(amounts.coins(new BigDecimal(amount))).isEqualTo(coins);
        assertThat(amounts.normalize(new BigDecimal(amount)).scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.10", "0.09", "0.11", "0.101", "0.100", "50.10", "1E+30"})
    void rejectsAmountsOutsideTheContract(String amount) {
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        assertThatThrownBy(() -> amounts.coins(value)).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void customAmountsCannotFallBackToStaticManualLinks() {
        WalletProperties manual = new WalletProperties(10, 1, 500, URI.create("https://example.com"), true);
        WalletTopUpRequestService requests = new WalletTopUpRequestService(null, manual, null,
                mock(EpointWalletPaymentService.class), null, null, null, null);
        assertThatThrownBy(() -> requests.create(1L, new WalletTopUpCreateRequestDto(null, new BigDecimal("0.10"))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(503));
    }
}
