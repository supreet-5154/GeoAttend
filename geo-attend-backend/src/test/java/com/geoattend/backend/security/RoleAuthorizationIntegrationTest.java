package com.geoattend.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.geoattend.backend.model.Batch;
import com.geoattend.backend.service.BatchService;
import com.geoattend.backend.service.MemberService;
import com.geoattend.backend.service.MemberSignupApprovalService;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class RoleAuthorizationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BatchService batchService;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MemberSignupApprovalService signupApprovalService;

    @MockitoBean
    private com.geoattend.backend.service.AttendanceService attendanceService;

    @MockitoBean
    private com.geoattend.backend.repository.ClubUserRepository clubUserRepository;

    @MockitoBean
    private com.geoattend.backend.repository.MemberRepository memberRepository;

    private MockMvc mockMvc;

    private String adminToken;
    private String clubUserToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();

        adminToken = "Bearer " + jwtService.generateToken(new ClubUser(1L, "Admin User", "ADMIN"));
        clubUserToken = "Bearer " + jwtService.generateToken(new ClubUser(2L, "Club User", "CLUB_USER"));
        memberToken = "Bearer " + jwtService.generateToken(new ClubUser(3L, "Member User", "MEMBER"));
    }

    @Test
    void unauthenticatedUserCanAccessActiveBatches() throws Exception {
        Mockito.when(batchService.getActiveBatches()).thenReturn(List.of(
                new Batch(1L, "5th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 5)
        ));

        mockMvc.perform(get("/api/batches/active"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedUserCannotAccessProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/batches"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/members/signups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCreateBatches() throws Exception {
        Mockito.when(batchService.createBatch("8th Generation", "ACTIVE"))
                .thenReturn(new Batch(8L, "8th Generation", "ACTIVE", LocalDateTime.now(), LocalDateTime.now(), 0));

        mockMvc.perform(post("/api/batches")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"8th Generation\", \"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void clubUserCannotCreateBatches() throws Exception {
        mockMvc.perform(post("/api/batches")
                        .header("Authorization", clubUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"8th Generation\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotCreateBatchesOrAccessStaffBatches() throws Exception {
        mockMvc.perform(post("/api/batches")
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"8th Generation\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/batches")
                        .header("Authorization", memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void bothAdminAndClubUserCanAccessSignupQueue() throws Exception {
        mockMvc.perform(get("/api/members/signups")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/members/signups")
                        .header("Authorization", clubUserToken))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotAccessSignupQueue() throws Exception {
        mockMvc.perform(get("/api/members/signups")
                        .header("Authorization", memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void bothAdminAndClubUserCanApproveAndRejectSignups() throws Exception {
        mockMvc.perform(post("/api/members/signups/10/approve")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\": 1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/members/signups/11/approve")
                        .header("Authorization", clubUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/members/signups/12/reject")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Incomplete submission\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/members/signups/13/reject")
                        .header("Authorization", clubUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Not eligible\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotApproveOrRejectSignups() throws Exception {
        mockMvc.perform(post("/api/members/signups/10/approve")
                        .header("Authorization", memberToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/members/signups/10/reject")
                        .header("Authorization", memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void bothAdminAndClubUserCanAssignMemberBatch() throws Exception {
        mockMvc.perform(put("/api/members/101/batch")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/members/101/batch")
                        .header("Authorization", clubUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\": 3}"))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotAssignMemberBatch() throws Exception {
        mockMvc.perform(put("/api/members/101/batch")
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\": 2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanAccessAttendanceApi() throws Exception {
        mockMvc.perform(get("/api/attendance")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance")
                        .header("Authorization", clubUserToken))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotAccessStaffAttendanceApi() throws Exception {
        mockMvc.perform(get("/api/attendance")
                        .header("Authorization", memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshTokenWithValidBearerTokenReturnsOkAndNewToken() throws Exception {
        Mockito.when(clubUserRepository.getUserById(1L))
                .thenReturn(new ClubUser(1L, "Admin User", "ADMIN"));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void refreshTokenWithoutTokenReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshTokenWithTamperedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Authorization", "Bearer invalid.token.payload"))
                .andExpect(status().isUnauthorized());
    }
}
