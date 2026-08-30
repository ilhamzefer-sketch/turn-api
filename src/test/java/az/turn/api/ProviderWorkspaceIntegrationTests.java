package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ProviderWorkspaceIntegrationTests {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessService businessService;
    @Autowired
    private BusinessMembershipService membershipService;
    @Autowired
    private BranchService branchService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private RoomAssignmentService assignmentService;
    @Autowired
    private InvitationAcceptanceService invitationAcceptanceService;
    @Autowired
    private IndividualWorkspaceService individualWorkspaceService;
    @Autowired
    private WorkspaceQueryService workspaceQueryService;
    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private BusinessCategoryRepository categoryRepository;
    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @Test
    void businessOwnerBuildsBranchAndRoomThenEmployeeAcceptsBothInvitations() {
        UserEntity owner = saveActiveUser("+994507000001", "Sahib", "İstifadəçi");
        UserEntity employee = saveActiveUser("+994507000002", "Leyla", "İşçi");
        BusinessResponseDto business = businessService.create(owner.getId(), businessRequest("Salon", "0507000001"));
        BranchResponseDto branch = branchService.create(
                business.id(),
                owner.getId(),
                branchRequest("Mərkəz filialı", null)
        );
        RoomResponseDto room = roomService.createBusinessRoom(
                branch.id(),
                owner.getId(),
                roomRequest("Leylanın otağı", ReservationMode.PLANNED_BOOKING)
        );
        assertThat(assignmentService.list(room.id(), owner.getId()))
                .singleElement()
                .satisfies(assignment -> {
                    assertThat(assignment.userId()).isEqualTo(owner.getId());
                    assertThat(assignment.status()).isEqualTo(RoomAssignmentStatus.ACTIVE);
                });
        BusinessMembershipDto membership = membershipService.invite(
                business.id(),
                owner.getId(),
                new BusinessMemberInviteRequestDto("0507000002", null, null, BusinessRole.EMPLOYEE)
        );
        RoomAssignmentDto assignment = assignmentService.invite(
                room.id(),
                owner.getId(),
                new RoomAssignmentInviteRequestDto(employee.getId())
        );

        ResponseStatusException prematureAcceptance = assertThrows(
                ResponseStatusException.class,
                () -> assignmentService.accept(assignment.id(), employee.getId())
        );
        assertThat(prematureAcceptance.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        membershipService.accept(membership.id(), employee.getId());
        RoomAssignmentDto accepted = assignmentService.accept(assignment.id(), employee.getId());
        RoomResponseDto updated = roomService.update(
                room.id(),
                employee.getId(),
                roomRequest("Leylanın yeni otaq adı", ReservationMode.PLANNED_BOOKING)
        );

        assertThat(branch.effectivePhone()).isEqualTo("+994507000001");
        assertThat(room.status()).isEqualTo(RoomStatus.DRAFT);
        assertThat(accepted.status()).isEqualTo(RoomAssignmentStatus.ACTIVE);
        assertThat(updated.name()).isEqualTo("Leylanın yeni otaq adı");
        assertThat(workspaceQueryService.getContexts(employee.getId()))
                .extracting(WorkspaceContextDto::type)
                .containsExactly(WorkspaceContextType.CUSTOMER, WorkspaceContextType.ROOM);
    }

    @Test
    void pendingEmployeeCompletesSameAccountWithoutAutomaticallyAcceptingBusiness() {
        UserEntity owner = saveActiveUser("+994507000003", "Biznes", "Sahibi");
        BusinessResponseDto business = businessService.create(owner.getId(), businessRequest("Klinika", "0507000003"));
        BusinessMembershipDto invitation = membershipService.invite(
                business.id(),
                owner.getId(),
                new BusinessMemberInviteRequestDto("0507000004", "Dəvət", "Adı", BusinessRole.ADMIN)
        );
        UserEntity pending = userRepository.findByNormalizedPhone("+994507000004").orElseThrow();
        assertThat(walletAccountRepository.findByUserId(pending.getId())).isPresent();

        UserEntity completed = userAccountService.register(new UserRegistrationRequestDto(
                "Nigar",
                "Həsənova",
                "0507000004",
                "Strong-pending-2026"
        ));

        assertThat(completed.getId()).isEqualTo(pending.getId());
        assertThat(completed.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(workspaceQueryService.getInvitations(completed.getId()).businessInvitations())
                .extracting(BusinessMembershipDto::id)
                .containsExactly(invitation.id());
        assertThat(workspaceQueryService.getContexts(completed.getId()))
                .extracting(WorkspaceContextDto::type)
                .containsExactly(WorkspaceContextType.CUSTOMER);

        membershipService.accept(invitation.id(), completed.getId());
        assertThat(workspaceQueryService.getContexts(completed.getId()))
                .extracting(WorkspaceContextDto::type)
                .containsExactly(WorkspaceContextType.CUSTOMER, WorkspaceContextType.BUSINESS);
    }

    @Test
    void individualUserCanCreateOnlyOneWorkspaceAndOneSelfOwnedRoom() {
        UserEntity user = saveActiveUser("+994507000005", "Fərdi", "Mütəxəssis");
        IndividualWorkspaceResponseDto workspace = individualWorkspaceService.create(
                user.getId(),
                new IndividualWorkspaceCreateRequestDto("Şəxsi təqvim", null)
        );
        RoomResponseDto room = roomService.createIndividualRoom(
                workspace.id(),
                user.getId(),
                roomRequest("Fərdi qəbul", ReservationMode.LIVE_QUEUE)
        );

        assertThat(room.individualWorkspaceId()).isEqualTo(workspace.id());
        assertThat(assignmentService.list(room.id(), user.getId()))
                .singleElement()
                .satisfies(assignment -> assertThat(assignment.status()).isEqualTo(RoomAssignmentStatus.ACTIVE));
        assertThrows(
                ResponseStatusException.class,
                () -> individualWorkspaceService.create(
                        user.getId(),
                        new IndividualWorkspaceCreateRequestDto("İkinci", null)
                )
        );
        assertThrows(
                ResponseStatusException.class,
                () -> roomService.createIndividualRoom(
                        workspace.id(),
                        user.getId(),
                        roomRequest("İkinci otaq", ReservationMode.LIVE_QUEUE)
                )
        );
    }

    @Test
    void anotherUserCannotManageBusinessAndBranchWithRoomsCannotBeArchived() {
        UserEntity owner = saveActiveUser("+994507000006", "Owner", "One");
        UserEntity stranger = saveActiveUser("+994507000007", "Other", "User");
        BusinessResponseDto business = businessService.create(owner.getId(), businessRequest("Servis", "0507000006"));
        BranchResponseDto branch = branchService.create(
                business.id(),
                owner.getId(),
                branchRequest("Servis filialı", "0507000008")
        );
        RoomResponseDto room = roomService.createBusinessRoom(
                branch.id(),
                owner.getId(),
                roomRequest("Usta otağı", ReservationMode.LIVE_QUEUE)
        );

        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> branchService.list(business.id(), stranger.getId())
        );
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThrows(ResponseStatusException.class, () -> branchService.archive(branch.id(), owner.getId()));

        roomService.archive(room.id(), owner.getId());
        branchService.archive(branch.id(), owner.getId());
        assertThat(branchService.list(business.id(), owner.getId()))
                .extracting(BranchResponseDto::status)
                .containsExactly(ProviderStatus.ARCHIVED);
    }

    @Test
    void businessUsesPlatformCategoryAndCustomSubcategoryOnlyForOther() {
        UserEntity owner = saveActiveUser("+994507000008", "Kateqoriya", "Sahibi");
        BusinessCategoryEntity health = categoryRepository.findByCodeAndActiveTrue("HEALTH_MEDICAL").orElseThrow();
        BusinessResponseDto business = businessService.create(
                owner.getId(),
                new BusinessUpsertRequestDto(
                        "Sağlamlıq mərkəzi", null, null, null, null, "0507000008", "Asia/Baku",
                        health.getId(), null
                )
        );

        assertThat(business.category().code()).isEqualTo("HEALTH_MEDICAL");
        ResponseStatusException invalidCustom = assertThrows(
                ResponseStatusException.class,
                () -> businessService.update(
                        business.id(),
                        owner.getId(),
                        new BusinessUpsertRequestDto(
                                "Sağlamlıq mərkəzi", null, null, null, null, "0507000008", "Asia/Baku",
                                health.getId(), "Xüsusi sahə"
                        )
                )
        );
        assertThat(invalidCustom.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(categoryRepository.findAll())
                .extracting(BusinessCategoryEntity::getCode)
                .contains(
                        "FINANCE_BANKING",
                        "LEGAL_NOTARY",
                        "FITNESS_WELLNESS",
                        "VETERINARY_PET",
                        "AUTOMOTIVE",
                        "REAL_ESTATE",
                        "EVENTS_PHOTOGRAPHY",
                        "CUSTOMER_SERVICE",
                        "HOSPITALITY_FOOD"
                );
    }

    @Test
    void acceptingRoomInvitationAlsoAcceptsPendingBusinessMembership() {
        UserEntity owner = saveActiveUser("+994507000009", "Biznes", "Sahibi");
        UserEntity employee = saveActiveUser("+994507000010", "Otaq", "Sahibi");
        BusinessResponseDto business = businessService.create(owner.getId(), businessRequest("Studiya", "0507000009"));
        BranchResponseDto branch = branchService.create(business.id(), owner.getId(), branchRequest("Əsas filial", null));
        RoomResponseDto room = roomService.createBusinessRoom(
                branch.id(),
                owner.getId(),
                roomRequest("Foto otağı", ReservationMode.PLANNED_BOOKING)
        );
        membershipService.invite(
                business.id(),
                owner.getId(),
                new BusinessMemberInviteRequestDto("0507000010", null, null, BusinessRole.EMPLOYEE)
        );
        RoomAssignmentDto invitation = assignmentService.invite(
                room.id(),
                owner.getId(),
                new RoomAssignmentInviteRequestDto(employee.getId())
        );

        RoomAssignmentDto accepted = invitationAcceptanceService.acceptRoom(invitation.id(), employee.getId());

        assertThat(accepted.status()).isEqualTo(RoomAssignmentStatus.ACTIVE);
        assertThat(workspaceQueryService.getContexts(employee.getId()))
                .extracting(WorkspaceContextDto::type)
                .containsExactly(WorkspaceContextType.CUSTOMER, WorkspaceContextType.ROOM);
        assertThat(workspaceQueryService.getInvitations(employee.getId()).businessInvitations()).isEmpty();
        assertThat(workspaceQueryService.getInvitations(employee.getId()).roomInvitations()).isEmpty();
    }

    @Test
    void archivedIndividualRoomCanBeReplacedWithoutReactivatingHistory() {
        UserEntity user = saveActiveUser("+994507000011", "Fərdi", "Sahib");
        IndividualWorkspaceResponseDto workspace = individualWorkspaceService.create(
                user.getId(),
                new IndividualWorkspaceCreateRequestDto("Şəxsi təqvim", "Asia/Baku")
        );
        RoomResponseDto archived = roomService.createIndividualRoom(
                workspace.id(),
                user.getId(),
                roomRequest("Köhnə otaq", ReservationMode.LIVE_QUEUE)
        );
        roomService.archive(archived.id(), user.getId());

        RoomResponseDto replacement = roomService.createIndividualRoom(
                workspace.id(),
                user.getId(),
                roomRequest("Yeni otaq", ReservationMode.PLANNED_BOOKING)
        );

        assertThat(replacement.id()).isNotEqualTo(archived.id());
        assertThat(replacement.status()).isEqualTo(RoomStatus.DRAFT);
        assertThat(roomService.listIndividualRooms(workspace.id(), user.getId()))
                .extracting(RoomResponseDto::id)
                .containsExactly(replacement.id());
    }

    private UserEntity saveActiveUser(String phone, String firstName, String lastName) {
        UserEntity user = new UserEntity();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setNormalizedPhone(phone);
        user.setPasswordHash("test-hash");
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.saveAndFlush(user);
    }

    private BusinessUpsertRequestDto businessRequest(String name, String phone) {
        return new BusinessUpsertRequestDto(name, null, null, null, null, phone, "Asia/Baku", null, null);
    }

    private BranchUpsertRequestDto branchRequest(String name, String phone) {
        return new BranchUpsertRequestDto(
                name,
                "Nizami küçəsi 1",
                "Bakı",
                "Nəsimi",
                null,
                null,
                phone,
                null,
                "Asia/Baku"
        );
    }

    private RoomUpsertRequestDto roomRequest(String name, ReservationMode mode) {
        return new RoomUpsertRequestDto(
                name,
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
        );
    }
}
