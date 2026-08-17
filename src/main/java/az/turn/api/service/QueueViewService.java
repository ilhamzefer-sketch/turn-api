package az.turn.api;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueueViewService {

    private static final long DEFAULT_AVERAGE_SERVICE_MINUTES = 5;

    private final QueueManagerRepository queueManagerRepository;

    public QueueViewService(QueueManagerRepository queueManagerRepository) {
        this.queueManagerRepository = queueManagerRepository;
    }

    public List<String> copyCategories(QueueEntity entity) {
        return entity.getCategories() == null ? List.of() : List.copyOf(entity.getCategories());
    }

    public QueueResponse toQueueResponse(QueueEntity entity) {
        String managerUsername = queueManagerRepository.findByQueueId(entity.getId())
                .map(QueueManagerEntity::getUsername)
                .orElse(null);

        return new QueueResponse(
                entity.getId(),
                entity.getRegistration().getId(),
                entity.getRegistration().getRegistrationType(),
                entity.getRegistration().getFullName(),
                entity.getRegistration().getEmail(),
                entity.getAddress(),
                entity.getServiceName(),
                copyCategories(entity),
                entity.getQrToken(),
                managerUsername,
                entity.getResetMode(),
                entity.getResetAt(),
                entity.isActive()
        );
    }

    public CustomerQueueHistoryItemResponse toCustomerQueueHistoryItemResponse(CustomerQueueEntryEntity entry) {
        QueueEntity queue = entry.getQueue();
        long waitingAhead = Math.max(0, entry.getQueueNumber() - queue.getCurrentServingNumber() - 1);
        return new CustomerQueueHistoryItemResponse(
                entry.getId(),
                queue.getId(),
                entry.getDisplayName() == null || entry.getDisplayName().isBlank() ? queue.getServiceName() : entry.getDisplayName(),
                queue.getServiceName(),
                queue.getAddress(),
                copyCategories(queue),
                entry.getQueueNumber(),
                queue.getCurrentServingNumber(),
                waitingAhead,
                resolveAverageServiceMinutes(queue),
                entry.getRating(),
                entry.getRatingNote(),
                entry.getJoinedAt()
        );
    }

    public CustomerQueueEntryResponse toCustomerQueueEntryResponse(CustomerQueueEntryEntity entry) {
        return new CustomerQueueEntryResponse(
                entry.getId(),
                entry.getDisplayName(),
                entry.getRating(),
                entry.getRatingNote(),
                entry.getJoinedAt()
        );
    }

    public UserQueueHistoryItemDto toUserQueueHistoryItemDto(CustomerQueueEntryEntity entry) {
        QueueEntity queue = entry.getQueue();
        long waitingAhead = Math.max(0, entry.getQueueNumber() - queue.getCurrentServingNumber() - 1);
        return new UserQueueHistoryItemDto(
                entry.getId(),
                "REGISTERED",
                queue.getId(),
                entry.getDisplayName() == null || entry.getDisplayName().isBlank()
                        ? queue.getServiceName()
                        : entry.getDisplayName(),
                queue.getServiceName(),
                queue.getAddress(),
                copyCategories(queue),
                entry.getQueueNumber(),
                queue.getCurrentServingNumber(),
                waitingAhead,
                resolveAverageServiceMinutes(queue),
                entry.getRating(),
                entry.getRatingNote(),
                entry.getJoinedAt()
        );
    }

    public UserQueueHistoryItemDto toGuestQueueHistoryItemDto(GuestQueueEntryEntity entry) {
        QueueEntity queue = entry.getQueue();
        long waitingAhead = Math.max(0, entry.getQueueNumber() - queue.getCurrentServingNumber() - 1);
        return new UserQueueHistoryItemDto(
                entry.getId(),
                "GUEST",
                queue.getId(),
                queue.getServiceName(),
                queue.getServiceName(),
                queue.getAddress(),
                copyCategories(queue),
                entry.getQueueNumber(),
                queue.getCurrentServingNumber(),
                waitingAhead,
                resolveAverageServiceMinutes(queue),
                null,
                null,
                entry.getJoinedAt()
        );
    }

    public QueueDetailResponse toQueueDetailResponse(QueueEntity entity) {
        long averageServiceMinutes = resolveAverageServiceMinutes(entity);
        long waitingCount = Math.max(0, entity.getLastIssuedNumber() - entity.getCurrentServingNumber());
        long estimatedWaitMinutes = waitingCount * averageServiceMinutes;
        String managerUsername = queueManagerRepository.findByQueueId(entity.getId())
                .map(QueueManagerEntity::getUsername)
                .orElse(null);

        return new QueueDetailResponse(
                entity.getId(),
                entity.getRegistration().getId(),
                entity.getRegistration().getRegistrationType(),
                entity.getRegistration().getFullName(),
                entity.getRegistration().getEmail(),
                entity.getAddress(),
                entity.getServiceName(),
                copyCategories(entity),
                entity.getQrToken(),
                entity.getCurrentServingNumber(),
                entity.getLastIssuedNumber(),
                waitingCount,
                entity.getLastIssuedNumber(),
                averageServiceMinutes,
                estimatedWaitMinutes,
                entity.getLastAdvancedAt(),
                managerUsername,
                entity.getResetMode(),
                entity.getResetAt(),
                entity.isActive()
        );
    }

    public long resolveAverageServiceMinutes(QueueEntity queue) {
        return Math.max(1, queue.getAverageServiceMinutes() == 0 ? DEFAULT_AVERAGE_SERVICE_MINUTES : queue.getAverageServiceMinutes());
    }

    public String buildScanMessage(long queueNumber, long currentServingNumber, long waitingCount, long estimatedWaitMinutes) {
        return "Hal hazirda novbeniz " + queueNumber + "-dir. Hal hazirda "
                + currentServingNumber + " nomreli musteriye xidmet olunur. "
                + waitingCount + " nefer gozlemededir. "
                + "Ortalama size novbeniz " + formatDuration(estimatedWaitMinutes) + " hesablanir.";
    }

    private String formatDuration(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours <= 0) {
            return minutes + " deq";
        }
        return hours + " saat " + minutes + " deq";
    }
}
