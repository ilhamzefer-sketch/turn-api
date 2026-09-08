package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    void completesExternalPaymentWithoutReceiptAndReleasesTheUser() {
        UserEntity user = user();
        WalletTopUpRequestEntity request = new WalletTopUpRequestEntity(user, topUpPackage(), CLICKED_AT);
        request.startExternalPayment("epoint", "42", "https://epoint.az/pay/42", CLICKED_AT.plusSeconds(1));
        WalletTransactionEntity transaction = new WalletTransactionEntity(
                new WalletAccountEntity(user, CLICKED_AT),
                WalletTransactionType.TOP_UP,
                30,
                0,
                30,
                WalletActorType.SYSTEM,
                null,
                "epoint",
                "top-up-request:42",
                "Epoint payment credited.",
                CLICKED_AT.plusMinutes(31)
        );

        request.completeExternalPayment("EPOINT-42", "success", transaction, CLICKED_AT.plusMinutes(31));

        assertThat(request.getStatus()).isEqualTo(WalletTopUpRequestStatus.PAID);
        assertThat(request.getActiveUserId()).isNull();
        assertThat(request.getReceiptAttachment()).isNull();
        assertThat(request.getWalletTransaction()).isEqualTo(transaction);
        assertThat(request.getExternalPaymentReference()).isEqualTo("EPOINT-42");
        assertThat(request.isReceiptWindowOpen(CLICKED_AT.plusMinutes(5))).isFalse();
    }

    @Test
    void epointOrderIdsAreUniqueButStillCarryTheTopUpRequestId() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-07T18:10:17Z"), ZoneOffset.UTC);

        String orderId = EpointWalletPaymentService.orderIdFor(42L, fixedClock);

        assertThat(orderId).isEqualTo("wallet-42-1788804617000");
        assertThat(EpointWalletPaymentService.requestIdFromOrderId(orderId)).isEqualTo(42L);
        assertThat(EpointWalletPaymentService.requestIdFromOrderId("42")).isEqualTo(42L);
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
