package az.turn.api;

public record QueueJoinResult(
        QueueEntity queue,
        CustomerQueueEntryEntity entry,
        long queueNumber,
        long waitingCount,
        long estimatedWaitMinutes
) {
}
