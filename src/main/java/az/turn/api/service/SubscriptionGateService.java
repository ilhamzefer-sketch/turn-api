package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SubscriptionGateService {
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final ProviderScopeAccessService scopeAccessService;
    private final RoomRepository roomRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final Clock clock;
    private final boolean enforcementEnabled;

    public SubscriptionGateService(
            ProviderSubscriptionRepository subscriptionRepository,
            ProviderScopeAccessService scopeAccessService,
            RoomRepository roomRepository,
            BusinessMembershipRepository membershipRepository,
            Clock clock,
            @Value("${app.subscription.enforcement-enabled:true}") boolean enforcementEnabled
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.scopeAccessService = scopeAccessService;
        this.roomRepository = roomRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
        this.enforcementEnabled = enforcementEnabled;
    }

    @Transactional
    public void requireRoomOperations(RoomEntity room) {
        if (!enforcementEnabled) return;
        ProviderSubscriptionEntity subscription = requireOperational(
                scopeAccessService.roomScopeType(room),
                scopeAccessService.roomScopeId(room)
        );
        enforceUsage(subscription);
    }

    @Transactional
    public void requirePublish(RoomEntity room) {
        requireRoomOperations(room);
    }

    @Transactional
    public void requireBusinessRoomCreation(long businessId) {
        if (!enforcementEnabled) return;
        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByScopeTypeAndScopeIdForUpdate(ProviderScopeType.BUSINESS, businessId)
                .orElseThrow(() -> paymentRequired("Biznes otağı yaratmaq üçün aktiv abunəlik tələb olunur."));
        refreshStatus(subscription);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.GRACE_PERIOD) {
            throw paymentRequired("Abunəlik aktiv deyil. Yeni otaq yaratmaq üçün abunəliyi yeniləyin.");
        }
        long roomCount = roomRepository.countByBranchBusinessIdAndStatusNot(businessId, RoomStatus.ARCHIVED);
        if (roomCount >= subscription.getRoomLimit()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Biznes üçün " + subscription.getRoomLimit()
                            + " otaq limitinə çatmısınız. Daha çox otaq üçün bizimlə əlaqə saxlayın."
            );
        }
    }

    private ProviderSubscriptionEntity requireOperational(ProviderScopeType scopeType, long scopeId) {
        ProviderSubscriptionEntity subscription = subscriptionRepository.findByScopeTypeAndScopeId(scopeType, scopeId)
                .orElseThrow(() -> paymentRequired("Aktiv abunəlik tələb olunur."));
        refreshStatus(subscription);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE
                && subscription.getStatus() != SubscriptionStatus.GRACE_PERIOD) {
            throw paymentRequired("Abunəlik aktiv deyil. Yeni əməliyyatlar dayandırılıb.");
        }
        return subscription;
    }

    private void refreshStatus(ProviderSubscriptionEntity subscription) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (subscription.getExpiresAt() == null || now.isBefore(subscription.getExpiresAt())) return;
        if (subscription.getGraceEndsAt() != null && now.isBefore(subscription.getGraceEndsAt())) {
            subscription.setStatus(SubscriptionStatus.GRACE_PERIOD);
        } else {
            subscription.setStatus(SubscriptionStatus.SUSPENDED);
        }
        subscriptionRepository.save(subscription);
    }

    private void enforceUsage(ProviderSubscriptionEntity subscription) {
        long roomCount = subscription.getScopeType() == ProviderScopeType.BUSINESS
                ? roomRepository.countByBranchBusinessIdAndStatusNot(subscription.getScopeId(), RoomStatus.ARCHIVED)
                : roomRepository.countByIndividualWorkspaceIdAndStatusNot(subscription.getScopeId(), RoomStatus.ARCHIVED);
        long employeeCount = subscription.getScopeType() == ProviderScopeType.BUSINESS
                ? membershipRepository.countByBusinessIdAndStatus(subscription.getScopeId(), BusinessMembershipStatus.ACTIVE)
                : 1;
        boolean overLimit = roomCount > subscription.getRoomLimit() || employeeCount > subscription.getEmployeeLimit();
        if (!overLimit) {
            if (subscription.getUsageGraceEndsAt() != null) {
                subscription.setUsageGraceEndsAt(null);
                subscriptionRepository.save(subscription);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (subscription.getUsageGraceEndsAt() == null) {
            subscription.setUsageGraceEndsAt(now.plusDays(7));
            subscriptionRepository.save(subscription);
            return;
        }
        if (!now.isBefore(subscription.getUsageGraceEndsAt())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cari otaq və ya əməkdaş sayı abunəlik limitini keçir. Yeni əməliyyatlar dayandırılıb."
            );
        }
    }

    private ResponseStatusException paymentRequired(String message) {
        return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, message);
    }
}
