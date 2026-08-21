package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PlannedBookingIntegrationTests {
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
    private PlannedBookingAvailabilityService availabilityService;
    @Autowired
    private CustomerBookingService customerBookingService;
    @Autowired
    private RoomBookingService roomBookingService;
    @Autowired
    private BookingAuditEventRepository auditRepository;
    @Autowired
    private UserAccountService userAccountService;
    @Test
    void createsCustomerBookingPreventsSecondActiveBookingAndReleasesCancelledSlot() {
        UserEntity owner = saveUser("+994507400001");
        UserEntity customer = saveUser("+994507400002");
        BookingFixture fixture = createPublishedRoom(owner, 10);
        List<AvailableSlotDto> initial = availabilityService.getPublicSlots(fixture.roomId(), fixture.date());

        PlannedBookingDto booking = customerBookingService.create(
                customer.getId(),
                new BookingCreateRequestDto(fixture.roomId(), initial.get(0).startAt(), "Pəncərə tərəfi")
        );
        ResponseStatusException duplicate = assertThrows(
                ResponseStatusException.class,
                () -> customerBookingService.create(
                        customer.getId(),
                        new BookingCreateRequestDto(fixture.roomId(), initial.get(1).startAt(), null)
                )
        );
        List<AvailableSlotDto> occupied = availabilityService.getPublicSlots(fixture.roomId(), fixture.date());
        PlannedBookingDto cancelled = customerBookingService.cancel(booking.id(), customer.getId());
        List<AvailableSlotDto> released = availabilityService.getPublicSlots(fixture.roomId(), fixture.date());

        assertThat(initial).hasSize(4);
        assertThat(booking.status()).isEqualTo(PlannedBookingStatus.ACTIVE);
        assertThat(booking.customerNote()).isEqualTo("Pəncərə tərəfi");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(occupied).extracting(AvailableSlotDto::startAt).doesNotContain(initial.get(0).startAt());
        assertThat(cancelled.status()).isEqualTo(PlannedBookingStatus.CANCELLED);
        assertThat(released).extracting(AvailableSlotDto::startAt).contains(initial.get(0).startAt());
        assertThat(auditRepository.findByBookingIdOrderByCreatedAtAsc(booking.id()))
                .extracting(BookingAuditEventEntity::getAction)
                .containsExactly(BookingAuditAction.CREATED, BookingAuditAction.CANCELLED);
    }

    @Test
    void managesManualGuestBookingAndLinksItToLaterRegistrationHistory() {
        UserEntity owner = saveUser("+994507400003");
        BookingFixture fixture = createPublishedRoom(owner, 0);
        List<AvailableSlotDto> slots = availabilityService.getPublicSlots(fixture.roomId(), fixture.date());
        PlannedBookingDto manual = roomBookingService.createManual(
                fixture.roomId(),
                owner.getId(),
                new BookingManualCreateRequestDto(
                        "Qonaq Müştəri",
                        "0507400004",
                        slots.get(0).startAt(),
                        LiveQueueEntrySource.OWNER_PHONE,
                        "Telefonla yazıldı"
                )
        );
        ResponseStatusException missingConfirmation = assertThrows(
                ResponseStatusException.class,
                () -> roomBookingService.reschedule(
                        fixture.roomId(),
                        manual.id(),
                        owner.getId(),
                        new BookingOperatorRescheduleRequestDto(slots.get(1).startAt(), false)
                )
        );
        PlannedBookingDto moved = roomBookingService.reschedule(
                fixture.roomId(),
                manual.id(),
                owner.getId(),
                new BookingOperatorRescheduleRequestDto(slots.get(1).startAt(), true)
        );
        UserEntity registered = userAccountService.register(new UserRegistrationRequestDto(
                "Qonaq",
                "Müştəri",
                "0507400004",
                "Booking-safe-2026"
        ));

        assertThat(missingConfirmation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(moved.startAt()).isEqualTo(slots.get(1).startAt());
        assertThat(moved.participantPhone()).isEqualTo("+994507400004");
        assertThat(roomBookingService.list(fixture.roomId(), owner.getId(), fixture.date()))
                .extracting(PlannedBookingDto::internalNote)
                .containsExactly("Telefonla yazıldı");
        assertThat(customerBookingService.history(registered.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(manual.id());
                    assertThat(item.internalNote()).isNull();
                });
        assertThat(auditRepository.findByBookingIdOrderByCreatedAtAsc(manual.id()))
                .extracting(BookingAuditEventEntity::getAction)
                .containsExactly(BookingAuditAction.CREATED, BookingAuditAction.RESCHEDULED);
    }

    @Test
    void serializesConcurrentRequestsForTheSameRoomAndSlot() throws Exception {
        UserEntity owner = saveUser("+994507400005");
        UserEntity firstCustomer = saveUser("+994507400006");
        UserEntity secondCustomer = saveUser("+994507400007");
        BookingFixture fixture = createPublishedRoom(owner, 0);
        LocalDateTime startAt = availabilityService.getPublicSlots(fixture.roomId(), fixture.date()).get(0).startAt();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> tryCreate(firstCustomer.getId(), fixture.roomId(), startAt),
                    () -> tryCreate(secondCustomer.getId(), fixture.roomId(), startAt)
            );
            List<Future<Boolean>> results = executor.invokeAll(tasks);

            assertThat(results).extracting(this::result).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void enforcesCustomerCutoffWhileAllowingInformedOperatorCancellation() {
        UserEntity owner = saveUser("+994507400008");
        UserEntity customer = saveUser("+994507400009");
        BookingFixture fixture = createPublishedRoom(owner, 0, 2880);
        LocalDateTime startAt = availabilityService.getPublicSlots(fixture.roomId(), fixture.date()).get(0).startAt();
        PlannedBookingDto booking = customerBookingService.create(
                customer.getId(),
                new BookingCreateRequestDto(fixture.roomId(), startAt, null)
        );

        ResponseStatusException cutoff = assertThrows(
                ResponseStatusException.class,
                () -> customerBookingService.cancel(booking.id(), customer.getId())
        );
        PlannedBookingDto cancelled = roomBookingService.cancel(
                fixture.roomId(),
                booking.id(),
                owner.getId(),
                new BookingOperatorCancelRequestDto("Müştəri ilə razılaşdırıldı", true)
        );

        assertThat(cutoff.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancelled.status()).isEqualTo(PlannedBookingStatus.CANCELLED);
    }

    private boolean tryCreate(long userId, long roomId, LocalDateTime startAt) {
        try {
            customerBookingService.create(userId, new BookingCreateRequestDto(roomId, startAt, null));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean result(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private UserEntity saveUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Booking");
        user.setLastName("User");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private BookingFixture createPublishedRoom(UserEntity owner, int bufferMinutes) {
        return createPublishedRoom(owner, bufferMinutes, 0);
    }

    private BookingFixture createPublishedRoom(UserEntity owner, int bufferMinutes, int cutoffMinutes) {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Baku")).plusDays(1);
        IndividualWorkspaceResponseDto workspace = workspaceService.create(
                owner.getId(),
                new IndividualWorkspaceCreateRequestDto("Booking workspace", "Asia/Baku")
        );
        RoomResponseDto room = roomService.createIndividualRoom(
                workspace.id(),
                owner.getId(),
                new RoomUpsertRequestDto(
                        "Planlı qəbul",
                        null,
                        null,
                        null,
                        "Asia/Baku",
                        ReservationMode.PLANNED_BOOKING,
                        30,
                        RoomVisibility.UNLISTED,
                        null,
                        null,
                        null
                )
        );
        scheduleService.replaceWeeklyRules(
                room.id(),
                owner.getId(),
                new WeeklyAvailabilityReplaceRequestDto(List.of(new WeeklyAvailabilityRuleRequestDto(
                        date.getDayOfWeek(),
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0),
                        true
                )))
        );
        configurationService.update(
                room.id(),
                owner.getId(),
                new RoomConfigurationUpdateRequestDto(
                        30,
                        bufferMinutes,
                        30,
                        0,
                        cutoffMinutes,
                        null,
                        null,
                        null,
                        null,
                        true
                )
        );
        configurationService.publish(room.id(), owner.getId());
        return new BookingFixture(room.id(), date);
    }
}
