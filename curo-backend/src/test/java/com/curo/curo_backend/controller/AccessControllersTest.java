package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AccessGrantView;
import com.curo.curo_backend.dto.ActorView;
import com.curo.curo_backend.dto.AuditEntryView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.ShareCodeResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.service.AccessGrantService;
import com.curo.curo_backend.service.AccessGuard;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.AuditService;
import com.curo.curo_backend.service.CurrentUserService;
import com.curo.curo_backend.service.GrantExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccessControllersTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private AccessGrantService grantService;
    private AccessGuard guard;
    private AuditService auditService;
    private CurrentUserService currentUser;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        grantService = mock(AccessGrantService.class);
        guard = mock(AccessGuard.class);
        auditService = mock(AuditService.class);
        currentUser = mock(CurrentUserService.class);

        AccessGrantController grants = new AccessGrantController();
        ReflectionTestUtils.setField(grants, "accessGrantService", grantService);
        PatientAccessController me = new PatientAccessController();
        ReflectionTestUtils.setField(me, "accessGrantService", grantService);
        ReflectionTestUtils.setField(me, "auditService", auditService);
        ReflectionTestUtils.setField(me, "currentUser", currentUser);
        CareIdController careId = new CareIdController();
        ReflectionTestUtils.setField(careId, "accessGuard", guard);

        mvc = MockMvcBuilders.standaloneSetup(grants, me, careId).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private AccessGrantView view(GrantStatus status) {
        return new AccessGrantView("g1", "CID-7K3MQ9", "CLINICAL", List.of(GrantScope.PROFILE), status,
                new ActorView("d1", "DOCTOR", "Dr Test", "Test Hospital"), "Follow-up", T0, null, null, null);
    }

    // ---- /access-grants

    @Test
    void requestReturns201WithTheContractShape() throws Exception {
        when(grantService.requestAccess(any())).thenReturn(view(GrantStatus.PENDING));

        mvc.perform(post("/access-grants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careId\":\"CID-7K3MQ9\",\"scope\":[\"PROFILE\"],\"reason\":\"Follow-up\",\"requestedDurationHours\":24}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("g1"))
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.type").value("CLINICAL"))
                .andExpect(jsonPath("$.scope[0]").value("PROFILE"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requester.userId").value("d1"))
                .andExpect(jsonPath("$.requester.role").value("DOCTOR"))
                .andExpect(jsonPath("$.requester.facilityName").value("Test Hospital"))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-23T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void requestPassesTheShareCodeThrough() throws Exception {
        when(grantService.requestAccess(any())).thenReturn(view(GrantStatus.ACTIVE));

        mvc.perform(post("/access-grants").contentType(MediaType.APPLICATION_JSON).content("{\"shareCode\":\"042913\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.AccessRequest.class);
        verify(grantService).requestAccess(captor.capture());
        assertThat(captor.getValue().getShareCode()).isEqualTo("042913");
        assertThat(captor.getValue().getCareId()).isNull();
    }

    @Test
    void grantExistsIs409AndCarriesTheExistingGrantId() throws Exception {
        when(grantService.requestAccess(any())).thenThrow(new GrantExistsException("g7"));

        mvc.perform(post("/access-grants").contentType(MediaType.APPLICATION_JSON).content("{\"careId\":\"CID-7K3MQ9\",\"scope\":[\"PROFILE\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("GRANT_EXISTS"))
                .andExpect(jsonPath("$.grantId").value("g7"));
    }

    @Test
    void otherErrorsHaveNoGrantIdField() throws Exception {
        when(grantService.requestAccess(any())).thenThrow(new ApiException(HttpStatus.GONE, "SHARE_CODE_EXPIRED", "That share code has expired."));

        mvc.perform(post("/access-grants").contentType(MediaType.APPLICATION_JSON).content("{\"shareCode\":\"042913\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("SHARE_CODE_EXPIRED"))
                .andExpect(jsonPath("$.grantId").doesNotExist());
    }

    @Test
    void approveWorksWithAndWithoutABody() throws Exception {
        when(grantService.approve(eq("g1"), any())).thenReturn(view(GrantStatus.ACTIVE));

        mvc.perform(post("/access-grants/g1/approve")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/access-grants/g1/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durationHours\":2,\"scope\":[\"PROFILE\"]}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.ApproveRequest.class);
        verify(grantService, org.mockito.Mockito.times(2)).approve(eq("g1"), captor.capture());
        assertThat(captor.getAllValues().get(0)).isNull();
        assertThat(captor.getAllValues().get(1).getDurationHours()).isEqualTo(2);
    }

    @Test
    void revokeReturnsTheGrant() throws Exception {
        when(grantService.revoke("g1")).thenReturn(view(GrantStatus.REVOKED));

        mvc.perform(post("/access-grants/g1/revoke")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void listPassesFiltersAndPagingAndReturnsThePagedShape() throws Exception {
        when(grantService.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(1, 5, 11, List.of(view(GrantStatus.ACTIVE))));

        mvc.perform(get("/access-grants").param("status", "ACTIVE").param("careId", "CID-7K3MQ9").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.total").value(11))
                .andExpect(jsonPath("$.items[0].id").value("g1"));
        verify(grantService).list("ACTIVE", "CID-7K3MQ9", 1, 5);
    }

    @Test
    void listDefaultsToFirstPageOfTwenty() throws Exception {
        when(grantService.list(any(), any(), anyInt(), anyInt())).thenReturn(new PagedResponse<>(0, 20, 0, List.of()));

        mvc.perform(get("/access-grants")).andExpect(status().isOk());
        verify(grantService).list(isNull(), isNull(), eq(0), eq(20));
    }

    // ---- /patient/me

    @Test
    void shareCodeEndpointReturns201WithCodeAndExpiry() throws Exception {
        when(grantService.createShareCode(any())).thenReturn(new ShareCodeResponse("042913", T0.plusSeconds(900)));

        mvc.perform(post("/patient/me/share-codes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":[\"PROFILE\",\"PRESCRIPTIONS\"],\"durationHours\":24}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareCode").value("042913"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-23T10:15:00Z"));
    }

    @Test
    void accessLogReadsTheSignedInPatientsLogWithFilters() throws Exception {
        AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
        when(currentUser.require()).thenReturn(patient);
        AuditEntryView entry = new AuditEntryView("e1", T0, new ActorView("d1", "DOCTOR", "Dr Test", "Test Hospital"),
                AuditAction.READ, "PROFILE", "CID-7K3MQ9", "g1", AuditOutcome.ALLOWED);
        when(auditService.listForPatient(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(0, 20, 1, List.of(entry)));

        mvc.perform(get("/patient/me/access-log").param("from", "2026-10-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z")
                        .param("actorRole", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].actor.name").value("Dr Test"))
                .andExpect(jsonPath("$.items[0].actor.role").value("DOCTOR"))
                .andExpect(jsonPath("$.items[0].action").value("READ"))
                .andExpect(jsonPath("$.items[0].resource").value("PROFILE"))
                .andExpect(jsonPath("$.items[0].grantId").value("g1"))
                .andExpect(jsonPath("$.items[0].outcome").value("ALLOWED"))
                .andExpect(jsonPath("$.items[0].at").value("2026-10-23T10:00:00Z"));
        verify(auditService).listForPatient("p1", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"),
                "DOCTOR", 0, 20);
    }

    @Test
    void accessLogIsOnlyForPatientsAndAlwaysTheirOwn() throws Exception {
        when(currentUser.require()).thenReturn(user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD"));

        mvc.perform(get("/patient/me/access-log")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN_ROLE"));
        verifyNoInteractions(auditService);
    }

    @Test
    void aBadDateIs400NotAServerError() throws Exception {
        when(currentUser.require()).thenReturn(user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9"));

        mvc.perform(get("/patient/me/access-log").param("from", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ---- /careid

    @Test
    void careIdLookupReturnsTheMinimalRecordWhenGranted() throws Exception {
        AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
        patient.setPhone("9000000001");
        when(guard.require(eq("CID-7K3MQ9"), eq(GrantScope.PROFILE), eq(AuditAction.READ), any(), any()))
                .thenReturn(new AccessGuard.Granted(patient, null));

        mvc.perform(get("/careid/CID-7K3MQ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.name").value("Test Patient"))
                .andExpect(jsonPath("$.consent.accepted").value(true))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.ageYears").doesNotExist());
    }

    @Test
    void careIdLookupWithoutAGrantIs403NoActiveGrant() throws Exception {
        when(guard.require(any(), any(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "You do not have an active grant for this patient's data."));

        mvc.perform(get("/careid/CID-7K3MQ9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ACTIVE_GRANT"))
                .andExpect(jsonPath("$.name").doesNotExist());
    }
}
