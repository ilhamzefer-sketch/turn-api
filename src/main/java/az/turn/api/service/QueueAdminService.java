package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QueueAdminService {

    private static final long FERDI_PAYMENT_AMOUNT = 20;
    private static final long KORPORATIV_PAYMENT_AMOUNT = 100;

    private final RegistrationRepository registrationRepository;
    private final QueueRepository queueRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final CustomerRepository customerRepository;
    private final QueueManagementService queueManagementService;
    private final QueueViewService queueViewService;

    public QueueAdminService(
            RegistrationRepository registrationRepository,
            QueueRepository queueRepository,
            PaymentSessionRepository paymentSessionRepository,
            CustomerRepository customerRepository,
            QueueManagementService queueManagementService,
            QueueViewService queueViewService
    ) {
        this.registrationRepository = registrationRepository;
        this.queueRepository = queueRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.customerRepository = customerRepository;
        this.queueManagementService = queueManagementService;
        this.queueViewService = queueViewService;
    }

    @Transactional
    public AdminDashboardResponse getAdminDashboard(String search, String registrationType, String paymentStatus, String month) {
        List<RegistrationEntity> registrations = registrationRepository.findAll();
        List<QueueEntity> queues = queueRepository.findAll().stream().map(queueManagementService::ensureQueueState).toList();
        List<PaymentSessionEntity> paymentSessions = paymentSessionRepository.findAll();
        Map<Long, Long> queueCountByRegistrationId = queues.stream()
                .collect(Collectors.groupingBy(queue -> queue.getRegistration().getId(), Collectors.counting()));

        List<AdminRegistrationItemResponse> allItems = registrations.stream()
                .map(registration -> toAdminRegistrationItem(registration, queueCountByRegistrationId.getOrDefault(registration.getId(), 0L)))
                .sorted(Comparator.comparing(AdminRegistrationItemResponse::createdAt).reversed())
                .toList();

        List<AdminRegistrationItemResponse> filteredItems = allItems.stream()
                .filter(item -> matchesSearch(item, search))
                .filter(item -> matchesRegistrationType(item, registrationType))
                .filter(item -> matchesPaymentStatus(item, paymentStatus))
                .filter(item -> matchesMonth(item, month))
                .toList();

        long totalRevenue = paymentSessions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .mapToLong(PaymentSessionEntity::getAmount)
                .sum();

        AdminSummaryResponse summary = new AdminSummaryResponse(
                registrations.size(),
                registrations.stream().filter(RegistrationEntity::isPaid).count(),
                queues.size(),
                totalRevenue,
                customerRepository.count(),
                queues.stream().filter(QueueEntity::isActive).count(),
                registrations.stream().filter(item -> item.getStatus() == RegistrationStatus.EXPIRED).count(),
                paymentSessions.stream().filter(item -> item.getStatus() == PaymentStatus.PENDING).count(),
                paymentSessions.stream().filter(item -> item.getStatus() == PaymentStatus.COMPLETED).count()
        );

        List<AdminMonthlyPaymentResponse> monthlyPayments = paymentSessions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED && payment.getCompletedAt() != null)
                .collect(Collectors.groupingBy(payment -> YearMonth.from(payment.getCompletedAt()), Collectors.toList()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<YearMonth, List<PaymentSessionEntity>>comparingByKey().reversed())
                .map(entry -> new AdminMonthlyPaymentResponse(
                        entry.getKey().toString(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(PaymentSessionEntity::getAmount).sum()
                ))
                .toList();

        List<AdminPaymentItemResponse> recentPayments = paymentSessions.stream()
                .sorted(Comparator.comparing(PaymentSessionEntity::getCreatedAt).reversed())
                .limit(50)
                .map(payment -> new AdminPaymentItemResponse(
                        payment.getId(),
                        payment.getFirstName() + " " + payment.getLastName(),
                        payment.getEmail(),
                        payment.getRegistrationType(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getProvider(),
                        payment.getExternalOrderId(),
                        payment.getPaymentReference(),
                        payment.getCreatedAt(),
                        payment.getCompletedAt()
                ))
                .toList();

        List<AdminQueueItemResponse> queueItems = queues.stream()
                .sorted(Comparator.comparing(QueueEntity::getId).reversed())
                .map(queue -> new AdminQueueItemResponse(
                        queue.getId(),
                        queue.getServiceName(),
                        queue.getAddress(),
                        queue.getRegistration().getFullName(),
                        queue.getRegistration().getRegistrationType(),
                        queue.getCurrentServingNumber(),
                        queue.getLastIssuedNumber(),
                        Math.max(0, queue.getLastIssuedNumber() - queue.getCurrentServingNumber()),
                        queueViewService.resolveAverageServiceMinutes(queue),
                        queue.getResetMode(),
                        queue.getResetAt(),
                        queue.isActive()
                ))
                .toList();

        return new AdminDashboardResponse(summary, monthlyPayments, filteredItems, recentPayments, queueItems);
    }

    private AdminRegistrationItemResponse toAdminRegistrationItem(RegistrationEntity entity, long queueCount) {
        return new AdminRegistrationItemResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getRegistrationType(),
                entity.getStatus(),
                entity.isPaid(),
                entity.getPaymentReference(),
                resolvePaymentAmount(entity),
                queueCount,
                entity.getCreatedAt()
        );
    }

    private long resolvePaymentAmount(RegistrationEntity entity) {
        return entity.getRegistrationType() == RegistrationType.KORPORATIV ? KORPORATIV_PAYMENT_AMOUNT : FERDI_PAYMENT_AMOUNT;
    }

    private boolean matchesSearch(AdminRegistrationItemResponse item, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.trim().toLowerCase();
        return item.fullName().toLowerCase().contains(normalized)
                || item.email().toLowerCase().contains(normalized)
                || (item.paymentReference() != null && item.paymentReference().toLowerCase().contains(normalized));
    }

    private boolean matchesRegistrationType(AdminRegistrationItemResponse item, String registrationType) {
        if (registrationType == null || registrationType.isBlank() || "ALL".equalsIgnoreCase(registrationType)) {
            return true;
        }
        return item.registrationType().name().equalsIgnoreCase(registrationType.trim());
    }

    private boolean matchesPaymentStatus(AdminRegistrationItemResponse item, String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank() || "ALL".equalsIgnoreCase(paymentStatus)) {
            return true;
        }
        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            return item.paid();
        }
        if ("UNPAID".equalsIgnoreCase(paymentStatus)) {
            return !item.paid();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment filter duzgun deyil.");
    }

    private boolean matchesMonth(AdminRegistrationItemResponse item, String month) {
        if (month == null || month.isBlank()) {
            return true;
        }
        try {
            return YearMonth.from(item.createdAt()).equals(YearMonth.parse(month.trim()));
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month filter YYYY-MM formatinda olmalidir.");
        }
    }
}
