package com.curo.curo_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Runs the real security filter chains. Only requests that are refused before any database call are made here,
 * since there is no MongoDB in the test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessRoutesSecurityTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("licence.verification.seed-on-startup", () -> "false");
        registry.add("drugs.seed-on-startup", () -> "false");
        registry.add("pharmacies.seed-on-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    private int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtService.generateToken("u-" + role, role, "CID-7K3MQ9", null));
    }

    @Test
    void noTokenMeansNoAccessToAnyOfTheNewRoutes() throws Exception {
        assertThat(statusOf(get("/careid/CID-7K3MQ9"))).isIn(401, 403);
        assertThat(statusOf(post("/access-grants").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(post("/access-grants/g1/approve"))).isIn(401, 403);
        assertThat(statusOf(post("/access-grants/g1/revoke"))).isIn(401, 403);
        assertThat(statusOf(get("/access-grants"))).isIn(401, 403);
        assertThat(statusOf(post("/patient/me/share-codes").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/me/access-log"))).isIn(401, 403);
    }

    @Test
    void patientRoutesAreNoLongerPublic() throws Exception {
        assertThat(statusOf(get("/patient/CID-7K3MQ9/summary"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/CID-7K3MQ9/history"))).isIn(401, 403);
    }

    @Test
    void patientOnlyRoutesRefuseEveryOtherRoleBeforeReachingTheDatabase() throws Exception {
        for (String role : new String[]{"DOCTOR", "PHARMACY", "CAREGIVER", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/patient/me/access-log")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/patient/me/share-codes").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void noTokenMeansNoAccessToThePrescriptionRoutes() throws Exception {
        assertThat(statusOf(post("/prescription/issue").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/prescription/rx1").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(post("/prescription/rx1/discontinue"))).isIn(401, 403);
        assertThat(statusOf(get("/prescription/rx1/history"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/CID-7K3MQ9/medications"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/me/medications"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/me"))).isIn(401, 403);
        assertThat(statusOf(get("/drugs/list"))).isIn(401, 403);
    }

    @Test
    void onlyDoctorsReachTheWriteRoutes() throws Exception {
        for (String role : new String[]{"PATIENT", "PHARMACY", "CAREGIVER"}) {
            assertThat(statusOf(as(role, post("/prescription/issue").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/prescription/rx1").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/prescription/rx1/discontinue")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void historyIsForDoctorsAndPharmaciesOnly() throws Exception {
        for (String role : new String[]{"PATIENT", "CAREGIVER", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/prescription/rx1/history")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void medicationRoutesKeepPatientsAndCliniciansApart() throws Exception {
        for (String role : new String[]{"PATIENT", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/patient/CID-7K3MQ9/medications")))).as(role).isEqualTo(403);
        }
        for (String role : new String[]{"DOCTOR", "PHARMACY", "CAREGIVER", "ADMIN"}) {
            assertThat(statusOf(as(role, get("/patient/me/medications")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, get("/patient/me")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void theDrugListIsNotForPatients() throws Exception {
        for (String role : new String[]{"PATIENT", "CAREGIVER"}) {
            assertThat(statusOf(as(role, get("/drugs/list")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void theDoseRoutesNeedALoginAndAPatient() throws Exception {
        assertThat(statusOf(get("/patient/me/doses/today"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/me/dose-log"))).isIn(401, 403);
        assertThat(statusOf(post("/doses/d1/taken"))).isIn(401, 403);
        for (String role : new String[]{"DOCTOR", "PHARMACY", "CAREGIVER", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/patient/me/doses/today")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, get("/patient/me/dose-log")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/doses/d1/taken")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void theSchedulerEndpointIsReachableWithoutAJwtButRefusesWithoutTheKey() throws Exception {
        var noKey = mvc.perform(post("/internal/scheduler/dose-tick")).andReturn().getResponse();
        assertThat(noKey.getStatus()).as("refused by the controller, not by the login filter").isEqualTo(401);
        assertThat(noKey.getContentAsString()).contains("INVALID_KEY");

        var wrongKey = mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "wrong")).andReturn().getResponse();
        assertThat(wrongKey.getStatus()).isEqualTo(401);
        assertThat(wrongKey.getContentAsString()).contains("INVALID_KEY").doesNotContain("wrong");

        // a signed-in user of any role is not the scheduler
        for (String role : new String[]{"PATIENT", "DOCTOR", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, post("/internal/scheduler/dose-tick")))).as(role).isEqualTo(401);
        }
    }

    @Test
    void onlyTheSchedulerPathIsOpenedUp() throws Exception {
        assertThat(statusOf(get("/internal"))).isIn(401, 403, 404);
        assertThat(statusOf(get("/patient/me/doses/today"))).isIn(401, 403);
        assertThat(statusOf(get("/some-other-path"))).isIn(401, 403);
    }

    @Test
    void thePharmacyRoutesNeedALogin() throws Exception {
        assertThat(statusOf(get("/pharmacies/nearby?lat=12.9&lng=77.6"))).isIn(401, 403);
        assertThat(statusOf(get("/pharmacy/stock/pharm1"))).isIn(401, 403);
        assertThat(statusOf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/pharmacy/stock/pharm1").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(post("/pharmacy/dispense").contentType("application/json").content("{}"))).isIn(401, 403);
    }

    @Test
    void stockAndDispenseAreForPharmaciesNotPatientsDoctorsOrCaregivers() throws Exception {
        for (String role : new String[]{"PATIENT", "DOCTOR", "CAREGIVER"}) {
            assertThat(statusOf(as(role, get("/pharmacy/stock/pharm1")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/pharmacy/stock/pharm1").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/pharmacy/dispense").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void theAlertAndCaregiverRoutesNeedALogin() throws Exception {
        assertThat(statusOf(get("/alerts"))).isIn(401, 403);
        assertThat(statusOf(post("/alerts/a1/read"))).isIn(401, 403);
        assertThat(statusOf(get("/caregiver-links"))).isIn(401, 403);
        assertThat(statusOf(post("/caregiver-links").contentType("application/json").content("{}"))).isIn(401, 403);
        assertThat(statusOf(post("/caregiver-links/l1/revoke"))).isIn(401, 403);
        assertThat(statusOf(get("/patient/CID-7K3MQ9/dose-schedule"))).isIn(401, 403);
    }

    @Test
    void caregiverLinksAreForPatientsAndCaregiversOnly() throws Exception {
        for (String role : new String[]{"DOCTOR", "PHARMACY", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/caregiver-links")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/caregiver-links").contentType("application/json").content("{}")))).as(role).isEqualTo(403);
            assertThat(statusOf(as(role, post("/caregiver-links/l1/revoke")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void aPatientCannotReadSomeoneElsesDoseScheduleAndPharmaciesHaveNoWayIn() throws Exception {
        for (String role : new String[]{"PATIENT", "PHARMACY", "ADMIN", "SUPER_ADMIN"}) {
            assertThat(statusOf(as(role, get("/patient/CID-7K3MQ9/dose-schedule")))).as(role).isEqualTo(403);
        }
    }

    @Test
    void aForgedTokenIsNotAccepted() throws Exception {
        String forged = new JwtService() {
            {
                org.springframework.test.util.ReflectionTestUtils.setField(this, "secretKey", UUID.randomUUID() + UUID.randomUUID().toString());
                org.springframework.test.util.ReflectionTestUtils.setField(this, "expirationMs", 60_000L);
            }
        }.generateToken("u-PATIENT", "PATIENT", "CID-7K3MQ9", null);

        assertThat(statusOf(get("/patient/me/access-log").header("Authorization", "Bearer " + forged))).isIn(401, 403);
    }
}
