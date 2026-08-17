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
    private IndividualWorkspaceService individualWorkspaceService;
    @Autowired
    private WorkspaceQueryService workspaceQueryService;
    @Autowired
    private UserAccountService userAccountService;

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
        BusinessMembershipDto membership = membershipService.invite(
                business.id(),
                owner.getId(),
                new BusinessMemberInviteRequestDto(employee.getNormalizedPhone(), null, null, BusinessRole.EMPLOYEE)
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
        return new BusinessUpsertRequestDto(name, null, null, null, null, phone, "Asia/Baku");
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
