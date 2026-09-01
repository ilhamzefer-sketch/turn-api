package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTopUpRequestEntityTests {
    private static final LocalDateTime CLICKED_AT = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Test
    void createsAnAwaitingReceiptRequestWithAThirtyMinuteWindow() {
        WalletTopUpRequestEntity request = new WalletTopUpRequestEntity(user(), topUpPackage(), CLICKED_AT);

        assertThat(request.getStatus()).isEqualTo(WalletTopUpRequestStatus.AWAITING_RECEIPT);
        assertThat(request.getActiveUserId()).isEqualTo(91L);
        assertThat(request.getAmountAzn()).isEqualTo(3);
        assertThat(request.getCoinAmount()).isEqualTo(30);
        assertThat(request.getCurrency()).isEqualTo("AZN");
        assertThat(request.getReceiptDeadlineAt()).isEqualTo(CLICKED_AT.plusMinutes(30));
        assertThat(request.isReceiptWindowOpen(CLICKED_AT.plusMinutes(29))).isTrue();
        assertThat(request.isReceiptWindowOpen(CLICKED_AT.plusMinutes(30))).isFalse();
    }

    @Test
    void submitsReceiptOnlyBeforeTheDeadline() {
        WalletTopUpRequestEntity request = new WalletTopUpRequestEntity(user(), topUpPackage(), CLICKED_AT);
        LocalDateTime submittedAt = CLICKED_AT.plusMinutes(29);

        request.submitReceipt(submittedAt);

        assertThat(request.getStatus()).isEqualTo(WalletTopUpRequestStatus.PENDING_REVIEW);
        assertThat(request.getReceiptUploadedAt()).isEqualTo(submittedAt);
        assertThat(request.getActiveUserId()).isEqualTo(91L);
        assertThatThrownBy(() -> request.submitReceipt(submittedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresAtTheDeadlineAndReleasesTheUser() {
        WalletTopUpRequestEntity request = new WalletTopUpRequestEntity(user(), topUpPackage(), CLICKED_AT);

        assertThat(request.expire(CLICKED_AT.plusMinutes(29))).isFalse();
        assertThat(request.expire(CLICKED_AT.plusMinutes(30))).isTrue();
        assertThat(request.getStatus()).isEqualTo(WalletTopUpRequestStatus.EXPIRED);
        assertThat(request.getActiveUserId()).isNull();
        assertThatThrownBy(() -> request.submitReceipt(CLICKED_AT.plusMinutes(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInactivePackagesAndBlankRejectionReasons() {
        WalletTopUpPackageEntity inactivePackage = new WalletTopUpPackageEntity(
                "AZN_3",
                3,
                30,
                "https://cb.birbank.business/pay/7847238243e34c9c9dd4666f749d5879",
                1,
                false,
                CLICKED_AT
        );

        assertThatThrownBy(() -> new WalletTopUpRequestEntity(user(), inactivePackage, CLICKED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        WalletTopUpRequestEntity request = new WalletTopUpRequestEntity(user(), topUpPackage(), CLICKED_AT);
        request.submitReceipt(CLICKED_AT.plusMinutes(5));
        assertThatThrownBy(() -> request.reject(new AdminAccountEntity(), "  ", CLICKED_AT.plusMinutes(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(91L);
        return user;
    }

    private WalletTopUpPackageEntity topUpPackage() {
        return new WalletTopUpPackageEntity(
                "AZN_3",
                3,
                30,
                "https://cb.birbank.business/pay/7847238243e34c9c9dd4666f749d5879",
                1,
                true,
                CLICKED_AT
        );
    }
}
