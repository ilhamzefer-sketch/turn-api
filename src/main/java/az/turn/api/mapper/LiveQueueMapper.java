package az.turn.api;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LiveQueueMapper {
    private static final List<LiveQueueEntryStatus> ACTIVE_STATUSES = List.of(
            LiveQueueEntryStatus.WAITING,
            LiveQueueEntryStatus.CURRENT,
            LiveQueueEntryStatus.SKIPPED
    );

    public LiveQueueSessionDto toOperatorDto(
            LiveQueueSessionEntity session,
            List<LiveQueueEntryEntity> entries,
            boolean accepting,
            LocalDateTime nextOpeningAt
    ) {
        return new LiveQueueSessionDto(
                session.getId(),
                session.getRoom().getId(),
                session.getRoom().getName(),
                session.getServiceDate(),
                session.getStatus(),
                session.getAcceptanceOverride(),
                accepting,
                nextOpeningAt,
                session.getNextResetAt(),
                currentReference(entries),
                count(entries, LiveQueueEntryStatus.WAITING),
                count(entries, LiveQueueEntryStatus.SKIPPED),
                entries.stream().filter(value -> ACTIVE_STATUSES.contains(value.getStatus())).count(),
                session.getOpenedAt(),
                session.getClosedAt(),
                entries.stream().map(this::toEntryDto).toList()
        );
    }

    public LiveQueuePublicDto toPublicDto(
            RoomEntity room,
            LiveQueueSessionEntity session,
            List<LiveQueueEntryEntity> entries,
            boolean accepting,
            LocalDateTime nextOpeningAt
    ) {
        long waitingCount = count(entries, LiveQueueEntryStatus.WAITING);
        return new LiveQueuePublicDto(
                room.getId(),
                room.getName(),
                session == null ? null : session.getId(),
                session == null ? LiveQueueSessionStatus.CLOSED : session.getStatus(),
                accepting,
                nextOpeningAt,
                session == null ? null : session.getNextResetAt(),
                currentReference(entries),
                waitingCount,
                waitingCount * room.getDefaultSlotDurationMinutes(),
                entries.stream()
                        .filter(value -> ACTIVE_STATUSES.contains(value.getStatus()))
                        .map(this::toPublicEntryDto)
                        .toList()
        );
    }

    public LiveQueueEntryDto toEntryDto(LiveQueueEntryEntity entry) {
        GuestContactEntity guest = entry.getGuestContact();
        UserEntity linkedUser = entry.getUser() != null
                ? entry.getUser()
                : guest == null ? null : guest.getLinkedUser();
        return new LiveQueueEntryDto(
                entry.getId(),
                entry.getPublicReference(),
                entry.getQueuePosition(),
                entry.getStatus(),
                entry.getSource(),
                entry.getPrivateDisplayName(),
                guest == null ? linkedUser.getNormalizedPhone() : guest.getNormalizedPhone(),
                linkedUser == null ? null : linkedUser.getId(),
                entry.getInternalNote(),
                entry.getCreatedByUser() == null ? null : entry.getCreatedByUser().getId(),
                entry.getCreatedAt(),
                entry.getCompletedAt(),
                entry.getRemovedAt()
        );
    }

    public LiveQueueJoinResponseDto toJoinDto(
            LiveQueueEntryEntity entry,
            List<LiveQueueEntryEntity> activeEntries,
            boolean accepting,
            int durationMinutes
    ) {
        long peopleAhead = peopleAhead(entry, activeEntries);
        return new LiveQueueJoinResponseDto(
                entry.getSession().getId(),
                entry.getPublicReference(),
                entry.getQueuePosition(),
                entry.getStatus(),
                peopleAhead,
                peopleAhead * durationMinutes,
                currentReference(activeEntries),
                accepting
        );
    }

    public LiveQueueParticipantStatusDto toParticipantStatusDto(
            LiveQueueEntryEntity entry,
            List<LiveQueueEntryEntity> activeEntries,
            boolean accepting,
            int durationMinutes
    ) {
        long peopleAhead = peopleAhead(entry, activeEntries);
        return new LiveQueueParticipantStatusDto(
                entry.getPublicReference(),
                entry.getStatus(),
                peopleAhead,
                peopleAhead * durationMinutes,
                currentReference(activeEntries),
                accepting
        );
    }

    public LiveQueueHistoryItemDto toHistoryDto(LiveQueueEntryEntity entry) {
        return new LiveQueueHistoryItemDto(
                entry.getId(),
                entry.getRoom().getId(),
                entry.getRoom().getName(),
                entry.getPublicReference(),
                entry.getQueuePosition(),
                entry.getStatus(),
                entry.getSource(),
                entry.getCreatedAt(),
                entry.getCompletedAt()
        );
    }

    private LiveQueuePublicEntryDto toPublicEntryDto(LiveQueueEntryEntity entry) {
        return new LiveQueuePublicEntryDto(
                entry.getPublicReference(),
                entry.getQueuePosition(),
                entry.getStatus()
        );
    }

    private long peopleAhead(LiveQueueEntryEntity target, List<LiveQueueEntryEntity> activeEntries) {
        if (!ACTIVE_STATUSES.contains(target.getStatus())) return 0;
        return activeEntries.stream()
                .filter(value -> value.getStatus() == LiveQueueEntryStatus.CURRENT
                        || value.getStatus() == LiveQueueEntryStatus.WAITING)
                .filter(value -> value.getQueuePosition() < target.getQueuePosition())
                .count();
    }

    private long count(List<LiveQueueEntryEntity> entries, LiveQueueEntryStatus status) {
        return entries.stream().filter(value -> value.getStatus() == status).count();
    }

    private String currentReference(List<LiveQueueEntryEntity> entries) {
        return entries.stream()
                .filter(value -> value.getStatus() == LiveQueueEntryStatus.CURRENT)
                .map(LiveQueueEntryEntity::getPublicReference)
                .findFirst()
                .orElse(null);
    }
}
