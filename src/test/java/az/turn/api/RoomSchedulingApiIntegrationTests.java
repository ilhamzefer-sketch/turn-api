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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.rate-limit.auth-per-minute=100")
@AutoConfigureMockMvc
class RoomSchedulingApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void configuresAndPublishesPlannedRoomThroughSecuredApi() throws Exception {
        TestCsrfToken csrf = csrf();
        String token = register(csrf, "0507222201");
        long workspaceId = createWorkspace(csrf, token);
        long roomId = createRoom(csrf, token, workspaceId);

        mockMvc.perform(put("/api/rooms/{roomId}/availability-rules", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[
                                  {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"13:00","active":true},
                                  {"dayOfWeek":"MONDAY","startTime":"14:00","endTime":"18:00","active":true}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(put("/api/rooms/{roomId}/configuration", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultSlotDurationMinutes":30,"appointmentBufferMinutes":5,
                                "bookingWindowDays":30,"minimumAdvanceMinutes":30,
                                "cancellationCutoffMinutes":120,"liveQueueAcceptingNewEntries":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentBufferMinutes").value(5));

        mockMvc.perform(post("/api/rooms/{roomId}/services", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Məsləhət","price":20.00,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("AZN"));

        mockMvc.perform(post("/api/rooms/{roomId}/publish", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void rejectsOverlappingScheduleAndUnauthenticatedConfiguration() throws Exception {
        TestCsrfToken csrf = csrf();
        mockMvc.perform(put("/api/rooms/1/configuration")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        String token = register(csrf, "0507222202");
        long roomId = createRoom(csrf, token, createWorkspace(csrf, token));

        mockMvc.perform(put("/api/rooms/{roomId}/configuration", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/rooms/{roomId}/availability-rules", roomId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rules":[
                                  {"dayOfWeek":"TUESDAY","startTime":"09:00","endTime":"13:00","active":true},
                                  {"dayOfWeek":"TUESDAY","startTime":"12:00","endTime":"15:00","active":true}
                                ]}
                                """))
                .andExpect(status().isConflict());
    }

    private long createWorkspace(TestCsrfToken csrf, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/individual-workspaces")
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Şəxsi təqvim\",\"timezone\":\"Asia/Baku\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createRoom(TestCsrfToken csrf, String token, long workspaceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/individual-workspaces/{workspaceId}/rooms", workspaceId)
                        .cookie(csrf.cookie())
                        .header(CsrfCookieFilter.CSRF_HEADER_NAME, csrf.value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Planlı qəbul","reservationMode":"PLANNED_BOOKING",
                                "defaultSlotDurationMinutes":30,"visibility":"UNLISTED"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(TestCsrfToken csrf, String phone) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("phone", phone)
                .put("firstName", "Schedule")
                .put("lastName", "Owner")
                .put("password", "Schedule-safe-2026")
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
