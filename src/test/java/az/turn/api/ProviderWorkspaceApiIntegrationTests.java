package az.turn.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class ProviderWorkspaceApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    @Test
    void createsBusinessBranchAndDraftRoomThroughSecuredApi() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0507111101", "Aysel", "Sahib");

        MvcResult businessResult = mockMvc.perform(post("/api/businesses")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Aysel Studio","phone":"0507111101","timezone":"Asia/Baku"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+994507111101"))
                .andReturn();
        long businessId = objectMapper.readTree(businessResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult branchResult = mockMvc.perform(post("/api/businesses/{businessId}/branches", businessId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mərkəz","address":"Nizami 1","city":"Bakı","district":"Nəsimi"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectivePhone").value("+994507111101"))
                .andReturn();
        long branchId = objectMapper.readTree(branchResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/branches/{branchId}/rooms", branchId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Kosmetoloq","reservationMode":"PLANNED_BOOKING",
                                "defaultSlotDurationMinutes":30,"visibility":"UNLISTED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessId").value(businessId))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/users/me/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CUSTOMER"))
                .andExpect(jsonPath("$[1].type").value("BUSINESS"));
    }

    @Test
    void rejectsUnauthenticatedAndWrongOwnerAccess() throws Exception {
        mockMvc.perform(get("/api/businesses")).andExpect(status().isUnauthorized());

        TestCsrfToken csrf = csrf();
        String ownerToken = register(csrf, "0507111102", "Birinci", "Sahib");
        String strangerToken = register(csrf, "0507111103", "Başqa", "Şəxs");
        MvcResult businessResult = mockMvc.perform(post("/api/businesses")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Qapalı biznes","phone":"0507111102"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long businessId = objectMapper.readTree(businessResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsRoomAndPendingBusinessInvitationsThroughOneEndpoint() throws Exception {
        TestCsrfToken csrf = csrf();
        register(csrf, "0507111104", "Biznes", "Sahibi");
        String employeeToken = register(csrf, "0507111105", "Otaq", "Sahibi");
        UserEntity owner = userRepository.findByNormalizedPhone("+994507111104").orElseThrow();
        UserEntity employee = userRepository.findByNormalizedPhone("+994507111105").orElseThrow();
        BusinessResponseDto business = businessService.create(
                owner.getId(),
                new BusinessUpsertRequestDto("Dəvət biznesi", null, null, null, null, "0507111104", "Asia/Baku", null, null)
        );
        BranchResponseDto branch = branchService.create(
                business.id(),
                owner.getId(),
                new BranchUpsertRequestDto(
                        "Mərkəz", "Nizami 1", "Bakı", "Nəsimi", null, null, null, null, "Asia/Baku"
                )
        );
        RoomResponseDto room = roomService.createBusinessRoom(
                branch.id(),
                owner.getId(),
                new RoomUpsertRequestDto(
                        "Dəvət otağı", null, null, null, "Asia/Baku", ReservationMode.LIVE_QUEUE,
                        30, RoomVisibility.UNLISTED, null, null, null
                )
        );
        membershipService.invite(
                business.id(),
                owner.getId(),
                new BusinessMemberInviteRequestDto("0507111105", null, null, BusinessRole.EMPLOYEE)
        );
        RoomAssignmentDto invitation = assignmentService.invite(
                room.id(),
                owner.getId(),
                new RoomAssignmentInviteRequestDto(employee.getId())
        );

        mockMvc.perform(post("/api/users/me/room-invitations/{invitationId}/accept", invitation.id())
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/users/me/invitations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessInvitations").isEmpty())
                .andExpect(jsonPath("$.roomInvitations").isEmpty());
        mockMvc.perform(get("/api/users/me/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].type").value("ROOM"));
    }

    private String register(
            TestCsrfToken csrf,
            String phone,
            String firstName,
            String lastName
    ) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", firstName)
                .put("lastName", lastName)
                .put("password", "Provider-safe-2026")
                .toString();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private TestCsrfToken csrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie(CsrfCookieFilter.CSRF_COOKIE_NAME);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TestCsrfToken(cookie, body.get("csrfToken").asText());
    }
}
