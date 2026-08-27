package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class RoomSchedulingIntegrationTests {
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
    private RoomAvailabilityService availabilityService;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private LiveQueueSessionRepository liveQueueSessionRepository;

    @Test
    void configuresAndPublishesPlannedRoomWithScheduleExceptions() {
        UserEntity owner = saveUser("+994507200001");
        RoomResponseDto room = createRoom(owner, ReservationMode.PLANNED_BOOKING);
        ResponseStatusException missingSchedule = assertThrows(
                ResponseStatusException.class,
                () -> configurationService.publish(room.id(), owner.getId())
        );

        List<WeeklyAvailabilityRuleDto> weekly = scheduleService.replaceWeeklyRules(
                room.id(),
                owner.getId(),
                weeklyRequest(
                        rule(DayOfWeek.MONDAY, 9, 13),
                        rule(DayOfWeek.MONDAY, 14, 18)
                )
        );
        List<WeeklyAvailabilityRuleDto> copied = scheduleService.copyWeeklyRules(
                room.id(),
                owner.getId(),
                new WeeklyAvailabilityCopyRequestDto(DayOfWeek.MONDAY, Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY))
        );
        RoomResponseDto configured = configurationService.update(
                room.id(),
                owner.getId(),
                plannedConfiguration()
        );
        AvailabilityExceptionDto exception = scheduleService.createException(
                room.id(),
                owner.getId(),
                new AvailabilityExceptionUpsertRequestDto(
                        LocalDate.of(2026, 9, 1),
                        AvailabilityExceptionType.CUSTOM_HOURS,
                        LocalTime.of(10, 0),
                        LocalTime.of(15, 0),
                        "Xüsusi iş günü"
                )
        );
        RoomResponseDto published = configurationService.publish(room.id(), owner.getId());

        assertThat(weekly).hasSize(2);
        assertThat(missingSchedule.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(copied).hasSize(6);
        assertThat(configured.appointmentBufferMinutes()).isEqualTo(10);
        assertThat(exception.type()).isEqualTo(AvailabilityExceptionType.CUSTOM_HOURS);
        assertThat(published.status()).isEqualTo(RoomStatus.PUBLISHED);
    }

    @Test
    void rejectsOverlappingWeeklyIntervals() {
        UserEntity owner = saveUser("+994507200002");
        RoomResponseDto room = createRoom(owner, ReservationMode.PLANNED_BOOKING);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> scheduleService.replaceWeeklyRules(
                        room.id(),
                        owner.getId(),
                        weeklyRequest(
                                rule(DayOfWeek.MONDAY, 9, 13),
                                rule(DayOfWeek.MONDAY, 12, 15)
                        )
                )
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void liveRoomUsesSafeDefaultsAndCreatesSessionWhenPublished() {
        UserEntity owner = saveUser("+994507200003");
        RoomResponseDto room = createRoom(owner, ReservationMode.LIVE_QUEUE);
        scheduleService.replaceWeeklyRules(
                room.id(),
                owner.getId(),
                weeklyRequest(rule(DayOfWeek.MONDAY, 9, 18))
        );
        RoomResponseDto configured = configurationService.update(
                room.id(),
                owner.getId(),
                new RoomConfigurationUpdateRequestDto(
                        20,
                        0,
                        30,
                        30,
                        0,
                        null,
                        null,
                        null,
                        null,
                        true
                )
        );
        RoomResponseDto published = configurationService.publish(room.id(), owner.getId());

        assertThat(room.liveQueueResetPolicy()).isEqualTo(LiveQueueResetPolicy.DAILY_AT_TIME);
        assertThat(room.liveQueueResetLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(configured.liveQueueResetPolicy()).isEqualTo(LiveQueueResetPolicy.DAILY_AT_TIME);
        assertThat(configured.liveQueueResetLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(published.status()).isEqualTo(RoomStatus.PUBLISHED);
        assertThat(liveQueueSessionRepository.findByRoomIdAndOpenSlot(room.id(), 1)).isPresent();
    }

    @Test
    void closedDateCannotContainAnotherException() {
        UserEntity owner = saveUser("+994507200004");
        RoomResponseDto room = createRoom(owner, ReservationMode.PLANNED_BOOKING);
        LocalDate date = LocalDate.of(2026, 10, 10);
        scheduleService.createException(
                room.id(),
                owner.getId(),
                new AvailabilityExceptionUpsertRequestDto(date, AvailabilityExceptionType.CLOSED, null, null, "Tətil")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> scheduleService.createException(
                        room.id(),
                        owner.getId(),
                        new AvailabilityExceptionUpsertRequestDto(
                                date,
                                AvailabilityExceptionType.BLOCKED_INTERVAL,
                                LocalTime.of(12, 0),
                                LocalTime.of(13, 0),
                                null
                        )
                )
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anotherUserCannotReadOrModifyRoomSchedule() {
        UserEntity owner = saveUser("+994507200005");
        UserEntity stranger = saveUser("+994507200006");
        RoomResponseDto room = createRoom(owner, ReservationMode.PLANNED_BOOKING);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> scheduleService.getWeeklyRules(room.id(), stranger.getId())
        );

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void replacingWeekdaysWithWeekendRulesTakesEffectImmediately() {
        UserEntity owner = saveUser("+994507200007");
        RoomResponseDto created = createRoom(owner, ReservationMode.LIVE_QUEUE);
        scheduleService.replaceWeeklyRules(
                created.id(),
                owner.getId(),
                weeklyRequest(
                        rule(DayOfWeek.MONDAY, 9, 18),
                        rule(DayOfWeek.TUESDAY, 9, 18),
                        rule(DayOfWeek.WEDNESDAY, 9, 18),
                        rule(DayOfWeek.THURSDAY, 9, 18),
                        rule(DayOfWeek.FRIDAY, 9, 18)
                )
        );

        List<WeeklyAvailabilityRuleDto> weekend = scheduleService.replaceWeeklyRules(
                created.id(),
                owner.getId(),
                weeklyRequest(
                        rule(DayOfWeek.SATURDAY, 10, 16),
                        rule(DayOfWeek.SUNDAY, 10, 16)
                )
        );
        RoomEntity room = roomRepository.findById(created.id()).orElseThrow();

        assertThat(weekend)
                .extracting(WeeklyAvailabilityRuleDto::dayOfWeek)
                .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(availabilityService.intervals(room, LocalDate.of(2026, 8, 29))).hasSize(1);
        assertThat(availabilityService.intervals(room, LocalDate.of(2026, 8, 30))).hasSize(1);
        assertThat(availabilityService.intervals(room, LocalDate.of(2026, 8, 31))).isEmpty();
    }

    private UserEntity saveUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Schedule");
        user.setLastName("Owner");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private RoomResponseDto createRoom(UserEntity owner, ReservationMode mode) {
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Şəxsi workspace", "Asia/Baku")
        );
        return roomService.createIndividualRoom(
                workspace.id(),
                owner.getId(),
                new RoomUpsertRequestDto(
                        "Qəbul otağı",
                        null,
                        null,
                        null,
                        "Asia/Baku",
                        mode,
                        30,
                        RoomVisibility.UNLISTED,
                        null,
                        null,
                        null
                )
        );
    }

    private WeeklyAvailabilityReplaceRequestDto weeklyRequest(WeeklyAvailabilityRuleRequestDto... rules) {
        return new WeeklyAvailabilityReplaceRequestDto(List.of(rules));
    }

    private WeeklyAvailabilityRuleRequestDto rule(DayOfWeek day, int startHour, int endHour) {
        return new WeeklyAvailabilityRuleRequestDto(
                day,
                LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0),
                true
        );
    }

    private RoomConfigurationUpdateRequestDto plannedConfiguration() {
        return new RoomConfigurationUpdateRequestDto(
                45,
                10,
                60,
                30,
                120,
                null,
                null,
                null,
                null,
                true
        );
    }
}
