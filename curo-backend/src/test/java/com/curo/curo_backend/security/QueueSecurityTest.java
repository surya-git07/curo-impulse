package com.curo.curo_backend.security;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Who may use the queue, and proof that being in it opens nothing. Databases are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
class QueueSecurityTest {

    private static final String BODY = "{\"careId\":\"CID-7K3MQ9\"}";
    private static final String CHECK_IN_BODY = "{\"careId\":\"CID-7K3MQ9\",\"doctorId\":\"u-DOCTOR\"}";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("licence.verification.seed-on-startup", () -> "false");
        registry.add("drugs.seed-on-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    // the service behaviour is covered by QueueServiceTest; this test is about who gets through and what the queue opens
    @MockitoBean
    private QueueService queueService;

    @MockitoBean
    private AccessGrantRepository grantRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void users() {
        for (String role : new String[]{"RECEPTION", "DOCTOR", "PATIENT", "PHARMACY", "HOSPITAL", "ADMIN"}) {
            AppUser u = new AppUser();
            u.setId("u-" + role);
            u.setRole(role);
            u.setName("Test " + role);
            u.setCareId("CID-" + role.substring(0, 5).replace('I', 'J').replace('O', 'P') + "2");
            when(userRepository.findById("u-" + role)).thenReturn(Optional.of(u));
        }
        AppUser patient = new AppUser();
        patient.setId("p1");
        patient.setRole("PATIENT");
        patient.setName("Test Patient");
        patient.setCareId("CID-7K3MQ9");
        when(userRepository.findByCareId("CID-7K3MQ9")).thenReturn(Optional.of(patient));
        when(userRepository.findById("p1")).thenReturn(Optional.of(patient));
    }

    private int as(String role, MockHttpServletRequestBuilder request) throws Exception {
        String token = jwtService.generateToken("u-" + role, role, "CID-X2222" + role.charAt(0), null);
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder checkIn() {
        return post("/reception/check-in").contentType("application/json").content(CHECK_IN_BODY);
    }

    @Test
    void receptionCanCheckAPatientIn() throws Exception {
        assertThat(as("RECEPTION", checkIn())).isEqualTo(201);
    }

    @Test
    void receptionListsDoctorsAndNoOtherRoleDoes() throws Exception {
        assertThat(as("RECEPTION", get("/reception/doctors"))).isEqualTo(200);
        for (String role : new String[]{"DOCTOR", "PATIENT", "PHARMACY", "HOSPITAL", "ADMIN"}) {
            assertThat(as(role, get("/reception/doctors"))).as(role).isEqualTo(403);
        }
        assertThat(mvc.perform(get("/reception/doctors")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void everyOtherRoleIsRefusedCheckIn() throws Exception {
        for (String role : new String[]{"DOCTOR", "PATIENT", "PHARMACY", "HOSPITAL", "ADMIN"}) {
            assertThat(as(role, checkIn())).as(role).isEqualTo(403);
        }
        assertThat(mvc.perform(checkIn()).andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void onlyDoctorsReadTheQueueAndMarkDone() throws Exception {
        assertThat(as("DOCTOR", get("/doctor/queue"))).isEqualTo(200);
        assertThat(as("DOCTOR", patch("/doctor/queue/q1/done"))).isEqualTo(200);
        for (String role : new String[]{"RECEPTION", "PATIENT", "PHARMACY", "HOSPITAL"}) {
            assertThat(as(role, get("/doctor/queue"))).as(role).isEqualTo(403);
            assertThat(as(role, patch("/doctor/queue/q1/done"))).as(role).isEqualTo(403);
        }
        assertThat(mvc.perform(get("/doctor/queue")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void aPatientGetsA403OnAllThree() throws Exception {
        assertThat(as("PATIENT", checkIn())).isEqualTo(403);
        assertThat(as("PATIENT", get("/doctor/queue"))).isEqualTo(403);
        assertThat(as("PATIENT", patch("/doctor/queue/q1/done"))).isEqualTo(403);
    }

    @Test
    void receptionCanCheckInButCannotReadAnyRecord() throws Exception {
        assertThat(as("RECEPTION", checkIn())).isEqualTo(201);
        assertThat(as("RECEPTION", get("/careid/CID-7K3MQ9"))).isEqualTo(403);
        assertThat(as("RECEPTION", get("/patient/CID-7K3MQ9/medications"))).isEqualTo(403);
        assertThat(as("RECEPTION", post("/prescription/issue").contentType("application/json").content("{}"))).isEqualTo(403);
        assertThat(as("RECEPTION", post("/access-grants").contentType("application/json").content(BODY))).isEqualTo(403);
    }

    @Test
    void beingInTheQueueGivesADoctorNoAccessToTheRecord() throws Exception {
        assertThat(as("RECEPTION", checkIn())).isEqualTo(201);
        // the patient is waiting, the doctor can see them in the queue ...
        assertThat(as("DOCTOR", get("/doctor/queue"))).isEqualTo(200);
        // ... but opening the record still needs an active grant (the grant repository is empty)
        String token = jwtService.generateToken("u-DOCTOR", "DOCTOR", "CID-X2222D", null);
        var careId = mvc.perform(get("/careid/CID-7K3MQ9").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(careId.getStatus()).isEqualTo(403);
        assertThat(careId.getContentAsString()).contains("NO_ACTIVE_GRANT");
        var meds = mvc.perform(get("/patient/CID-7K3MQ9/medications").header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(meds.getStatus()).isEqualTo(403);
        assertThat(meds.getContentAsString()).contains("NO_ACTIVE_GRANT");
    }
}
