package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPlatformService {
    private static final List<SupportRequestStatus> OPEN_STATUSES = List.of(
            SupportRequestStatus.OPEN,
            SupportRequestStatus.IN_REVIEW
    );

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RoomRepository roomRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final AccountOwnershipDisputeRepository disputeRepository;
    private final PhoneChangeRequestRepository phoneChangeRepository;
    private final AccountDeletionRequestRepository deletionRepository;

    public AdminPlatformService(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            RoomRepository roomRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            PaymentSessionRepository paymentSessionRepository,
            AccountOwnershipDisputeRepository disputeRepository,
            PhoneChangeRequestRepository phoneChangeRepository,
            AccountDeletionRequestRepository deletionRepository
    ) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.roomRepository = roomRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.disputeRepository = disputeRepository;
        this.phoneChangeRepository = phoneChangeRepository;
        this.deletionRepository = deletionRepository;
    }

    @Transactional(readOnly = true)
    public AdminPlatformOverviewDto overview() {
        return new AdminPlatformOverviewDto(
                userRepository.count(), userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                businessRepository.countByStatusNot(ProviderStatus.ARCHIVED),
                roomRepository.countByStatusNot(RoomStatus.ARCHIVED),
                subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                subscriptionRepository.countByStatus(SubscriptionStatus.GRACE_PERIOD),
                subscriptionRepository.countByStatus(SubscriptionStatus.SUSPENDED),
                paymentSessionRepository.countByPaymentPurposeAndStatus(
                        PaymentPurpose.PROVIDER_SUBSCRIPTION,
                        PaymentStatus.COMPLETED
                ),
                disputeRepository.countByStatusIn(OPEN_STATUSES),
                phoneChangeRepository.countByStatusIn(OPEN_STATUSES),
                deletionRepository.countByStatusIn(OPEN_STATUSES)
        );
    }
}
