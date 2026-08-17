package az.turn.api;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.subscription.enforcement-enabled=true",
        "app.payment.provider=mock",
        "app.payment.mode=sandbox"
})
@Transactional
class StepSixIntegrationTests {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IndividualWorkspaceRepository workspaceRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private PlannedBookingRepository plannedBookingRepository;

    @Autowired
    private SubscriptionPaymentService subscriptionPaymentService;

    @Autowired
    private SubscriptionGateService subscriptionGateService;

    @Autowired
    private SupportRequestService supportRequestService;

    @Autowired
    private OperationalReportExcelService excelService;

    @Autowired
    private ServiceRatingService ratingService;

    @Test
    void activatesWorkspaceSubscriptionAfterSuccessfulSandboxPayment() {
        UserEntity owner = userRepository.save(activeUser("+994501116601"));
        IndividualWorkspaceEntity workspace = workspaceRepository.save(workspace(owner));
        RoomEntity room = roomRepository.save(room(owner, workspace));

        assertThatThrownBy(() -> subscriptionGateService.requireRoomOperations(room))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        SubscriptionPaymentSessionDto checkout = subscriptionPaymentService.checkout(
                owner.getId(),
                new SubscriptionCheckoutRequestDto(
                        ProviderScopeType.INDIVIDUAL_WORKSPACE,
                        workspace.getId(),
                        "STANDARD_MONTHLY",
                        "Step Six Owner",
                        "4169741330151778"
                )
        );
        SubscriptionPaymentSessionDto confirmed = subscriptionPaymentService.confirm(
                checkout.id(),
                checkout.sessionToken(),
                owner.getId()
        );

        assertThat(confirmed.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(confirmed.subscription().status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(confirmed.subscription().expiresAt()).isAfter(confirmed.subscription().startsAt());
        assertThatCode(() -> subscriptionGateService.requireRoomOperations(room)).doesNotThrowAnyException();
    }

    @Test
    void resolvesOwnershipDisputeWithPasswordReset() {
        UserEntity user = userRepository.save(activeUser("+994501116602"));
        OwnershipDisputeDto dispute = supportRequestService.createDispute(
                new OwnershipDisputeCreateRequestDto(
                        user.getNormalizedPhone(),
                        "Account Owner",
                        "+994501116603",
                        "This phone account belongs to the claimant."
                )
        );

        OwnershipDisputeDto resolved = supportRequestService.resolveDispute(
                dispute.id(),
                "admin",
                new OwnershipDisputeResolveRequestDto(
                        DisputeResolutionAction.RESET_PASSWORD,
                        "Identity was manually verified.",
                        false
                )
        );

        UserEntity updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(resolved.status()).isEqualTo(SupportRequestStatus.RESOLVED);
        assertThat(updated.getStatus()).isEqualTo(UserStatus.PASSWORD_RESET_REQUIRED);
        assertThat(updated.getPasswordHash()).isNull();
    }

    @Test
    void createsReadableOperationalExcelWorkbook() throws Exception {
        OperationalAnalyticsDto report = new OperationalAnalyticsDto(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                7, 4, 3, 5, 1, 1, 0, 0, 2, 5, 12, 25,
                "MONDAY", 10, List.of()
        );

        byte[] content = excelService.create(report);

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheet("Summary").getRow(3).getCell(1).getStringCellValue()).isEqualTo("7");
        }
    }

    @Test
    void letsTheLinkedCustomerRateAndEditACompletedService() {
        UserEntity owner = userRepository.save(activeUser("+994501116604"));
        UserEntity customer = userRepository.save(activeUser("+994501116605"));
        IndividualWorkspaceEntity workspace = workspaceRepository.save(workspace(owner));
        RoomEntity room = roomRepository.save(room(owner, workspace));
        room.setStatus(RoomStatus.PUBLISHED);
        room = roomRepository.save(room);
        PlannedBookingEntity booking = completedBooking(room, customer);
        booking = plannedBookingRepository.save(booking);

        ServiceRatingDto created = ratingService.upsertBooking(
                customer.getId(), booking.getId(), new RatingUpsertRequestDto(4, "Good service")
        );
        ServiceRatingDto updated = ratingService.upsertBooking(
                customer.getId(), booking.getId(), new RatingUpsertRequestDto(5, "Excellent service")
        );
        RoomRatingSummaryDto summary = ratingService.summary(room.getId());

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.score()).isEqualTo(5);
        assertThat(summary.ratingCount()).isEqualTo(1);
        assertThat(summary.averageScore()).isEqualTo(5.0);
    }

    private UserEntity activeUser(String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName("Step");
        user.setLastName("Six");
        user.setNormalizedPhone(phone);
        user.setPasswordHash("password-hash");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

    private IndividualWorkspaceEntity workspace(UserEntity owner) {
        IndividualWorkspaceEntity workspace = new IndividualWorkspaceEntity();
        workspace.setOwnerUser(owner);
        workspace.setName("Step Six Workspace");
        workspace.setTimezone("Asia/Baku");
        workspace.setStatus(ProviderStatus.ACTIVE);
        return workspace;
    }

    private RoomEntity room(UserEntity owner, IndividualWorkspaceEntity workspace) {
        RoomEntity room = new RoomEntity();
        room.setIndividualWorkspace(workspace);
        room.setCreatedByUser(owner);
        room.setName("Step Six Room");
        room.setTimezone("Asia/Baku");
        room.setReservationMode(ReservationMode.LIVE_QUEUE);
        room.setDefaultSlotDurationMinutes(15);
        room.setStatus(RoomStatus.DRAFT);
        room.setVisibility(RoomVisibility.UNLISTED);
        return room;
    }

    private PlannedBookingEntity completedBooking(RoomEntity room, UserEntity customer) {
        PlannedBookingEntity booking = new PlannedBookingEntity();
        booking.setRoom(room);
        booking.setUser(customer);
        booking.setBookingReference("B-STEP-SIX-RATING");
        booking.setStatus(PlannedBookingStatus.COMPLETED);
        booking.setSource(LiveQueueEntrySource.WEB);
        booking.setStartAt(LocalDateTime.of(2026, 8, 17, 10, 0));
        booking.setEndAt(LocalDateTime.of(2026, 8, 17, 10, 15));
        booking.setBlockingEndAt(LocalDateTime.of(2026, 8, 17, 10, 15));
        booking.setCompletedAt(LocalDateTime.of(2026, 8, 17, 10, 15));
        return booking;
    }
}
