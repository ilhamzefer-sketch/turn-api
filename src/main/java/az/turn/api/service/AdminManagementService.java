package az.turn.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminManagementService {
    private final UserRepository userRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionService walletTransactionService;
    private final BusinessRepository businessRepository;
    private final RoomRepository roomRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final AdminManagementMapper mapper;
    private final PlatformAuditService auditService;
    private final UserPasswordService userPasswordService;
    private final UserSessionService userSessionService;
    private final Clock clock;

    public AdminManagementService(
            UserRepository userRepository,
            WalletAccountRepository walletAccountRepository,
            WalletTransactionService walletTransactionService,
            BusinessRepository businessRepository,
            RoomRepository roomRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            AdminManagementMapper mapper,
            PlatformAuditService auditService,
            UserPasswordService userPasswordService,
            UserSessionService userSessionService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionService = walletTransactionService;
        this.businessRepository = businessRepository;
        this.roomRepository = roomRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mapper = mapper;
        this.auditService = auditService;
        this.userPasswordService = userPasswordService;
        this.userSessionService = userSessionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserPageDto users(String suppliedSearch, int page, int size) {
        Page<UserEntity> result = userRepository.searchForAdmin(normalizeSearch(suppliedSearch), PageRequest.of(page, size));
        return mapUsers(result);
    }

    @Transactional(readOnly = true)
    public AdminUserPageDto usersByNameAndPhone(String suppliedName, String suppliedPhone, int page, int size) {
        Page<UserEntity> result = userRepository.searchForAdminByNameAndPhone(
                normalizeSearch(suppliedName),
                normalizePhoneSearch(suppliedPhone),
                PageRequest.of(page, size)
        );
        return mapUsers(result);
    }

    private AdminUserPageDto mapUsers(Page<UserEntity> result) {
        List<Long> userIds = result.getContent().stream().map(UserEntity::getId).toList();
        Map<Long, Long> balances = balances(userIds);
        List<AdminUserDto> items = result.getContent().stream()
                .map(user -> mapper.toAdminUserDto(user, balances.getOrDefault(user.getId(), 0L)))
                .toList();
        return new AdminUserPageDto(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminBusinessPageDto businesses(String suppliedSearch, int page, int size) {
        Page<BusinessEntity> result = businessRepository.searchForAdmin(normalizeSearch(suppliedSearch), PageRequest.of(page, size));
        List<Long> businessIds = result.getContent().stream().map(BusinessEntity::getId).toList();
        Map<Long, Long> roomCounts = roomCounts(businessIds);
        Map<Long, ProviderSubscriptionEntity> subscriptions = subscriptions(businessIds);
        List<AdminBusinessDto> items = result.getContent().stream()
                .map(business -> mapper.toAdminBusinessDto(
                        business,
                        roomCounts.getOrDefault(business.getId(), 0L),
                        subscriptions.get(business.getId())
                ))
                .toList();
        return new AdminBusinessPageDto(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public WalletTransactionDto creditCoins(
            String actorUsername,
            long userId,
            AdminCoinCreditRequestDto request
    ) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
        String reason = request.reason().trim();
        WalletTransactionDto transaction = walletTransactionService.apply(
                userId,
                new WalletTransactionCommandDto(
                        WalletTransactionType.ADMIN_CREDIT,
                        request.amount(),
                        WalletActorType.ADMIN,
                        null,
                        actorUsername,
                        "admin-credit:" + actorUsername + ":" + request.idempotencyKey().trim(),
                        reason
                )
        );
        auditService.record(
                "ADMIN",
                actorUsername,
                "USER_COINS_CREDITED",
                "USER",
                userId,
                "coins=" + request.amount() + ",reference=" + transaction.referenceKey()
        );
        return transaction;
    }

    @Transactional
    public void changeUserPassword(
            String actorUsername,
            long userId,
            AdminUserPasswordUpdateRequestDto request
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
        if (user.getStatus() == UserStatus.ANONYMIZED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anonimləşdirilmiş istifadəçinin şifrəsi dəyişdirilə bilməz.");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Qeydiyyatı tamamlanmamış istifadəçinin şifrəsi dəyişdirilə bilməz.");
        }

        user.setPasswordHash(userPasswordService.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now(clock));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.PASSWORD_RESET_REQUIRED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);
        userSessionService.revokeAllSessionsForCredentialsChange(userId);
        auditService.record(
                "ADMIN",
                actorUsername,
                "USER_PASSWORD_CHANGED",
                "USER",
                userId,
                "reason=" + request.reason().trim()
        );
    }

    @Transactional
    public AdminBusinessDto increaseRoomLimit(
            String actorUsername,
            long businessId,
            AdminRoomLimitUpdateRequestDto request
    ) {
        BusinessEntity business = businessRepository.findByIdForUpdate(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes tapılmadı."));
        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByScopeTypeAndScopeIdForUpdate(ProviderScopeType.BUSINESS, businessId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Otaq limitini dəyişmək üçün biznesin abunəlik qeydi olmalıdır."
                ));
        long roomCount = roomRepository.countByBranchBusinessIdAndStatusNot(businessId, RoomStatus.ARCHIVED);
        if (request.roomLimit() < roomCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yeni limit mövcud otaq sayından az ola bilməz.");
        }
        if (request.roomLimit() < subscription.getRoomLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin paneldən otaq limiti yalnız artırıla bilər.");
        }
        if (request.roomLimit() == subscription.getRoomLimit()) {
            return mapper.toAdminBusinessDto(business, roomCount, subscription);
        }
        int previousLimit = subscription.getRoomLimit();
        subscription.setRoomLimit(request.roomLimit());
        ProviderSubscriptionEntity saved = subscriptionRepository.save(subscription);
        auditService.record(
                "ADMIN",
                actorUsername,
                "BUSINESS_ROOM_LIMIT_INCREASED",
                "BUSINESS",
                businessId,
                "from=" + previousLimit + ",to=" + request.roomLimit() + ",reason=" + request.reason().trim()
        );
        return mapper.toAdminBusinessDto(business, roomCount, saved);
    }

    private Map<Long, Long> roomCounts(Collection<Long> businessIds) {
        if (businessIds.isEmpty()) return Map.of();
        return roomRepository.countActiveRoomsByBusinessIds(businessIds, RoomStatus.ARCHIVED).stream()
                .collect(Collectors.toMap(BusinessRoomCountProjection::getBusinessId, BusinessRoomCountProjection::getRoomCount));
    }

    private Map<Long, Long> balances(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return walletAccountRepository.findBalancesByUserIds(userIds).stream()
                .collect(Collectors.toMap(WalletUserBalanceProjection::getUserId, WalletUserBalanceProjection::getBalance));
    }

    private Map<Long, ProviderSubscriptionEntity> subscriptions(Collection<Long> businessIds) {
        if (businessIds.isEmpty()) return Map.of();
        return subscriptionRepository.findByScopeTypeAndScopeIdIn(ProviderScopeType.BUSINESS, businessIds).stream()
                .collect(Collectors.toMap(ProviderSubscriptionEntity::getScopeId, Function.identity()));
    }

    private String normalizeSearch(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("0[1-9]\\d{8}")) normalized = "+994" + normalized.substring(1);
        return "%" + normalized + "%";
    }

    private String normalizePhoneSearch(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().replaceAll("[^0-9+]", "");
        if (normalized.matches("0[1-9]\\d{8}")) normalized = "+994" + normalized.substring(1);
        if (normalized.isBlank()) return null;
        return "%" + normalized + "%";
    }
}
