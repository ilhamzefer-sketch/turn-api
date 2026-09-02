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

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class LiveQueueApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private LiveQueueSessionRepository sessionRepository;

    @Test
    void runsSecuredOperatorAndPasswordlessQrFlowWithoutPublicPii() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0507330001");
        long roomId = createPublishedRoom(csrf, accessToken);
        mockMvc.perform(get("/api/rooms/{roomId}/live-queue", roomId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceOverride").value("AUTO"))
                .andExpect(jsonPath("$.acceptingNewEntries").value(true));

        MvcResult qrResult = mockMvc.perform(post("/api/rooms/{roomId}/qr-codes", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        JsonNode qr = objectMapper.readTree(qrResult.getResponse().getContentAsString());
        String qrToken = qr.get("token").asText();

        MvcResult joinResult = mockMvc.perform(post("/api/public/qr/{token}/live-queue/join", qrToken)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Gizli Müştəri\",\"phone\":\"0507330011\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicReference").isNotEmpty())
                .andReturn();
        String publicReference = objectMapper.readTree(joinResult.getResponse().getContentAsString())
                .get("publicReference").asText();

        mockMvc.perform(get("/api/public/rooms/{roomId}/live-queue", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].publicReference").value(publicReference))
                .andExpect(jsonPath("$.entries[0].displayName").doesNotExist())
                .andExpect(jsonPath("$.entries[0].phone").doesNotExist())
                .andExpect(jsonPath("$.entries[0].internalNote").doesNotExist());

        mockMvc.perform(get("/api/rooms/{roomId}/live-queue", roomId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].displayName").value("Gizli Müştəri"))
                .andExpect(jsonPath("$.entries[0].phone").value("+994507330011"));

        mockMvc.perform(post("/api/rooms/{roomId}/live-queue/call-next", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublicReference").value(publicReference));

        mockMvc.perform(post("/api/rooms/{roomId}/live-queue/complete-current", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].status").value("COMPLETED"));

        mockMvc.perform(delete("/api/rooms/{roomId}/qr-codes/{credentialId}", roomId, qr.get("id").asLong())
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/public/qr/{token}/live-queue", qrToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedOperationAndInvalidManualSource() throws Exception {
        TestCsrfToken csrf = csrf();
        mockMvc.perform(post("/api/rooms/1/live-queue/open")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value()))
                .andExpect(status().isUnauthorized());

        String accessToken = register(csrf, "0507330002");
        long roomId = createPublishedRoom(csrf, accessToken);
        mockMvc.perform(post("/api/rooms/{roomId}/live-queue/entries", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Manual\",\"phone\":\"0507330022\",\"source\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOperatorAccessUntilRequiredRoomSetupIsPublished() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0507330003");
        long roomId = createDraftLiveRoom(csrf, accessToken);

        mockMvc.perform(get("/api/rooms/{roomId}/live-queue", roomId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Otağı istifadə etmək üçün məcburi mərhələləri tamamlayın və otağı yayımlayın."
                ));
    }

    @Test
    void recreatesADeletedAutomaticSessionWhenOperatorOpensTheRoom() throws Exception {
        TestCsrfToken csrf = csrf();
        String accessToken = register(csrf, "0507330004");
        long roomId = createPublishedRoom(csrf, accessToken);
        LiveQueueSessionEntity original = sessionRepository.findByRoomIdAndOpenSlot(roomId, 1).orElseThrow();
        long originalSessionId = original.getId();
        sessionRepository.delete(original);
        sessionRepository.flush();

        MvcResult result = mockMvc.perform(get("/api/rooms/{roomId}/live-queue", roomId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptanceOverride").value("AUTO"))
                .andReturn();

        long recreatedSessionId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(recreatedSessionId).isNotEqualTo(originalSessionId);
        assertThat(sessionRepository.findByRoomIdAndOpenSlot(roomId, 1)).isPresent();
    }

    private long createPublishedRoom(TestCsrfToken csrf, String accessToken) throws Exception {
        String currentDay = ZonedDateTime.now(ZoneId.of("Asia/Baku")).getDayOfWeek().name();
        long roomId = createDraftLiveRoom(csrf, accessToken);
        mockMvc.perform(put("/api/rooms/{roomId}/availability-rules", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[{\"dayOfWeek\":\"" + currentDay + "\","
                                + "\"startTime\":\"00:00\",\"endTime\":\"23:59\","
                                + "\"active\":true}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms/{roomId}/publish", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
        return roomId;
    }

    private long createDraftLiveRoom(TestCsrfToken csrf, String accessToken) throws Exception {
        MvcResult workspaceResult = mockMvc.perform(post("/api/individual-workspaces")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Canlı workspace\",\"timezone\":\"Asia/Baku\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long workspaceId = objectMapper.readTree(workspaceResult.getResponse().getContentAsString()).get("id").asLong();
        MvcResult roomResult = mockMvc.perform(post("/api/individual-workspaces/{workspaceId}/rooms", workspaceId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Canlı qəbul\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationMode").value("LIVE_QUEUE"))
                .andExpect(jsonPath("$.defaultSlotDurationMinutes").value(30))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.liveQueueResetPolicy").value("DAILY_AT_TIME"))
                .andExpect(jsonPath("$.liveQueueResetLocalTime").value("00:00:00"))
                .andExpect(jsonPath("$.liveQueueAcceptingNewEntries").value(true))
                .andReturn();
        return objectMapper.readTree(roomResult.getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"firstName\":\"Live\","
                                + "\"lastName\":\"Owner\",\"password\":\"Live-safe-2026\"}"))
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
