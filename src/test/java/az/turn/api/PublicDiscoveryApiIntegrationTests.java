package az.turn.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class PublicDiscoveryApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private BusinessCategoryRepository categoryRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomAssignmentRepository assignmentRepository;
    @Autowired
    private RoomServiceItemRepository serviceRepository;
    @Autowired
    private QrCredentialRepository qrCredentialRepository;
    @Autowired
    private SecureTokenService tokenService;
    @Autowired
    private ApiSessionService apiSessionService;

    private UserEntity owner;
    private BusinessCategoryEntity category;
    private RoomEntity publicRoom;
    private RoomEntity unlistedRoom;
    private RoomEntity privateRoom;
    private String qrToken;
    private String searchServiceName;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        owner = userRepository.save(user("Discovery", "Owner", "+99455" + suffix.substring(suffix.length() - 7)));
        category = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAzAsc().get(0);
        BusinessEntity business = businessRepository.save(business("Discovery Studio " + suffix, category, owner));
        BranchEntity branch = branchRepository.save(branch(business));
        publicRoom = roomRepository.save(room(branch, "Aysel kosmetoloq", RoomVisibility.PUBLIC, ReservationMode.PLANNED_BOOKING));
        unlistedRoom = roomRepository.save(room(branch, "Birbaşa otaq", RoomVisibility.UNLISTED, ReservationMode.LIVE_QUEUE));
        privateRoom = roomRepository.save(room(branch, "Gizli otaq", RoomVisibility.PRIVATE, ReservationMode.PLANNED_BOOKING));
        assignmentRepository.save(assignment(publicRoom, owner, false));
        assignmentRepository.save(assignment(unlistedRoom, owner, true));
        assignmentRepository.save(assignment(privateRoom, owner, true));
        searchServiceName = "Dəri baxımı " + suffix;
        serviceRepository.save(service(publicRoom, searchServiceName));
        qrToken = "qr_" + suffix;
        qrCredentialRepository.save(credential(unlistedRoom, owner, qrToken));
    }

    @Test
    void searchesOnlyPublicRoomsWithBoundedFrontendReadyMetadata() throws Exception {
        mockMvc.perform(get("/api/public/rooms")
                        .param("q", searchServiceName)
                        .param("categoryId", category.getId().toString())
                        .param("district", "Nəsimi")
                        .param("mode", "PLANNED_BOOKING")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publicRoom.getId()))
                .andExpect(jsonPath("$.items[0].providerName").exists())
                .andExpect(jsonPath("$.items[0].serviceNames[0]").value(searchServiceName))
                .andExpect(jsonPath("$.items[0].averageRating").value(0.0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(12));

        mockMvc.perform(get("/api/public/rooms").param("size", "25"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesUnlistedDirectProfileWithoutPrivateNotesOrHiddenPhone() throws Exception {
        publicRoom.setNotes("Daxili məxfi qeyd");
        roomRepository.save(publicRoom);

        mockMvc.perform(get("/api/public/rooms/{roomId}", publicRoom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aysel kosmetoloq"))
                .andExpect(jsonPath("$.contactPhone").value("+994501112233"))
                .andExpect(jsonPath("$.owners[0].displayName").value("Discovery Owner"))
                .andExpect(jsonPath("$.owners[0].phone").doesNotExist())
                .andExpect(jsonPath("$.services[0].name").value(searchServiceName))
                .andExpect(jsonPath("$.notes").doesNotExist());

        mockMvc.perform(get("/api/public/rooms/{roomId}", unlistedRoom.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/rooms/{roomId}", privateRoom.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolvesActiveQrToRoomModeAndRejectsPrivateOrRevokedCredentials() throws Exception {
        mockMvc.perform(get("/api/public/qr/{token}", qrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(unlistedRoom.getId()))
                .andExpect(jsonPath("$.reservationMode").value("LIVE_QUEUE"))
                .andExpect(jsonPath("$.publicPath").value("/rooms/" + unlistedRoom.getId()));

        String privateToken = "private_" + System.nanoTime();
        qrCredentialRepository.save(credential(privateRoom, owner, privateToken));
        mockMvc.perform(get("/api/public/qr/{token}", privateToken))
                .andExpect(status().isNotFound());

        QrCredentialEntity revoked = credential(unlistedRoom, owner, "revoked_" + System.nanoTime());
        revoked.setActive(false);
        revoked.setRevokedAt(LocalDateTime.now());
        String revokedRaw = revoked.getTokenHash();
        revoked.setTokenHash(tokenService.hash(revokedRaw));
        qrCredentialRepository.save(revoked);
        mockMvc.perform(get("/api/public/qr/{token}", revokedRaw))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsPlatformCategoriesAndRehydratesAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].code").isString())
                .andExpect(jsonPath("$[0].name").isString());

        String accessToken = apiSessionService.authenticateUser(
                owner,
                new MockHttpServletRequest(),
                new MockHttpServletResponse()
        ).accessToken();
        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId()))
                .andExpect(jsonPath("$.phone").value(owner.getNormalizedPhone()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    private UserEntity user(String firstName, String lastName, String phone) {
        UserEntity user = new UserEntity();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setNormalizedPhone(phone);
        user.setPasswordHash("not-used-in-test");
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(LocalDateTime.now());
        user.setPasswordChangedAt(LocalDateTime.now());
        return user;
    }

    private BusinessEntity business(String name, BusinessCategoryEntity value, UserEntity user) {
        BusinessEntity business = new BusinessEntity();
        business.setPrimaryOwnerUser(user);
        business.setName(name);
        business.setCategory(value);
        business.setNormalizedPhone("+994501112233");
        business.setTimezone("Asia/Baku");
        business.setStatus(ProviderStatus.ACTIVE);
        return business;
    }

    private BranchEntity branch(BusinessEntity business) {
        BranchEntity branch = new BranchEntity();
        branch.setBusiness(business);
        branch.setName("Mərkəz filialı");
        branch.setAddress("Nizami küçəsi 1");
        branch.setCity("Bakı");
        branch.setDistrict("Nəsimi");
        branch.setTimezone("Asia/Baku");
        branch.setStatus(ProviderStatus.ACTIVE);
        return branch;
    }

    private RoomEntity room(
            BranchEntity branch,
            String name,
            RoomVisibility visibility,
            ReservationMode mode
    ) {
        RoomEntity room = new RoomEntity();
        room.setBranch(branch);
        room.setCreatedByUser(owner);
        room.setName(name);
        room.setDescription("Public otaq təsviri");
        room.setNotes("Public cavabda görünməməlidir");
        room.setTimezone("Asia/Baku");
        room.setReservationMode(mode);
        room.setDefaultSlotDurationMinutes(30);
        room.setAppointmentBufferMinutes(0);
        room.setBookingWindowDays(30);
        room.setMinimumAdvanceMinutes(30);
        room.setCancellationCutoffMinutes(0);
        room.setLiveQueueAcceptingNewEntries(true);
        room.setStatus(RoomStatus.PUBLISHED);
        room.setVisibility(visibility);
        return room;
    }

    private RoomAssignmentEntity assignment(RoomEntity room, UserEntity user, boolean showPhone) {
        RoomAssignmentEntity assignment = new RoomAssignmentEntity();
        assignment.setRoom(room);
        assignment.setUser(user);
        assignment.setRole(RoomRole.ROOM_OWNER);
        assignment.setStatus(RoomAssignmentStatus.ACTIVE);
        assignment.setInvitedByUser(user);
        assignment.setShowPhonePublicly(showPhone);
        assignment.setRespondedAt(LocalDateTime.now());
        return assignment;
    }

    private RoomServiceItemEntity service(RoomEntity room, String name) {
        RoomServiceItemEntity service = new RoomServiceItemEntity();
        service.setRoom(room);
        service.setName(name);
        service.setActive(true);
        return service;
    }

    private QrCredentialEntity credential(RoomEntity room, UserEntity user, String rawToken) {
        QrCredentialEntity credential = new QrCredentialEntity();
        credential.setRoom(room);
        credential.setTokenHash(tokenService.hash(rawToken));
        credential.setType(QrCredentialType.PERMANENT_ROOM);
        credential.setActive(true);
        credential.setCreatedByUser(user);
        return credential;
    }
}
