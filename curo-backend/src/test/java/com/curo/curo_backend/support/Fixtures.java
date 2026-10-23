package com.curo.curo_backend.support;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static AppUser user(String id, String role, String name, String careId) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setRole(role);
        u.setName(name);
        u.setCareId(careId);
        u.setPhone("90000" + id.hashCode() % 100000);
        Consent c = new Consent();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        u.setConsent(c);
        return u;
    }

    public static AccessGrant grant(String id, AppUser patient, AppUser grantee, GrantStatus status, Instant expiresAt, GrantScope... scope) {
        AccessGrant g = new AccessGrant();
        g.setId(id);
        g.setPatientId(patient.getId());
        g.setCareId(patient.getCareId());
        g.setGranteeId(grantee.getId());
        g.setRole(grantee.getRole());
        g.setGranteeName(grantee.getName());
        g.setScope(List.of(scope));
        g.setStatus(status);
        g.setExpiresAt(expiresAt);
        g.setCreatedAt(Instant.parse("2026-10-23T09:00:00Z"));
        return g;
    }

    /** Makes this user the signed-in one, as the JWT filter would. */
    public static void signIn(AppUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
    }

    public static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
