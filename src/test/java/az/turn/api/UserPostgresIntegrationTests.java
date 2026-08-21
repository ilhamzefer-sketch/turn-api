package az.turn.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UserPostgresIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private IndividualWorkspaceRepository individualWorkspaceRepository;

    @Autowired
    private WeeklyAvailabilityRuleRepository weeklyAvailabilityRuleRepository;

    @Autowired
    private PlannedBookingRepository plannedBookingRepository;

    @Autowired
    private Flyway flyway;

    @Test
    void appliesUnifiedAccountMigrationAndEnforcesUniquePhone() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
        userRepository.saveAndFlush(activeUser("+994505556677"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(activeUser("+994505556677"))
        );
    }

    @Test
    void enforcesExactlyOneRoomOwnerScopeInPostgres() {
        UserEntity user = userRepository.saveAndFlush(activeUser("+994505556688"));
        RoomEntity room = new RoomEntity();
        room.setCreatedByUser(user);
        room.setName("Invalid scope room");
        room.setTimezone("Asia/Baku");
        room.setReservationMode(ReservationMode.LIVE_QUEUE);
        room.setDefaultSlotDurationMinutes(15);
        room.setStatus(RoomStatus.DRAFT);
        room.setVisibility(RoomVisibility.UNLISTED);

        assertThrows(DataIntegrityViolationException.class, () -> roomRepository.saveAndFlush(room));
    }

    @Test
    void allowsAReplacementAfterTheIndividualRoomIsArchived() {
        UserEntity owner = userRepository.saveAndFlush(activeUser("+994505556689"));
        IndividualWorkspaceEntity workspace = new IndividualWorkspaceEntity();
        workspace.setOwnerUser(owner);
        workspace.setName("Replacement workspace");
        workspace.setTimezone("Asia/Baku");
        workspace.setStatus(ProviderStatus.ACTIVE);
        workspace = individualWorkspaceRepository.saveAndFlush(workspace);

        RoomEntity archivedRoom = individualRoom(owner, workspace, "Archived room");
        archivedRoom.setStatus(RoomStatus.ARCHIVED);
        archivedRoom.setArchivedAt(LocalDateTime.of(2026, 8, 20, 11, 0));
        roomRepository.saveAndFlush(archivedRoom);

        RoomEntity replacement = roomRepository.saveAndFlush(individualRoom(owner, workspace, "Replacement room"));

        assertThat(replacement.getId()).isNotEqualTo(archivedRoom.getId());
    }

    @Test
    void enforcesWeeklyAvailabilityIntervalInPostgres() {
        UserEntity user = userRepository.saveAndFlush(activeUser("+994505556699"));
        IndividualWorkspaceEntity workspace = new IndividualWorkspaceEntity();
        workspace.setOwnerUser(user);
        workspace.setName("Postgres workspace");
        workspace.setTimezone("Asia/Baku");
        workspace.setStatus(ProviderStatus.ACTIVE);
        workspace = individualWorkspaceRepository.saveAndFlush(workspace);

        RoomEntity room = new RoomEntity();
        room.setIndividualWorkspace(workspace);
        room.setCreatedByUser(user);
        room.setName("Postgres room");
        room.setTimezone("Asia/Baku");
        room.setReservationMode(ReservationMode.PLANNED_BOOKING);
        room.setDefaultSlotDurationMinutes(30);
        room.setStatus(RoomStatus.DRAFT);
        room.setVisibility(RoomVisibility.UNLISTED);
        room = roomRepository.saveAndFlush(room);

        WeeklyAvailabilityRuleEntity rule = new WeeklyAvailabilityRuleEntity();
        rule.setRoom(room);
        rule.setDayOfWeek(DayOfWeek.MONDAY);
        rule.setStartTime(LocalTime.of(18, 0));
        rule.setEndTime(LocalTime.of(9, 0));
        rule.setActive(true);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> weeklyAvailabilityRuleRepository.saveAndFlush(rule)
        );
    }

    @Test
    void preventsTwoActiveBookingsForTheSameRoomStartInPostgres() {
        UserEntity owner = userRepository.saveAndFlush(activeUser("+994505556700"));
        UserEntity firstCustomer = userRepository.saveAndFlush(activeUser("+994505556701"));
        UserEntity secondCustomer = userRepository.saveAndFlush(activeUser("+994505556702"));
        RoomEntity room = savePlannedRoom(owner);
        LocalDateTime startAt = LocalDateTime.of(2026, 9, 10, 10, 0);
        plannedBookingRepository.saveAndFlush(booking(room, firstCustomer, "B-PG-ONE", startAt));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> plannedBookingRepository.saveAndFlush(booking(room, secondCustomer, "B-PG-TWO", startAt))
        );
    }

    @Test
    void upgradesPopulatedVersionTwelveSchemaWithoutDeletingLegacyRows() throws Exception {
        Flyway legacyFlyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test")
                .defaultSchema("upgrade_test")
                .target(MigrationVersion.fromVersion("12"))
                .load();
        legacyFlyway.migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to upgrade_test");
            statement.executeUpdate("insert into registrations "
                    + "(first_name, last_name, email, password_hash, paid, payment_reference, registration_type, created_at, status) "
                    + "values ('Legacy', 'Owner', 'legacy@example.com', 'hash', true, 'REF', 'FERDI', current_timestamp, 'ACTIVE')");
            statement.executeUpdate("insert into queues "
                    + "(registration_id, address, service_name, qr_token, current_queue_number) "
                    + "values (1, 'Baku', 'Legacy queue', 'legacy-qr', 0)");
            statement.executeUpdate("insert into guest_queue_entries (queue_id, queue_number, first_name, last_name) "
                    + "values (1, 1, 'Legacy', 'Guest')");
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("upgrade_test")
                .defaultSchema("upgrade_test")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("set search_path to upgrade_test");
            try (ResultSet result = statement.executeQuery(
                    "select count(*) from guest_queue_entries where normalized_phone is null"
            )) {
                result.next();
                assertThat(result.getLong(1)).isEqualTo(1);
            }
        }
    }

    private UserEntity activeUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Postgres");
        user.setLastName("Test");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("{bcrypt-sha256}$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private RoomEntity savePlannedRoom(UserEntity owner) {
        IndividualWorkspaceEntity workspace = new IndividualWorkspaceEntity();
        workspace.setOwnerUser(owner);
        workspace.setName("Booking postgres workspace");
        workspace.setTimezone("Asia/Baku");
        workspace.setStatus(ProviderStatus.ACTIVE);
        workspace = individualWorkspaceRepository.saveAndFlush(workspace);

        RoomEntity room = new RoomEntity();
        room.setIndividualWorkspace(workspace);
        room.setCreatedByUser(owner);
        room.setName("Booking postgres room");
        room.setTimezone("Asia/Baku");
        room.setReservationMode(ReservationMode.PLANNED_BOOKING);
        room.setDefaultSlotDurationMinutes(30);
        room.setStatus(RoomStatus.DRAFT);
        room.setVisibility(RoomVisibility.UNLISTED);
        return roomRepository.saveAndFlush(room);
    }

    private RoomEntity individualRoom(
            UserEntity owner,
            IndividualWorkspaceEntity workspace,
            String name
    ) {
        RoomEntity room = new RoomEntity();
        room.setIndividualWorkspace(workspace);
        room.setCreatedByUser(owner);
        room.setName(name);
        room.setTimezone("Asia/Baku");
        room.setReservationMode(ReservationMode.PLANNED_BOOKING);
        room.setDefaultSlotDurationMinutes(30);
        room.setStatus(RoomStatus.DRAFT);
        room.setVisibility(RoomVisibility.UNLISTED);
        return room;
    }

    private PlannedBookingEntity booking(
            RoomEntity room,
            UserEntity customer,
            String reference,
            LocalDateTime startAt
    ) {
        PlannedBookingEntity booking = new PlannedBookingEntity();
        booking.setRoom(room);
        booking.setUser(customer);
        booking.setBookingReference(reference);
        booking.setStatus(PlannedBookingStatus.ACTIVE);
        booking.setSource(LiveQueueEntrySource.WEB);
        booking.setStartAt(startAt);
        booking.setEndAt(startAt.plusMinutes(30));
        booking.setBlockingEndAt(startAt.plusMinutes(30));
        booking.setActiveSlot(1);
        booking.setActiveCustomerSlot(1);
        booking.setCreatedByUser(customer);
        return booking;
    }
}
