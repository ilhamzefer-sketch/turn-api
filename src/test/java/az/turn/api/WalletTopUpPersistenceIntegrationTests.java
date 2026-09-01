package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WalletTopUpPersistenceIntegrationTests {
    @Autowired
    private WalletTopUpPackageRepository packageRepository;

    @Autowired
    private WalletTopUpRequestRepository requestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTopUpRequestStateService stateService;

    @Test
    void loadsOnlyTheFiveFixedPackagesInDisplayOrder() {
        List<WalletTopUpPackageEntity> packages = packageRepository.findByActiveTrueOrderByDisplayOrderAsc();

        assertThat(packages).extracting(WalletTopUpPackageEntity::getAmountAzn)
                .containsExactly(3, 5, 10, 15, 20);
        assertThat(packages).extracting(WalletTopUpPackageEntity::getCoinAmount)
                .containsExactly(30L, 50L, 100L, 150L, 200L);
    }

    @Test
    void persistsAnImmutablePackageSnapshotOnTheRequest() {
        UserEntity user = createUser("+994501293401");
        WalletTopUpPackageEntity topUpPackage = packageRepository.findById("AZN_10").orElseThrow();
        LocalDateTime clickedAt = LocalDateTime.of(2026, 8, 31, 12, 0);

        WalletTopUpRequestEntity request = requestRepository.saveAndFlush(
                new WalletTopUpRequestEntity(user, topUpPackage, clickedAt)
        );

        assertThat(request.getAmountAzn()).isEqualTo(10);
        assertThat(request.getCoinAmount()).isEqualTo(100);
        assertThat(request.getPaymentUrl())
                .isEqualTo("https://cb.birbank.business/pay/75c998cbda8e4674bb11cbf961d91c27");
        assertThat(requestRepository.findByActiveUserId(user.getId()))
                .contains(request);
    }

    @Test
    void databaseRejectsTwoActiveRequestsForTheSameUser() {
        UserEntity user = createUser("+994501293402");
        WalletTopUpPackageEntity firstPackage = packageRepository.findById("AZN_3").orElseThrow();
        WalletTopUpPackageEntity secondPackage = packageRepository.findById("AZN_5").orElseThrow();
        LocalDateTime clickedAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        requestRepository.saveAndFlush(new WalletTopUpRequestEntity(user, firstPackage, clickedAt));

        assertThatThrownBy(() -> requestRepository.saveAndFlush(
                new WalletTopUpRequestEntity(user, secondPackage, clickedAt.plusMinutes(1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiredRequestReleasesTheUserForANewRequest() {
        UserEntity user = createUser("+994501293403");
        WalletTopUpPackageEntity firstPackage = packageRepository.findById("AZN_3").orElseThrow();
        WalletTopUpPackageEntity secondPackage = packageRepository.findById("AZN_20").orElseThrow();
        LocalDateTime clickedAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        WalletTopUpRequestEntity first = requestRepository.saveAndFlush(
                new WalletTopUpRequestEntity(user, firstPackage, clickedAt)
        );
        first.expire(clickedAt.plusMinutes(30));
        requestRepository.saveAndFlush(first);

        WalletTopUpRequestEntity second = requestRepository.saveAndFlush(
                new WalletTopUpRequestEntity(user, secondPackage, clickedAt.plusMinutes(31))
        );

        assertThat(second.getStatus()).isEqualTo(WalletTopUpRequestStatus.AWAITING_RECEIPT);
        assertThat(requestRepository.findByActiveUserId(user.getId())).contains(second);
    }

    @Test
    void readingAnExpiredActiveRequestPersistsItsExpiration() {
        UserEntity user = createUser("+994501293404");
        WalletTopUpPackageEntity topUpPackage = packageRepository.findById("AZN_3").orElseThrow();
        requestRepository.saveAndFlush(new WalletTopUpRequestEntity(
                user, topUpPackage, LocalDateTime.of(2020, 1, 1, 12, 0)
        ));

        assertThatThrownBy(() -> stateService.active(user.getId()))
                .isInstanceOf(WalletTopUpException.class);
        assertThat(requestRepository.findByActiveUserId(user.getId())).isEmpty();
        assertThat(requestRepository.findAll().stream().filter(request -> request.getUser().getId().equals(user.getId())))
                .singleElement()
                .extracting(WalletTopUpRequestEntity::getStatus)
                .isEqualTo(WalletTopUpRequestStatus.EXPIRED);
    }

    private UserEntity createUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Top up");
        user.setLastName("Test");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }
}
