package az.turn.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserWalletFraudRiskTests {
    @Test
    void requiresManualReviewFromTheThirdConfirmedFraud() {
        UserEntity user = new UserEntity();

        assertThat(user.getConfirmedWalletFraudCount()).isZero();
        assertThat(user.requiresManualWalletTopUpReview()).isFalse();
        assertThat(user.registerConfirmedWalletFraud()).isEqualTo(1);
        assertThat(user.registerConfirmedWalletFraud()).isEqualTo(2);
        assertThat(user.requiresManualWalletTopUpReview()).isFalse();
        assertThat(user.registerConfirmedWalletFraud()).isEqualTo(3);
        assertThat(user.requiresManualWalletTopUpReview()).isTrue();
    }

    @Test
    void rejectsANegativeConfirmedFraudCount() {
        UserEntity user = new UserEntity();

        assertThatThrownBy(() -> user.setConfirmedWalletFraudCount(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
