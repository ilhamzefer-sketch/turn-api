package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class LiveQueueIntegrationTests {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IndividualWorkspaceService workspaceService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomScheduleService scheduleService;
    @Autowired
    private RoomConfigurationService configurationService;
    @Autowired
    private LiveQueueSessionService sessionService;
    @Autowired
    private LiveQueueEntryService entryService;
    @Autowired
    private LiveQueueOperationService operationService;
    @Autowired
    private LiveQueueSessionRepository sessionRepository;
    @Autowired
    private LiveQueueEntryRepository entryRepository;
    @Autowired
    private QrCredentialService qrCredentialService;

    @Test
    void operatesGuestLifecycleAndPreservesResetHistory() {
        UserEntity owner = saveUser("+994507300001");
        long roomId = createPublishedLiveRoom(owner);
        LiveQueueSessionDto opened = sessionService.open(roomId, owner.getId());
        LiveQueueJoinResponseDto first = joinGuest(roomId, "Birinci", "0507300011");
        LiveQueueJoinResponseDto duplicate = joinGuest(roomId, "Təkrar", "0507300011");
        LiveQueueJoinResponseDto second = joinGuest(roomId, "İkinci", "0507300012");

        LiveQueueSessionDto called = operationService.callNext(roomId, owner.getId());
        LiveQueueSessionDto skipped = operationService.skip(roomId, entryId(first.publicReference()), owner.getId());
        operationService.restore(roomId, entryId(first.publicReference()), owner.getId());
        LiveQueueSessionDto completed = operationService.completeCurrent(roomId, owner.getId());
        LiveQueueJoinResponseDto third = joinGuest(roomId, "Üçüncü", "0507300013");
        LiveQueueSessionDto replacement = sessionService.reset(roomId, owner.getId());

        assertThat(duplicate.publicReference()).isEqualTo(first.publicReference());
        assertThat(first.queuePosition()).isEqualTo(1);
        assertThat(second.queuePosition()).isEqualTo(2);
        assertThat(called.currentPublicReference()).isEqualTo(first.publicReference());
        assertThat(skipped.currentPublicReference()).isEqualTo(second.publicReference());
        assertThat(completed.currentPublicReference()).isEqualTo(first.publicReference());
        assertThat(replacement.id()).isNotEqualTo(opened.id());
        assertThat(entryRepository.findByPublicReference(third.publicReference()).orElseThrow().getStatus())
                .isEqualTo(LiveQueueEntryStatus.RESET);
        assertThat(sessionRepository.findById(opened.id()).orElseThrow().getStatus())
                .isEqualTo(LiveQueueSessionStatus.CLOSED);
    }

    @Test
    void supportsManualEntriesPrivateEditsAndControlledOrdering() {
        UserEntity owner = saveUser("+994507300002");
        long roomId = createPublishedLiveRoom(owner);
        sessionService.open(roomId, owner.getId());
        LiveQueueEntryDto manual = entryService.addManual(
                roomId,
                owner.getId(),
                new LiveQueueManualEntryRequestDto(
                        "Telefon müştərisi",
                        "0507300021",
                        LiveQueueEntrySource.OWNER_PHONE,
                        "Saat 15:00-da zəng edib"
                )
        );
        joinGuest(roomId, "QR müştərisi", "0507300022");

        LiveQueueEntryDto updated = operationService.updateManual(
                roomId,
                manual.id(),
                owner.getId(),
                new LiveQueueEntryUpdateRequestDto("Dəyişmiş ad", "0507300023", "Yeni qeyd")
        );
        LiveQueueSessionDto reordered = operationService.sendToEnd(roomId, manual.id(), owner.getId());
        LiveQueuePublicDto publicView = sessionService.getPublic(roomId);

        assertThat(updated.displayName()).isEqualTo("Dəyişmiş ad");
        assertThat(updated.phone()).isEqualTo("+994507300023");
        assertThat(updated.internalNote()).isEqualTo("Yeni qeyd");
        assertThat(reordered.entries()).extracting(LiveQueueEntryDto::queuePosition).isSorted();
        assertThat(publicView.entries()).allMatch(value -> value.publicReference() != null);
    }

    @Test
    void preventsAnotherUserFromOperatingRoomQueue() {
        UserEntity owner = saveUser("+994507300003");
        UserEntity stranger = saveUser("+994507300004");
        long roomId = createPublishedLiveRoom(owner);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> sessionService.open(roomId, stranger.getId())
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createsRegeneratesAndRevokesIndependentQrCredentials() {
        UserEntity owner = saveUser("+994507300005");
        long roomId = createPublishedLiveRoom(owner);
        sessionService.open(roomId, owner.getId());
        QrCredentialDto first = qrCredentialService.create(roomId, owner.getId());
        QrCredentialDto second = qrCredentialService.create(roomId, owner.getId());
        QrCredentialDto regenerated = qrCredentialService.regenerate(roomId, first.id(), owner.getId());

        LiveQueueJoinResponseDto joined = entryService.joinGuest(
                qrCredentialService.resolveActiveRoom(regenerated.token()).getId(),
                new LiveQueueJoinRequestDto("QR guest", "0507300051"),
                LiveQueueEntrySource.QR
        );
        qrCredentialService.revoke(roomId, second.id(), owner.getId());

        assertThat(joined.publicReference()).startsWith("Q-");
        assertThat(qrCredentialService.resolveActiveRoom(regenerated.token()).getId()).isEqualTo(roomId);
        assertThrows(ResponseStatusException.class, () -> qrCredentialService.resolveActiveRoom(first.token()));
        assertThrows(ResponseStatusException.class, () -> qrCredentialService.resolveActiveRoom(second.token()));
    }

    @Test
    void serializesConcurrentNumberIssuanceAndReturnsSameActivePhoneEntry() throws Exception {
        UserEntity owner = saveUser("+994507300006");
        long roomId = createPublishedLiveRoom(owner);
        sessionService.open(roomId, owner.getId());
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<LiveQueueJoinResponseDto>> uniqueTasks = IntStream.range(0, 6)
                    .mapToObj(index -> (Callable<LiveQueueJoinResponseDto>) () -> joinGuest(
                            roomId,
                            "Concurrent " + index,
                            "05073100" + String.format("%02d", index)
                    ))
                    .toList();
            List<LiveQueueJoinResponseDto> results = collect(executor.invokeAll(uniqueTasks));
            List<Callable<LiveQueueJoinResponseDto>> duplicateTasks = IntStream.range(0, 4)
                    .mapToObj(index -> (Callable<LiveQueueJoinResponseDto>) () -> joinGuest(
                            roomId,
                            "Duplicate " + index,
                            "0507320000"
                    ))
                    .toList();
            List<LiveQueueJoinResponseDto> duplicates = collect(executor.invokeAll(duplicateTasks));

            Set<Long> positions = new HashSet<>(results.stream().map(LiveQueueJoinResponseDto::queuePosition).toList());
            Set<String> duplicateReferences = new HashSet<>(
                    duplicates.stream().map(LiveQueueJoinResponseDto::publicReference).toList()
            );
            assertThat(positions).hasSize(6);
            assertThat(duplicateReferences).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<LiveQueueJoinResponseDto> collect(List<Future<LiveQueueJoinResponseDto>> futures) throws Exception {
        List<LiveQueueJoinResponseDto> results = new ArrayList<>();
        for (Future<LiveQueueJoinResponseDto> future : futures) results.add(future.get());
        return results;
    }

    private LiveQueueJoinResponseDto joinGuest(long roomId, String name, String phone) {
        return entryService.joinGuest(
                roomId,
                new LiveQueueJoinRequestDto(name, phone),
                LiveQueueEntrySource.WEB
        );
    }

    private long entryId(String publicReference) {
        return entryRepository.findByPublicReference(publicReference).orElseThrow().getId();
    }

    private UserEntity saveUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Live");
        user.setLastName("Owner");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private long createPublishedLiveRoom(UserEntity owner) {
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Canlı workspace", "Asia/Baku")
        );
        RoomResponseDto room = roomService.createIndividualRoom(
                workspace.id(),
                owner.getId(),
                new RoomUpsertRequestDto(
                        "Canlı qəbul",
                        null,
                        null,
                        null,
                        "Asia/Baku",
                        ReservationMode.LIVE_QUEUE,
                        15,
                        RoomVisibility.UNLISTED,
                        null,
                        null,
                        null
                )
        );
        scheduleService.replaceWeeklyRules(
                room.id(),
                owner.getId(),
                new WeeklyAvailabilityReplaceRequestDto(List.of(
                        new WeeklyAvailabilityRuleRequestDto(
                                DayOfWeek.MONDAY,
                                LocalTime.of(0, 0),
                                LocalTime.of(23, 59),
                                true
                        )
                ))
        );
        configurationService.update(
                room.id(),
                owner.getId(),
                new RoomConfigurationUpdateRequestDto(
                        15,
                        0,
                        30,
                        30,
                        0,
                        LiveQueueResetPolicy.EVERY_INTERVAL,
                        null,
                        1440,
                        100,
                        true
                )
        );
        return configurationService.publish(room.id(), owner.getId()).id();
    }
}
