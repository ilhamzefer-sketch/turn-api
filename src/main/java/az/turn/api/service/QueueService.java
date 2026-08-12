package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QueueService {

    private final QueueManagementService queueManagementService;
    private final CustomerQueueService customerQueueService;
    private final QueueAdminService queueAdminService;

    public QueueService(
            QueueManagementService queueManagementService,
            CustomerQueueService customerQueueService,
            QueueAdminService queueAdminService
    ) {
        this.queueManagementService = queueManagementService;
        this.customerQueueService = customerQueueService;
        this.queueAdminService = queueAdminService;
    }

    @Transactional
    public QueueResponse createQueue(QueueCreateRequest request) {
        return queueManagementService.createQueue(request);
    }

    @Transactional
    public List<QueueResponse> getQueues(long registrationId) {
        return queueManagementService.getQueues(registrationId);
    }

    @Transactional
    public List<QueueResponse> getPublicQueues() {
        return queueManagementService.getPublicQueues();
    }

    @Transactional
    public QueueDetailResponse getQueueDetail(long queueId, Long registrationId, Long queueManagerId) {
        return queueManagementService.getQueueDetail(queueId, registrationId, queueManagerId);
    }

    @Transactional
    public QueueScanResponse scanQueue(QueueScanRequest request) {
        return customerQueueService.scanQueue(request);
    }

    @Transactional
    public CustomerQueueJoinResponse joinQueue(CustomerQueueJoinRequest request) {
        return customerQueueService.joinQueue(request);
    }

    @Transactional
    public QueueDetailResponse advanceQueue(long queueId, QueueAdvanceRequest request) {
        return queueManagementService.advanceQueue(queueId, request);
    }

    @Transactional
    public QueueDetailResponse resetQueue(long queueId, QueueResetRequest request) {
        return queueManagementService.resetQueue(queueId, request);
    }

    @Transactional
    public List<CustomerQueueHistoryItemResponse> getCustomerHistory(long customerId) {
        return customerQueueService.getCustomerHistory(customerId);
    }

    @Transactional
    public CustomerQueueEntryResponse renameCustomerQueueEntry(long entryId, CustomerQueueRenameRequest request) {
        return customerQueueService.renameCustomerQueueEntry(entryId, request);
    }

    @Transactional
    public CustomerQueueEntryResponse rateCustomerQueueEntry(long entryId, CustomerQueueRatingRequest request) {
        return customerQueueService.rateCustomerQueueEntry(entryId, request);
    }

    @Transactional
    public AdminDashboardResponse getAdminDashboard(String search, String registrationType, String paymentStatus, String month) {
        return queueAdminService.getAdminDashboard(search, registrationType, paymentStatus, month);
    }
}
