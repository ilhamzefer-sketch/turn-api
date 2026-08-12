package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class CustomerQueueService {

    private final CustomerRepository customerRepository;
    private final QueueRepository queueRepository;
    private final CustomerQueueEntryRepository customerQueueEntryRepository;
    private final GuestQueueEntryRepository guestQueueEntryRepository;
    private final QueueManagementService queueManagementService;
    private final QueueViewService queueViewService;

    public CustomerQueueService(
            CustomerRepository customerRepository,
            QueueRepository queueRepository,
            CustomerQueueEntryRepository customerQueueEntryRepository,
            GuestQueueEntryRepository guestQueueEntryRepository,
            QueueManagementService queueManagementService,
            QueueViewService queueViewService
    ) {
        this.customerRepository = customerRepository;
        this.queueRepository = queueRepository;
        this.customerQueueEntryRepository = customerQueueEntryRepository;
        this.guestQueueEntryRepository = guestQueueEntryRepository;
        this.queueManagementService = queueManagementService;
        this.queueViewService = queueViewService;
    }

    @Transactional
    public QueueScanResponse scanQueue(QueueScanRequest request) {
        String qrToken = normalizeRequired(request.qrToken(), "QR token mutleqdir.");
        QueueEntity queue = queueRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu QR token ucun novbe tapilmadi."));
        queue = queueManagementService.ensureQueueState(queue);
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu novbe artiq deaktivdir.");
        }
        QueueJoinResult result = createQueueEntry(queue, request.customerId(), request.displayName(), request.firstName(), request.lastName());

        return new QueueScanResponse(
                result.queue().getId(),
                result.queue().getAddress(),
                result.queue().getServiceName(),
                queueViewService.copyCategories(result.queue()),
                result.queue().getRegistration().getFullName(),
                result.queueNumber(),
                result.queue().getCurrentServingNumber(),
                result.waitingCount(),
                result.queue().getLastIssuedNumber(),
                result.estimatedWaitMinutes(),
                queueViewService.resolveAverageServiceMinutes(result.queue()),
                result.queue().getQrToken(),
                queueViewService.buildScanMessage(
                        result.queueNumber(),
                        result.queue().getCurrentServingNumber(),
                        result.waitingCount(),
                        result.estimatedWaitMinutes()
                )
        );
    }

    @Transactional
    public CustomerQueueJoinResponse joinQueue(CustomerQueueJoinRequest request) {
        if (request.customerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evdan qosulmaq ucun musteri kimi daxil olun.");
        }
        QueueEntity queue = queueManagementService.ensureQueueState(resolveQueueForJoin(request));
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu novbe artiq deaktivdir.");
        }
        QueueJoinResult result = createQueueEntry(queue, request.customerId(), request.displayName(), null, null);

        return new CustomerQueueJoinResponse(
                result.entry() != null ? result.entry().getId() : null,
                result.entry() != null ? result.entry().getCustomer().getId() : null,
                result.queue().getId(),
                result.queue().getAddress(),
                result.queue().getServiceName(),
                queueViewService.copyCategories(result.queue()),
                result.queue().getRegistration().getFullName(),
                result.queueNumber(),
                result.queue().getCurrentServingNumber(),
                result.waitingCount(),
                result.queue().getLastIssuedNumber(),
                result.estimatedWaitMinutes(),
                queueViewService.resolveAverageServiceMinutes(result.queue()),
                result.queue().getQrToken(),
                result.entry() != null ? result.entry().getDisplayName() : null,
                queueViewService.buildScanMessage(
                        result.queueNumber(),
                        result.queue().getCurrentServingNumber(),
                        result.waitingCount(),
                        result.estimatedWaitMinutes()
                )
        );
    }

    @Transactional
    public List<CustomerQueueHistoryItemResponse> getCustomerHistory(long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri tapilmadi.");
        }

        return customerQueueEntryRepository.findByCustomerIdOrderByJoinedAtDesc(customerId)
                .stream()
                .map(entry -> {
                    queueManagementService.ensureQueueState(entry.getQueue());
                    return queueViewService.toCustomerQueueHistoryItemResponse(entry);
                })
                .toList();
    }

    @Transactional
    public CustomerQueueEntryResponse renameCustomerQueueEntry(long entryId, CustomerQueueRenameRequest request) {
        CustomerQueueEntryEntity entry = findCustomerQueueEntry(entryId);
        validateCustomerQueueEntryAccess(entry, request.customerId());
        entry.setDisplayName(normalizeRequired(request.displayName(), "Novbe adi mutleqdir."));
        return queueViewService.toCustomerQueueEntryResponse(customerQueueEntryRepository.save(entry));
    }

    @Transactional
    public CustomerQueueEntryResponse rateCustomerQueueEntry(long entryId, CustomerQueueRatingRequest request) {
        CustomerQueueEntryEntity entry = findCustomerQueueEntry(entryId);
        validateCustomerQueueEntryAccess(entry, request.customerId());

        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qiymet 1 ile 5 arasi olmalidir.");
        }

        entry.setRating(request.rating());
        entry.setRatingNote(request.note() == null ? null : request.note().trim());
        return queueViewService.toCustomerQueueEntryResponse(customerQueueEntryRepository.save(entry));
    }

    private CustomerQueueEntryEntity findCustomerQueueEntry(long entryId) {
        return customerQueueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri novbe kaydi tapilmadi."));
    }

    private QueueEntity resolveQueueForJoin(CustomerQueueJoinRequest request) {
        if (request.queueId() != null) {
            return queueManagementService.findQueue(request.queueId());
        }
        if (request.qrToken() != null && !request.qrToken().isBlank()) {
            return queueRepository.findByQrToken(request.qrToken().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu QR token ucun novbe tapilmadi."));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Queue ID ve ya QR token mutleqdir.");
    }

    private QueueJoinResult createQueueEntry(QueueEntity queue, Long customerId, String displayName, String guestFirstName, String guestLastName) {
        queue.setLastIssuedNumber(queue.getLastIssuedNumber() + 1);
        QueueEntity updatedQueue = queueRepository.save(queue);

        long queueNumber = updatedQueue.getLastIssuedNumber();
        long waitingCount = Math.max(0, updatedQueue.getLastIssuedNumber() - updatedQueue.getCurrentServingNumber());
        long waitingBeforeThisCustomer = Math.max(0, queueNumber - updatedQueue.getCurrentServingNumber() - 1);
        long estimatedWaitMinutes = waitingBeforeThisCustomer * queueViewService.resolveAverageServiceMinutes(updatedQueue);

        CustomerQueueEntryEntity savedEntry = null;
        if (customerId != null) {
            CustomerEntity customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri tapilmadi."));

            CustomerQueueEntryEntity entry = new CustomerQueueEntryEntity();
            entry.setCustomer(customer);
            entry.setQueue(updatedQueue);
            entry.setQueueNumber(queueNumber);
            entry.setDisplayName(displayName == null || displayName.isBlank() ? updatedQueue.getServiceName() : displayName.trim());
            savedEntry = customerQueueEntryRepository.save(entry);
        } else {
            GuestQueueEntryEntity guestEntry = new GuestQueueEntryEntity();
            guestEntry.setQueue(updatedQueue);
            guestEntry.setQueueNumber(queueNumber);
            guestEntry.setFirstName(normalizeRequired(guestFirstName, "Qeydiyyatsiz musteri ucun ad mutleqdir."));
            guestEntry.setLastName(normalizeRequired(guestLastName, "Qeydiyyatsiz musteri ucun soyad mutleqdir."));
            guestQueueEntryRepository.save(guestEntry);
        }

        return new QueueJoinResult(updatedQueue, savedEntry, queueNumber, waitingCount, estimatedWaitMinutes);
    }

    private void validateCustomerQueueEntryAccess(CustomerQueueEntryEntity entry, Long customerId) {
        if (customerId == null || !Objects.equals(entry.getCustomer().getId(), customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu musteri novbe kaydina icazeniz yoxdur.");
        }
    }

    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value.trim();
    }

    private record QueueJoinResult(
            QueueEntity queue,
            CustomerQueueEntryEntity entry,
            long queueNumber,
            long waitingCount,
            long estimatedWaitMinutes
    ) {
    }
}
