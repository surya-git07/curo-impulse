# Curo API Contract (DRAFT for review)

Status: **DRAFT v0.3, not yet agreed.** Owner: Vasuki (backend/frontend). Consumers: frontend (Vasuki), patient-app (Sangami), infra (Surya).
Any change to this file ships in the same commit as the code change and is announced (CLAUDE.md).

Derived by reading the old apps in `_reference/` (hospital-backend, hospital-site, patient-app). All sample data below is fake.

**Changes in v0.2:** AccessGrants and access log (section 3); prescription versions, modify, discontinue and history (section 7); medication profile, dose schedule and dose log (section 8); interaction and allergy check, non-blocking (section 9); alerts, caregiver links, adherence and the scheduler endpoint (section 10); licence fields in signup (section 2); `/auth/consent` renamed `/auth/policy-consent`; one consent field name (`consent`); soft delete on `DELETE /patient/me`; `GET /hospital/list` stays public; RECEPTION folded into ADMIN, SUPER_ADMIN kept; password reset dropped from the MVP. Sections after 2 were renumbered.

**Changes since v0.3 (access grants built):** share codes are six digits; guard, audit and rate-limit behaviour documented in section 3; `/patient/**` is no longer public.

**Changes since v0.3 (guest share):** new section 3b: `POST /patient/me/shares`, `GET /patient/me/shares`, `POST /patient/me/shares/{shareId}/revoke` and the unauthenticated `POST /share/redeem`. Access log entries gain `granteeType` (`USER` or `GUEST`) and an optional `shareId`; `actor` is `null` for a guest. Not built yet.

**Changes since v0.3 (pharmacy finder and stock removed):** `GET /pharmacies/nearby`, `GET` and `PUT /pharmacy/stock/{id}`, coordinates (`lat`/`lng`) on signup and the stock and distance machinery are gone. `POST /pharmacy/dispense` stays: it marks the prescription `DISPENSED` and writes a dispensing log, with no stock check or reduction.

**Changes since v0.3 (build-status audit):** every endpoint in this file was checked against the controllers on `main`. Those with no active mapping are marked **NOT BUILT** (in the Status column, or in the heading). Most of them exist only as disabled legacy code (`@Profile("legacy-jpa")`, which is off by default), so a client that calls them gets 404 or 403 today. `GET /auth/me` is the one endpoint added in this pass. `GET /patient/me/prescriptions/detailed` is NOT BUILT and replaced by `GET /patient/me/medications` (section 12). Only `/auth/signup` and `/auth/login` are public under `/auth`.

**Changes since v0.3 (reception check-in):** `POST /reception/check-in`, `GET /doctor/queue` and `PATCH /doctor/queue/{id}/done` are built (section 6). They use a new `queue_entries` collection. The older `/reception/checkin`, `/reception/queue/{hospitalId}` and `/reception/visit/{id}` rows stay NOT BUILT and are superseded by these.

**Changes since v0.3 (RECEPTION signup):** `POST /auth/signup` accepts role `RECEPTION` (front desk). It is a self-signup role like PATIENT: no licence or council fields, no approval, it can sign in straight away. It is a different role from `ADMIN`, which is not a signup role. RECEPTION cannot prescribe, dispense, request access or read patient data. The frontend dropdown is Reception, Doctor, Pharmacy, Hospital. This replaces the earlier note that Reception never self-signs-up.

**Changes since v0.3 (HOSPITAL signup):** `POST /auth/signup` accepts role `HOSPITAL` (section 2). A hospital account is a facility login only: it cannot prescribe, dispense, request access or read patient data. Signup in the frontend offers Doctor, Pharmacy and Hospital. The licence registry also holds `CLINIC` and `HOSPITAL` entries.

**Changes in v0.3:** `name` is the only name field for every role (`fullName` removed). Licence signup rewritten (section 2): doctors send `licenceNumber`, `stateCouncil`, `name`; pharmacies send `drugLicenceNumber`, `gstin`, `state`, `name`. The response carries `verificationStatus` (`VERIFIED` or `PENDING`) with `verificationReasonCode` and `verificationReason`, replacing `status` / `statusReason`. `VERIFIED` only on a match against a mock `licence_registry`; otherwise `PENDING` for SUPER_ADMIN approval. Wrong number format, wrong name, wrong state and wrong GSTIN return distinct 400 codes; a duplicate licence returns 409. GSTIN validation rules added. The sample GSTIN in the pharmacy example was replaced because the old one failed the check character.

## 1. Conventions

- Base URL comes from config/env (`API_BASE_URL`); never hardcode `localhost` or a hosted URL in clients.
- JSON in and out. Field names are **camelCase** (the old patient-app used snake_case against a separate Python service; that service is merged into this backend, see Open Questions).
- Auth: `Authorization: Bearer <JWT>`. JWT is signed with `JWT_SECRET` (env only).
- IDs: old system used SQL integer ids. New system uses MongoDB string ids (`"id": "665f..."`). Patients are identified externally by **CareID**, never by ABHA.
- Roles: `DOCTOR`, `PATIENT`, `CAREGIVER`, `PHARMACY`, `HOSPITAL`, `RECEPTION`, `ADMIN`, `SUPER_ADMIN`. `RECEPTION` (front desk) is its own role again and self-signs-up; it replaces the earlier plan to fold it into `ADMIN`. `SUPER_ADMIN` (old role, kept) verifies facilities and staff accounts. Neither ADMIN nor SUPER_ADMIN can read patient data (section 3).
- Errors: the old backend returned bare strings as bodies. New contract: `{ "error": "CODE", "message": "human text" }` with the same HTTP statuses.
- Paged lists (new endpoints only): `?page=0&size=20` (size max 100), response `{ "page": 0, "size": 20, "total": 134, "items": [ ... ] }`. Older endpoints keep returning plain arrays.
- Timestamps are ISO-8601 UTC strings. Dates are `YYYY-MM-DD`.
- Status legend: **KEEP** (same behaviour), **CHANGE** (kept, shape or auth changes), **NEW**, **DROP** (not carried over). **NOT BUILT** is added to a row's status when the endpoint has no active controller mapping on `main`; the rest of the row describes the intended contract, not current behaviour. A row without that tag is mapped.

## 2. Identity and auth

### NEW: POST /auth/signup (public)
Creates an account with recorded consent and issues a CareID. Consent is mandatory: `consent.accepted` must be `true`, otherwise 400. `consent` is the single consent field used everywhere in this contract (request, response, `/auth/me`, `/auth/policy-consent`).

**PATIENT / CAREGIVER**
```json
// request
{
  "role": "PATIENT",
  "name": "Test Patient",
  "phone": "9000000001",
  "password": "min 8 chars",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response
{
  "id": "665f0000000000000000a001",
  "careId": "CID-7K3MQ9",
  "name": "Test Patient",
  "role": "PATIENT",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:15:00Z" }
}
```

**DOCTOR** (licence fields required; HPR/ABDM is not used)
```json
// request
{
  "role": "DOCTOR",
  "name": "Anjali Varadarajan",
  "phone": "9000000002",
  "password": "min 8 chars",
  "facilityId": "665f0000000000000000b001",
  "licenceNumber": "TN-48213",
  "stateCouncil": "Tamil Nadu Medical Council",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response
{
  "id": "665f0000000000000000a002",
  "careId": "CID-4HN8XD",
  "name": "Anjali Varadarajan",
  "role": "DOCTOR",
  "verificationStatus": "VERIFIED",
  "verificationReasonCode": "LICENCE_MATCHED",
  "verificationReason": "Licence number, name and council match the licence registry.",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:20:00Z" }
}
```

**PHARMACY** (`name` is the licensed pharmacy / licensee name exactly as on the drug licence; there is no separate person name in the MVP. Licence fields are required when `facilityId` is omitted, because signup then creates the pharmacy facility; if `facilityId` of an existing VERIFIED facility is sent, only the account fields are needed)
```json
// request
{
  "role": "PHARMACY",
  "name": "Sri Velan Medicals",
  "phone": "9000000003",
  "password": "min 8 chars",
  "drugLicenceNumber": "TN/CHE/20B-48213",
  "gstin": "33AAGFS4821K1Z5",
  "state": "TN",
  "address": "1 Test Street, Chennai",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response, number well-formed but not in the registry
{
  "id": "665f0000000000000000a003",
  "careId": "CID-9PW2TC",
  "name": "Sri Velan Medicals",
  "role": "PHARMACY",
  "facilityId": "665f0000000000000000c001",
  "verificationStatus": "PENDING",
  "verificationReasonCode": "LICENCE_NOT_IN_REGISTRY",
  "verificationReason": "Licence number was not found in the licence registry. A SUPER_ADMIN will review it.",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:25:00Z" }
}
```
`address` is optional free text. Signup creates the pharmacy record that the account dispenses as. (Coordinates, geocoding and the nearby finder were removed.)

**RECEPTION** (built; no licence, no council, no GSTIN; `name` is the person's name)
```json
// request
{
  "role": "RECEPTION",
  "name": "Test Receptionist",
  "phone": "9000000020",
  "password": "min 8 chars",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response (no verification fields, no facilityId)
{
  "id": "665f0000000000000000a020",
  "careId": "CID-4HJ8WQ",
  "name": "Test Receptionist",
  "role": "RECEPTION",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:35:00Z" }
}
```
Consent is required as for every role. The account is active at once and can sign in with its CareID or phone. **A RECEPTION account cannot prescribe, dispense, request access (`POST /access-grants`, 403 `FORBIDDEN_ROLE`) or read patient data (403).** The check-in and queue endpoints in section 6 are meant for it but are NOT BUILT, so the frontend Reception page is a placeholder.

**HOSPITAL** (built; `name` is the hospital name exactly as in the registry; the licence number, state and GSTIN are all required)
```json
// request
{
  "role": "HOSPITAL",
  "name": "Kochi Lakeshore Hospital",
  "phone": "9000000010",
  "password": "min 8 chars",
  "licenceNumber": "KL-HOSP-31182",
  "gstin": "32AAKCK5507R1Z2",
  "state": "KL",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response, matched the registry
{
  "id": "665f0000000000000000a010",
  "careId": "CID-4HJ8WQ",
  "name": "Kochi Lakeshore Hospital",
  "role": "HOSPITAL",
  "facilityId": "665f0000000000000000c010",
  "verificationStatus": "VERIFIED",
  "verificationReasonCode": "LICENCE_MATCHED",
  "verificationReason": "Licence number, name and state match the licence registry.",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:30:00Z" }
}
```
Checked with the same rules as a pharmacy: format, `state` is an ISO code, the GSTIN passes the check and its state code matches `state`, then the registry (`HOSPITAL` entries). `VERIFIED` on a match; a well-formed number that is not in the registry is `PENDING` for a SUPER_ADMIN (section 2, account review); a wrong name, state or GSTIN for a registered number is a 400 with the codes in the error table. A licence number already used by another account is `409 LICENCE_TAKEN`. The number is stored in the account's `licenceNumber`. `facilityId` and `facilityName` are set on the account; there is no separate hospital record yet (the old `/hospital/*` endpoints are NOT BUILT). **A HOSPITAL account cannot prescribe, dispense, request access (`POST /access-grants`, 403 `FORBIDDEN_ROLE`) or read patient data (403).**

**`name` is the only name field** for every role (the earlier `fullName` is gone). For PATIENT/CAREGIVER/DOCTOR it is the person's name; for PHARMACY it is the licensee name above; for HOSPITAL it is the hospital name.

**Licence verification (DOCTOR, PHARMACY and HOSPITAL only; `verificationStatus`, `verificationReasonCode` and `verificationReason` are absent for PATIENT and CAREGIVER)**

The server asks a `LicenceVerificationService`. The MVP implementation is a **mock registry**: a `licence_registry` MongoDB collection of invented records, switched on by `licence.verification.mode=mock`. No government registry is called, so VERIFIED here means "matches the mock registry". The same contract holds when a real registry replaces it.

**Demo mode:** setting `licence.verification.mode=demo-accept` (env `LICENCE_VERIFICATION_MODE=demo-accept`; the default stays `mock`) skips the registry lookup and the name match, so a licence that passes the format, GSTIN and state checks is `VERIFIED` with reason code `LICENCE_ACCEPTED_DEMO`; bad formats and GSTINs keep their error codes and duplicates are still `409 LICENCE_TAKEN`. For demos only; the server logs a WARN at startup.

Checks run in this order and stop at the first failure:
| # | Check | Outcome |
|---|---|---|
| 1 | Required fields present (doctor: `licenceNumber`, `stateCouncil`, `name`; pharmacy: `drugLicenceNumber`, `gstin`, `state`, `name`) | 400 `LICENCE_FIELDS_REQUIRED` |
| 2 | Licence number format | 400 `INVALID_LICENCE_NUMBER` |
| 3 | Pharmacy only: `state` is an ISO 3166-2:IN code | 400 `INVALID_STATE` |
| 4 | Pharmacy only: GSTIN is valid (below) | 400 `INVALID_GSTIN`, or 400 `GSTIN_STATE_MISMATCH` if its state code is not the `state` sent |
| 5 | Licence number already used by another account | 409 `LICENCE_TAKEN` |
| 6 | Number found in the registry but the **name** differs | 400 `LICENCE_NAME_MISMATCH` |
| 7 | Number found but the **state** differs (`stateCouncil` for doctors, `state` for pharmacies) | 400 `LICENCE_STATE_MISMATCH` |
| 8 | Pharmacy only: number found but the **GSTIN** differs | 400 `LICENCE_GSTIN_MISMATCH` |
| 9 | Number well-formed but **not in the registry** | 201, `verificationStatus: "PENDING"`, `verificationReasonCode: "LICENCE_NOT_IN_REGISTRY"` |
| 10 | Everything matches | 201, `verificationStatus: "VERIFIED"`, `verificationReasonCode: "LICENCE_MATCHED"` |

- `VERIFIED` only on an exact match against the mock registry (names compared ignoring case, spacing, punctuation and titles such as "Dr"). Nothing else can produce VERIFIED.
- `PENDING`: waits for a SUPER_ADMIN to approve or reject (see below). A PENDING user cannot log in (403 `ACCOUNT_PENDING`); a rejected one gets 403 `ACCOUNT_REJECTED`.
- Error messages never repeat what the registry holds (no names, states or GSTINs of the matched record).
- Licence number comparison ignores case and surrounding spaces; stored in upper case.
- Formats (placeholders to confirm): `licenceNumber` `^[A-Za-z0-9/\-]{4,25}$`; `drugLicenceNumber` `^[A-Za-z0-9/\-]{5,30}$`; `state` an ISO 3166-2:IN code (`TN`, `KA`, ...); `stateCouncil` compared by name with the registry's council.
- Mock registry data lives in code (`LicenceRegistrySeed`), seeded on startup: 10 invented doctors and 5 invented pharmacies, all fictional.

**GSTIN validation** (15 characters, upper case, applied before any registry lookup):
1. Format `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` (state code, PAN, entity number, literal `Z`, check character).
2. The first two digits are a known GST state code, and that code belongs to the `state` sent (for example `33` is `TN`).
3. The last character is the standard GSTIN check character (base-36 weighted checksum over the first 14 characters). This checks the number is well formed; it does not prove the GSTIN is registered.

**Licence signup error codes** (body `{ "error": "<code>", "message": "..." }`; messages never repeat registry data)
| Code | HTTP | Meaning | Roles |
|---|---|---|---|
| `LICENCE_FIELDS_REQUIRED` | 400 | A required licence field is missing or blank | DOCTOR, PHARMACY, HOSPITAL |
| `INVALID_LICENCE_NUMBER` | 400 | Licence number is not in a recognised format | DOCTOR, PHARMACY, HOSPITAL |
| `INVALID_STATE` | 400 | `state` is not an ISO 3166-2:IN code such as `TN` | PHARMACY, HOSPITAL |
| `INVALID_GSTIN` | 400 | GSTIN has the wrong length or pattern, an unknown state code, or a wrong check character | PHARMACY, HOSPITAL |
| `GSTIN_STATE_MISMATCH` | 400 | GSTIN is well formed but its state code belongs to a different state than `state` | PHARMACY, HOSPITAL |
| `LICENCE_TAKEN` | 409 | Another account already uses this licence number | DOCTOR, PHARMACY, HOSPITAL |
| `LICENCE_NAME_MISMATCH` | 400 | Number is in the registry but `name` differs | DOCTOR, PHARMACY, HOSPITAL |
| `LICENCE_STATE_MISMATCH` | 400 | Number is in the registry but `stateCouncil` (doctor) or `state` (pharmacy, hospital) differs | DOCTOR, PHARMACY, HOSPITAL |
| `LICENCE_GSTIN_MISMATCH` | 400 | Number is in the registry but `gstin` differs | PHARMACY, HOSPITAL |

A number that is well formed but not in the registry is not an error: it returns 201 with `verificationStatus: "PENDING"` and `verificationReasonCode: "LICENCE_NOT_IN_REGISTRY"`. If the registry could not be seeded (database down at startup), every well-formed number takes that path until it is seeded.

Other errors: 400 `CONSENT_REQUIRED`, 400 `WEAK_PASSWORD`, 409 `PHONE_TAKEN` (a soft-deleted account's phone stays taken until its data is purged). The consent timestamp (`acceptedAt`) is set server-side; clients never send timestamps.

**CareID format:** `CID-` followed by 6 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no 0/O/1/I). Generated server-side with a cryptographically secure RNG, unique index, retry on collision. Regex: `^CID-[A-HJ-NP-Z2-9]{6}$`. Immutable once issued and never reused, even after account deletion.

### NEW: POST /auth/login (public)  (replaces old /auth/login)
```json
// request
{ "identifier": "CID-7K3MQ9", "password": "..." }      // identifier = CareID or phone
// 200 response
{
  "token": "<jwt>",
  "expiresAt": "2026-10-23T22:15:00Z",
  "user": { "id": "665f...", "careId": "CID-7K3MQ9", "name": "Test Patient", "role": "PATIENT",
            "facilityId": null, "facilityName": null, "profileCompleted": false }
}
```
JWT claims: `sub` (user id), `role`, `careId`, `facilityId` (nullable). Errors: 401 `INVALID_CREDENTIALS` (same message for unknown user, wrong password and deleted account), 403 `ACCOUNT_PENDING` (DOCTOR or PHARMACY awaiting SUPER_ADMIN approval; checked after the password), 403 `ACCOUNT_REJECTED`.
Old response was `{token, username, role, hospitalName}` plus a separate full-name field; old claims were `role, facilityId, facilityName, specialization, staffId`.

### NEW: GET /auth/me (any signed-in role)  (built)
Needs a valid token; without one it is refused (401 or 403). Returns the login `user` fields **flat**, plus `consent`; the three verification fields appear only for DOCTOR and PHARMACY, and any field that is null is left out.
```json
// 200 response
{
  "id": "665f...", "careId": "CID-7K3MQ9", "name": "Test Patient", "role": "PATIENT",
  "facilityId": null, "facilityName": null, "profileCompleted": false,
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:00:00Z" }
}
```
For DOCTOR and PHARMACY the object also has `verificationStatus`, `verificationReasonCode` and `verificationReason`. A deleted or unknown user is `401 UNAUTHENTICATED`. Under `/auth`, only `POST /auth/signup` and `POST /auth/login` are public.

### NEW: POST /auth/policy-consent (any role), **NOT BUILT**  (renamed from /auth/consent)
Records withdrawal or re-acceptance of the privacy policy. Not the same thing as an AccessGrant (section 3), which controls who may see which patient's data.
```json
// request
{ "consent": { "accepted": false, "policyVersion": "2026-10-01" } }
// 200 response
{ "consent": { "accepted": false, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T11:00:00Z" } }
```
Withdrawal blocks new prescriptions for that CareID.

### NEW: GET /careid/{careId} (DOCTOR, PHARMACY; active grant required)
Gated (section 3, scope `PROFILE`); 403 `NO_ACTIVE_GRANT` otherwise. Returns `{ "careId", "name", "ageYears", "gender", "consent": { "accepted": true } }`. 404 if unknown (only after the grant check, so an unknown CareID and an ungranted one are indistinguishable: both return 403 `NO_ACTIVE_GRANT`). Never returns phone or full record. `ageYears` and `gender` are left out of the response until the patient profile is stored. To get access to a CareID, start with `POST /access-grants`.

### NEW: Staff account review (SUPER_ADMIN), **NOT BUILT**
None of these three routes is mapped. Until they are, a PENDING account cannot be approved through the API.

| Method + path | Request | Response |
|---|---|---|
| `GET /admin/users?verificationStatus=PENDING` | none | paged `[{id,careId,role,name,facilityId,licenceNumber,stateCouncil,drugLicenceNumber,gstin,state,verificationStatus,verificationReasonCode,verificationReason,createdAt}]` |
| `POST /admin/users/{id}/verify` | none | user with `verificationStatus:"VERIFIED"` (the user can then log in) |
| `POST /admin/users/{id}/reject` | `{reason}` | user with `verificationStatus:"REJECTED"` (login returns 403 `ACCOUNT_REJECTED`) |

### DROP: POST /auth/send-otp, POST /auth/reset-password (from patient-app)
Password reset is out of the MVP (old behaviour also returned the OTP in the response body, which must never ship). A forgotten password in the MVP means a SUPER_ADMIN-assisted reset outside this API. To be re-added later as NEW endpoints.

### DROP: POST /auth/register
Old staff registration with `hprId` / `hprVerified`. Replaced by `/auth/signup` for staff roles.

## 3. Access grants and access log

**Rule: every endpoint that reads or writes patient data returns `403 { "error": "NO_ACTIVE_GRANT" }` unless the caller has the right role AND an active AccessGrant for that CareID covering the needed scope.** Applies to DOCTOR, PHARMACY and CAREGIVER. ADMIN and SUPER_ADMIN never have access to patient data (no override in the MVP). A PATIENT reading their own data via `/patient/me/...` needs no grant. Every allowed and denied attempt is written to the patient's access log.

**AccessGrant object**
```json
{
  "id": "665f0000000000000000d001",
  "careId": "CID-7K3MQ9",
  "type": "CLINICAL",
  "scope": ["PROFILE", "PRESCRIPTIONS", "MEDICATIONS", "DOSES"],
  "status": "ACTIVE",
  "requester": { "userId": "665f...a002", "role": "DOCTOR", "name": "Dr Test Doctor", "facilityName": "Test Hospital" },
  "reason": "Follow-up visit",
  "requestedAt": "2026-10-23T10:30:00Z",
  "approvedAt": "2026-10-23T10:31:00Z",
  "expiresAt": "2026-10-24T10:31:00Z",
  "revokedAt": null
}
```
- `type`: `CLINICAL` (DOCTOR), `PHARMACY` (PHARMACY), `CAREGIVER` (created by an approved caregiver link, section 10).
- `scope` values: `PROFILE` (name, age, gender, allergies, conditions), `PRESCRIPTIONS`, `MEDICATIONS` (current medication profile and schedule), `DOSES` (dose log, adherence, marking doses taken).
- `status`: `PENDING`, `ACTIVE`, `REVOKED` (also what a denied request becomes), `EXPIRED`.
- Duration: patient picks on approval; defaults and limits are configurable (proposed: default 24 h, max 720 h for CLINICAL and PHARMACY). CAREGIVER grants have no expiry until revoked.

**Share code (NEW, needed for "request by share code")**
`POST /patient/me/share-codes` (PATIENT), request `{ "scope": ["PROFILE","PRESCRIPTIONS"], "durationHours": 24 }`, response `201 { "shareCode": "042913", "expiresAt": "2026-10-23T10:45:00Z" }`. The code is **six digits** (leading zeros allowed), single use, valid 15 minutes (configurable). Showing the code to a clinician is the patient's approval in advance. Because six digits can be guessed, each user is limited to 5 wrong codes per 15 minutes: further attempts get `429 TOO_MANY_ATTEMPTS` until the window passes. A code that is not found, malformed or already used is `404 INVALID_SHARE_CODE`; an expired one is `410 SHARE_CODE_EXPIRED` (once the database has removed it, `404`).

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `POST /access-grants` | DOCTOR, PHARMACY | by CareID: `{careId, scope:[...], reason, requestedDurationHours}`; or by share code: `{shareCode, reason}` | by CareID: `201` grant with `status:"PENDING"` and a `GRANT_REQUEST` alert goes to the patient. By share code: `201` grant already `ACTIVE`, code consumed, scope and duration taken from the code. Errors: 404 `INVALID_SHARE_CODE`, 410 `SHARE_CODE_EXPIRED`, 409 `GRANT_EXISTS` (body includes `grantId` of the existing PENDING or ACTIVE grant), 400 if both or neither of `careId` and `shareCode` are sent. For an unknown CareID the response is the same 201 PENDING (no way to probe which CareIDs exist) |
| NEW | `POST /access-grants/{id}/approve` | PATIENT (owner only) | `{durationHours?, scope?}` (scope may only narrow the request) | grant `ACTIVE`. 409 `NOT_PENDING`. 403 if not the owner |
| NEW | `POST /access-grants/{id}/revoke` | PATIENT (owner), or the requester for their own grant | none | grant `REVOKED`. On a `PENDING` grant this is the deny action. Takes effect immediately |
| NEW | `GET /access-grants?status=&careId=` | PATIENT, DOCTOR, PHARMACY, CAREGIVER | none | paged grants. PATIENT sees grants on their own data; DOCTOR/PHARMACY/CAREGIVER see grants they requested |
| NEW | `GET /patient/me/access-log?from=&to=&actorRole=&page=&size=` | PATIENT | none | paged log (below) |

**Access log entry**
```json
{
  "id": "665f0000000000000000e001",
  "at": "2026-10-23T10:40:00Z",
  "actor": { "userId": "665f...a002", "name": "Dr Test Doctor", "role": "DOCTOR", "facilityName": "Test Hospital" },
  "action": "READ",
  "resource": "PRESCRIPTION",
  "resourceId": "665f...f001",
  "grantId": "665f0000000000000000d001",
  "outcome": "ALLOWED"
}
```
`action`: `READ`, `WRITE`, `QR_SCAN`, `GRANT_REQUESTED`, `GRANT_APPROVED`, `GRANT_REVOKED`, `GRANT_EXPIRED`. `outcome`: `ALLOWED` or `DENIED` (denied attempts are logged too, with `grantId: null`). The log is append-only; revoking a grant or soft-deleting the account does not remove entries.

Behaviour of the log and the guard:
- Every patient-data call goes through one check (`AccessGuard`). It needs a suitable role **and** an active, unexpired grant for that CareID and scope, otherwise `403 NO_ACTIVE_GRANT`. A refusal never says why, so an unknown CareID and an ungranted one look identical.
- **Fail closed:** if an allowed access cannot be written to the log, it is refused with `500 AUDIT_UNAVAILABLE`. Failing to log a refusal or a grant change never changes the response.
- Entries hold ids and names only, never the patient data that was read. The application never updates or deletes them.
- Grants expire lazily: an ACTIVE grant past `expiresAt` always reads as `EXPIRED`, and the stored status and a `GRANT_EXPIRED` entry (actor role `SYSTEM`) are written the first time it is seen.
- `from` / `to` are ISO-8601 instants (`from` inclusive, `to` exclusive); a bad value is `400 VALIDATION_ERROR`. `actorRole` is case-insensitive.

**Endpoints covered by the grant rule** (the grant rule applies to the endpoints that are built; rows marked NOT BUILT in their own sections are listed here for when they return)

| Endpoint (details in later sections) | Scope needed |
|---|---|
| `GET /careid/{careId}`, `GET /patient/{careId}/summary` | `PROFILE` |
| `GET /patient/{careId}/history`, `GET /prescription/patient/{careId}`, `GET /prescription/for-patient/{careId}/active`, `GET /prescription/active-conflicts/{careId}` | `PRESCRIPTIONS` |
| `POST /pharmacy/dispense` | `PRESCRIPTIONS` (a PHARMACY grant) |
| `POST /prescription/issue`, `PUT /prescription/{id}`, `POST /prescription/{id}/discontinue`, `GET /prescription/{id}/history`, `GET /prescription/group/{groupId}`, group `recall`, `extend`, `dispense-full`, `DELETE /prescription/{id}`, `GET /pharmacy/prescription/{id}`, `POST /pharmacy/dispense` | `PRESCRIPTIONS` (writes need a `CLINICAL` or `PHARMACY` grant as fitting) |
| `GET /patient/{careId}/medications`, `GET /patient/{careId}/dose-schedule` | `MEDICATIONS` |
| `GET /patient/{careId}/dose-log`, `GET /patient/{careId}/adherence`, `POST /doses/{id}/taken` (as caregiver) | `DOSES` |
| `POST /checks/interaction` | `PROFILE` + `MEDICATIONS` |
| `GET /prescription/recent`, `GET /prescription/for-pharmacy/{pharmacyId}` (lists) | list returns only rows covered by one of the caller's active grants; other rows are omitted, not errored |

Not covered (not patient data): `/reception/checkin`, `/reception/queue/...` (hold only the CareID and the name typed at the desk), `/pharmacy/log/{pharmacyId}` (the pharmacy's own dispensing record), doctors, leave, drugs, stock, facilities.

**Pharmacy QR exception:** `GET /prescription/by-qr/{qrCode}` needs no prior grant. A valid QR is the patient's act of handing the prescription over, so the call creates an `ACTIVE` PHARMACY grant scoped to that one prescription group (short expiry, proposed 24 h), logged as `QR_SCAN`. Needs confirmation (Open Questions).

## 3b. Guest share (read-only view for someone without an account)

A patient can let a person with no Curo account (a relative, a visiting pharmacist) see part of their record, read-only, for a limited time. This is **not** an AccessGrant (section 3): it creates no grant, the guest has no JWT, and the secret opens exactly one endpoint, `POST /share/redeem`. It is also not the clinician share code of section 3 (`/patient/me/share-codes`), which only starts a grant for a signed-in DOCTOR or PHARMACY.

**Scope** is a non-empty subset of `["medicines", "allergies", "history"]` (repeats are ignored). `medicines` is the current medication profile (section 8), `allergies` the allergy list, `history` past prescriptions. A guest never sees phone, name, CareID, doses or the access log.

**Secrets (two ways in, both returned once at creation)**
- `token`: the main secret. 32 bytes from a cryptographically secure RNG, base64url (43 characters), meant for a link or QR. Valid until `expiresAt`.
- `code`: a short fallback to read out or type. 8 characters from the CareID alphabet (`ABCDEFGHJKLMNPQRSTUVWXYZ23456789`). Expires fast: valid for **15 minutes** from creation (`codeExpiresAt`, configurable), and never later than `expiresAt`.
- Only hashes of `token` and `code` are stored. Neither can be read back: not from `GET /patient/me/shares`, not from logs, not by Claude or SUPER_ADMIN. A lost secret means revoking and creating a new share.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW, **NOT BUILT** | `POST /patient/me/shares` | PATIENT | `{ "scope": ["medicines","allergies"], "durationHours": 24 }`. `durationHours` optional, default 24, max 168 | `201` share + `token`, `code`, `codeExpiresAt` (below). 400 `INVALID_SCOPE` if `scope` is missing, empty, or has a value outside the three; 400 `DURATION_TOO_LONG` if over 168; 400 `VALIDATION_ERROR` if `durationHours` is not a positive integer; 403 `CONSENT_WITHDRAWN` if the patient withdrew policy consent |
| NEW, **NOT BUILT** | `GET /patient/me/shares?status=&page=&size=` | PATIENT | none | paged list of the patient's shares, newest first (paged shape, section 1). `status` is `ACTIVE`, `EXPIRED` or `REVOKED` |
| NEW, **NOT BUILT** | `POST /patient/me/shares/{shareId}/revoke` | PATIENT (owner) | none | the share with `status:"REVOKED"` and `revokedAt`. Takes effect immediately, on the token and the code. Idempotent: revoking a revoked share returns 200 with the original `revokedAt`. 404 `SHARE_NOT_FOUND` for an unknown id or someone else's share |
| NEW, **NOT BUILT** | `POST /share/redeem` | **public, no JWT** (rate-limited) | exactly one of `{ "token": "..." }` or `{ "code": "..." }` | `200` guest view (below). Errors below. 400 `VALIDATION_ERROR` if both or neither is sent |

**Share object** (create, list, revoke)
```json
{
  "shareId": "665f0000000000000000h001",
  "careId": "CID-7K3MQ9",
  "scope": ["medicines", "allergies"],
  "status": "ACTIVE",
  "durationHours": 24,
  "createdAt": "2026-10-23T10:30:00Z",
  "expiresAt": "2026-10-24T10:30:00Z",
  "revokedAt": null,
  "viewCount": 2,
  "lastViewedAt": "2026-10-23T11:05:00Z"
}
```
`status` is `ACTIVE`, `EXPIRED` (read as such as soon as `expiresAt` passes) or `REVOKED`. `viewCount` is the number of successful redeems; `lastViewedAt` is `null` until the first. The create response adds:
```json
{
  "shareId": "665f0000000000000000h001",
  "...": "share fields as above",
  "token": "q3J8xv1mZ0o2k9n-Wc4tYb7RdLhU5aEeGfSiPjXvN6A",
  "code": "K7M2QX9D",
  "codeExpiresAt": "2026-10-23T10:45:00Z"
}
```

**`POST /share/redeem` success (200)**, only the fields in `scope` are present; a field outside the scope is absent, not empty
```json
{
  "readOnly": true,
  "scope": ["medicines", "allergies"],
  "expiresAt": "2026-10-24T10:30:00Z",
  "medicines": [
    { "drug": "Amoxicillin", "dose": "500 mg", "frequency": "THREE_TIMES_DAILY", "times": ["08:00","14:00","20:00"], "endDate": "2026-10-29" }
  ],
  "allergies": ["Penicillin"]
}
```
- `readOnly` is always `true`. `allergies` is always an array (`[]` when none are recorded). `history` items use the shape of `GET /patient/{careId}/history` (section 12) without `riskFlag`.
- The response carries `Cache-Control: no-store`. No CareID, name or phone is returned.
- Each success increments `viewCount`, sets `lastViewedAt`, and writes an access-log entry (below).

**Redeem errors** (body `{ "error": "CODE", "message": "..." }`)
| Code | HTTP | Meaning |
|---|---|---|
| `INVALID_CODE` | 404 | Token or code unknown, malformed, or a short `code` past `codeExpiresAt`. One answer for all of these, so it never tells a guesser which part was wrong |
| `SHARE_EXPIRED` | 410 | The share is past `expiresAt` |
| `SHARE_REVOKED` | 410 | The patient revoked it (also returned after the account is soft-deleted, since that revokes every share) |
| `TOO_MANY_ATTEMPTS` | 429 | Attempt limit reached. Body adds `retryAfterSeconds`, also sent as the `Retry-After` header |

```json
{ "error": "TOO_MANY_ATTEMPTS", "message": "Too many attempts. Try again later.", "retryAfterSeconds": 540 }
```
Checks run in this order: limits, lookup (`INVALID_CODE`), `SHARE_REVOKED`, `SHARE_EXPIRED`. The two 410 answers are only reachable with a correct secret.

**Attempt limits (proposed values, configurable)**
- **Per IP:** 10 failed redeems per 15 minutes. Further calls get 429 until the window passes, even with a correct secret.
- **Per code:** 5 failed lookups of the same submitted code string per 15 minutes, so a targeted retry of one guess is stopped. This also applies to `token` strings, though a 43-character random token cannot be guessed in practice.
- A success does not reset either counter. The window is fixed, not sliding. The limiter is in memory like the clinician share-code limiter (Open Questions), so it resets on restart and is per server instance.
- The short `code` space is 32^8 (about 1.1 x 10^12) and the code lives 15 minutes, which together with the limits above is what makes a short code acceptable. The `token` is the secret to prefer for anything shared in writing.

**Access log (section 3)**
Every successful redeem writes one entry to the patient's access log, which the patient reads at `GET /patient/me/access-log`. The entry has no `actor` (a guest is unknown), and carries `granteeType` and the `shareId`:
```json
{
  "id": "665f0000000000000000e002",
  "at": "2026-10-23T11:05:00Z",
  "actor": null,
  "granteeType": "GUEST",
  "action": "READ",
  "resource": "GUEST_SHARE",
  "resourceId": "665f0000000000000000h001",
  "shareId": "665f0000000000000000h001",
  "grantId": null,
  "outcome": "ALLOWED"
}
```
- `granteeType` is `USER` for every entry made by a signed-in actor (all existing entries) and `GUEST` for a redeem. `actor` is `null` only when `granteeType` is `GUEST`.
- Entries hold ids only, never the data that was shown, and no IP address or other guest detail.
- Fail closed, as in section 3: if the entry cannot be written, the redeem returns `500 AUDIT_UNAVAILABLE` and no data, and `viewCount` is not incremented.
- Failed redeems are not written to the patient's log: a guesser has no `shareId`, so they cannot be attributed to a share, and they would let a stranger fill the log. They are counted by the limiter only.

**Other rules**
- Creating, revoking and every successful redeem need no AccessGrant. A PATIENT acts on their own data only.
- Soft-deleting the account (section 12) revokes every share at once.
- `token` and `code` are never logged by the server, never put in the access log, and never returned after the create response.

## 4. Facilities (hospital, pharmacy)

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE, **NOT BUILT** | `POST /hospital/register` | public (rate-limited) | `{name,type,registrationNumber,gstin}` | Hospital `{id,name,type,registrationNumber,gstin,verificationStatus:"PENDING",verifiedAt,verifiedBy}` |
| KEEP, **NOT BUILT** | `GET /hospital/list` | **public** (needed by the doctor signup dropdown) | none | `Hospital[]` |
| CHANGE, **NOT BUILT** | `POST /hospital/{id}/verify` | SUPER_ADMIN | none | Hospital with `VERIFIED` |
| CHANGE, **NOT BUILT** | `POST /hospital/{id}/reject` | SUPER_ADMIN | none | Hospital with `REJECTED` |
| CHANGE, **NOT BUILT** | `POST /pharmacy-facility/register` | public (rate-limited) | `{name,type,drugLicenceNumber,gstin,state,address?}` (old key `drugLicenseNumber`) | PharmacyFacility (same status fields plus `state,address`) |
| CHANGE, **NOT BUILT** | `GET /pharmacy-facility/list` | any authed | none | `PharmacyFacility[]` |
| CHANGE, **NOT BUILT** | `POST /pharmacy-facility/{id}/verify` | SUPER_ADMIN | none | PharmacyFacility `VERIFIED` |
| CHANGE, **NOT BUILT** | `POST /pharmacy-facility/{id}/reject` | SUPER_ADMIN | none | PharmacyFacility `REJECTED` |

Rules kept: name unique (409), registration/licence number required (400), GSTIN must match `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` (400).
Change: `?adminUsername=` query param is removed; the verifying admin comes from the JWT (`verifiedBy`). The old code also required the SUPER_ADMIN role for these four routes; that is restored here.

## 5. Doctors, leave, profile

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| KEEP, **NOT BUILT** | `GET /staff/doctors/{hospitalId}` | DOCTOR, ADMIN | none | `[{id,name,specialization,available:true}]`, or when on leave `[{id,name,specialization,available:false,leaveUntil,reason,substituteId,substituteName}]` |
| CHANGE, **NOT BUILT** | `GET /doctor/profile` | DOCTOR | none | `{name,username,specialization,licenceNumber,stateCouncil,verificationStatus}`. Dropped: `hprId`, `hprVerified`, `hprDetails` (the old `hprDetails` was hardcoded fake data) |
| KEEP, **NOT BUILT** | `POST /doctor/leave/mark` | DOCTOR | `{leaveFrom:"2026-11-01",leaveUntil:"2026-11-03",reason:"...",substituteDoctorId}` | DoctorLeave `{id,doctorId,leaveFrom,leaveUntil,reason,substituteDoctorId,status:"ACTIVE"}`. 400 if substitute is self |
| KEEP, **NOT BUILT** | `GET /doctor/leave/mine` | DOCTOR | none | active DoctorLeave or `null` |
| KEEP, **NOT BUILT** | `POST /doctor/leave/{id}/end` | DOCTOR | none | DoctorLeave. 403 if not own leave, 404 unknown |
| KEEP, **NOT BUILT** | `GET /admin/leave/active` | ADMIN | none | `DoctorLeave[]` |
| DROP | `GET /doctor/leave/profile` | | | duplicate of `/doctor/profile` |

## 6. Check-in and queue (old "reception")

**Built (as of the reception check-in change).** The queue is a plain list of who is waiting. It carries no medical data and **grants nobody access to a patient record**: a doctor who opens a patient from the queue still gets `403 NO_ACTIVE_GRANT` until the patient has approved access (section 3).

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `POST /reception/check-in` | RECEPTION only (every other role 403) | `{ "careId": "CID-7K3MQ9" }` | `201 { careId, patientName, status:"WAITING", checkedInAt }`. 404 `PATIENT_NOT_FOUND` for an unknown CareID or one that is not a patient; 409 `ALREADY_IN_QUEUE` if the patient is already WAITING today. Writes an audit entry (`WRITE` of `QUEUE_ENTRY`) to the patient's access log |
| NEW | `GET /doctor/queue` | DOCTOR only | none | `{ counts:{total,done,pending}, entries:[{id,careId,patientName,status,checkedInAt}] }` for today (Indian calendar day), earliest first |
| NEW | `PATCH /doctor/queue/{id}/done` | DOCTOR only | none | the entry with `status:"DONE"`. Repeating it is fine. 404 `QUEUE_ENTRY_NOT_FOUND` |

`queue_entries` fields: `careId`, `patientName`, `status` (`WAITING` or `DONE`), `checkedInAt` (server UTC), `checkedInBy` (reception user id), `facilityId` (may be empty). **Every doctor sees every entry for now**: there is no facility scoping yet. A `PATIENT_NOT_FOUND` answer tells the front desk whether a CareID belongs to a patient, which is intended for check-in.

The older routes below are superseded by these and remain NOT BUILT.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE, **NOT BUILT** | `POST /reception/checkin` | RECEPTION (front desk) or DOCTOR | `{careId,hospitalId,doctorId,patientName,age,gender}` (old key `abha`) | Visit `{id,careId,hospitalId,doctorId,status:"WAITING",checkedInAt}`. 409 if already waiting. Not a grant-gated endpoint; the doctor still needs a grant to open the patient's data |
| KEEP, **NOT BUILT** | `GET /reception/queue/{hospitalId}?doctorId=` | DOCTOR, ADMIN | none | `Visit[]` (doctorId optional filter) |
| KEEP, **NOT BUILT** | `DELETE /reception/visit/{id}` | DOCTOR, ADMIN | none | 200. 404 unknown |

## 7. Prescriptions

Request/response keep old shapes with `patientAbha` renamed `careId`. All patient-data rows below are grant-gated (section 3).

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `POST /prescription/issue` | DOCTOR | `{careId,drugs:[{drug,dose,duration?,durationDays?,frequency?,times?}]}`. `doctorId` and `hospitalId` may be sent but are ignored: the doctor is the signed-in user | `201 {drugs:[Prescription],warnings:[{drug,severity,warning,explanation}]}`. `groupId` and `qrCode` return when the pharmacy side is ported. 400 `DRUGS_REQUIRED` (1 to 20 drugs), `DRUG_REQUIRED`, `DOSE_REQUIRED`, `INVALID_FREQUENCY`, `INVALID_SCHEDULE`, `INVALID_DURATION`; 403 `CONSENT_WITHDRAWN` if the patient withdrew policy consent. **Never blocks on an interaction or allergy:** the old 409 on drug conflict is removed; every finding comes back in `warnings` and the prescription is saved regardless (section 9). Each drug is checked against the patient's current drugs and the drugs earlier in the same request. Request problems (400) are reported before any grant check |
| KEEP, **NOT BUILT** | `GET /prescription/by-qr/{qrCode}` | PHARMACY, ADMIN | none | `{groupId,careId,status,issuedAt,drugs}`. 404 invalid; 409 already dispensed; 409 recalled. See QR exception in section 3 |
| KEEP, **NOT BUILT** | `GET /prescription/group/{groupId}` | DOCTOR, PHARMACY, ADMIN | none | `{groupId,careId,status,issuedAt,drugs}`. 404 |
| DROP | `POST /prescription/group/{groupId}/dispense` | | | superseded by `dispense-full`; not called by any client |
| KEEP, **NOT BUILT** | `POST /prescription/group/{groupId}/dispense-full` | PHARMACY, ADMIN | none (old body unused) | PrescriptionGroup with `status:"DISPENSED"`. 404; 409 not issuable; 409 no pharmacy routing |
| KEEP, **NOT BUILT** | `POST /prescription/group/{groupId}/recall` | DOCTOR | none | PrescriptionGroup `RECALLED`. 404; 409 not active; 403 after 30 minutes |
| KEEP, **NOT BUILT** | `POST /prescription/group/{groupId}/extend` | DOCTOR | `{extendByDays: 3, note: "..."}` | PrescriptionGroup with `extendedDays`, `extensionNote`. 400 if not positive; 409 wrong status |
| CHANGE, **NOT BUILT** | `GET /prescription/patient/{careId}` | DOCTOR, ADMIN | none | `Prescription[]` |
| CHANGE, **NOT BUILT** | `GET /prescription/recent` | DOCTOR, PHARMACY, ADMIN | none | `[{id,careId,status,issuedAt,extendedDays,drugs}]`, only rows under an active grant |
| CHANGE, **NOT BUILT** | `GET /prescription/for-pharmacy/{pharmacyId}` | DOCTOR, PHARMACY, ADMIN | none | `PrescriptionGroup[]`, only rows under an active grant |
| CHANGE, **NOT BUILT** | `GET /prescription/for-patient/{careId}/active` | DOCTOR, PHARMACY, ADMIN | none | `[{groupId,careId,status,issuedAt,drugs}]` |
| CHANGE, **NOT BUILT** | `GET /prescription/active-conflicts/{careId}` | DOCTOR | none | `[{drug,drugClass,doctorId,issuedAt,dose}]` |
| CHANGE, **NOT BUILT** | `DELETE /prescription/{id}` | DOCTOR, ADMIN | none | 200. 404. **409 `HAS_HISTORY` once the item has been dispensed or modified**; use discontinue instead. (ADMIN gets 403 `NO_ACTIVE_GRANT` in practice, see section 3) |
| NEW | `PUT /prescription/{id}` | DOCTOR (the issuing doctor) | `{dose?, duration?, durationDays?, frequency?, times?, reason}` (at least one change; `reason` required) | Prescription with the new version appended. 404 `PRESCRIPTION_NOT_FOUND`; 403 `FORBIDDEN` if not the issuing doctor; 409 `INVALID_STATE` if discontinued (or recalled, later); 400 `REASON_REQUIRED`, 400 `NO_CHANGE` if nothing differs; 409 `CONCURRENT_MODIFICATION` if two doctors change it at once. If already dispensed, future doses in the schedule are regenerated from the new version (section 8) and a `PRESCRIPTION_CHANGED` alert goes to the patient |
| NEW | `POST /prescription/{id}/discontinue` | DOCTOR (the issuing doctor) | `{reason}` | Prescription with `status:"DISCONTINUED"` and a final version appended. 404 `PRESCRIPTION_NOT_FOUND`; 403 `FORBIDDEN` if not the issuing doctor; 400 `REASON_REQUIRED`; 409 `INVALID_STATE` if already discontinued. Pending future doses are cancelled; past dose records stay. `PRESCRIPTION_CHANGED` alert to patient |
| NEW | `GET /prescription/{id}/history` | DOCTOR, PHARMACY | none | `{prescriptionId,careId,drug,currentVersion,versions:[...]}` (version object below) |

**Version object** (append-only, never edited or deleted):
```json
{
  "version": 2,
  "changeType": "MODIFIED",
  "dose": "500 mg",
  "duration": "7 days",
  "durationDays": 7,
  "frequency": "TWICE_DAILY",
  "times": ["08:00", "20:00"],
  "reason": "Dose reduced after renal review",
  "changedBy": { "userId": "665f...a002", "name": "Dr Test Doctor", "role": "DOCTOR" },
  "changedAt": "2026-10-24T09:00:00Z"
}
```
`changeType`: `ISSUED` (version 1), `MODIFIED`, `DISCONTINUED`.

**Object shapes**
- Prescription: `{id,careId,doctorId,drug,dose,duration,durationDays,frequency,times,status,issuedAt,prescriptionGroupId,currentVersion,versions}`. `versions` is only included on `GET /prescription/{id}/history`, the `PUT` and the `discontinue` responses; list endpoints return `currentVersion` only.
- PrescriptionGroup: `{id,careId,doctorId,hospitalId,pharmacyId,qrCode,status,issuedAt,extendedDays,extensionNote}`.
- Dropped field: `externalPrescriptionId` (existed only to link to the old external patient service).
- New optional drug fields: `frequency` (`ONCE_DAILY`, `TWICE_DAILY`, `THREE_TIMES_DAILY`, `FOUR_TIMES_DAILY`), `times` (`["HH:mm"]` in the patient's local time, overrides the default times for that frequency), `durationDays` (integer). The old free-text `duration` is still accepted and parsed ("7 days"); if it cannot be parsed and `durationDays` is absent, no dose schedule is generated for that drug.

**As built (Mongo `prescriptions` collection):**
- `status` is `ACTIVE`, `DISPENSED` (set by a pharmacy dispense; still a current medication, and the doctor can still modify or discontinue it) or `DISCONTINUED`. `RECALLED` and `ISSUED` are not used, and `prescriptionGroupId`, `groupId` and `qrCode`.
- Every prescription stores `versions[]`; the top-level fields always equal the latest version. A version records the change type, the values, `reason`, who (`changedBy` user id, name and role) and when (`changedAt`, set by the server).
- Frequency defaults when `times` is not sent: `ONCE_DAILY` 08:00; `TWICE_DAILY` 08:00 and 20:00; `THREE_TIMES_DAILY` 08:00, 14:00, 20:00; `FOUR_TIMES_DAILY` 06:00, 12:00, 18:00, 22:00. `times` needs a `frequency` and exactly as many different `HH:mm` values as the frequency has doses.
- `duration` text is read as `N day(s)`, `N week(s)` or `Nd`/`Nw` (1 to 365 days). Anything else is kept as written and gives no end date. An explicit `durationDays` always wins.
- A course is **current** until the end of its last day, counted in Indian time (`Asia/Kolkata`): a 7-day course issued on the 23rd is current through the 29th. A prescription with no duration stays current until discontinued. Only current prescriptions feed the interaction check and the medication profile.
- Prescriptions are looked up by id; a doctor with no grant who guesses an id gets `403 NO_ACTIVE_GRANT`, never the record.

## 8. Medication profile, dose schedule, dose log

Self-view uses `/patient/me/...` (no grant). The same data for others uses `/patient/{careId}/...` and is grant-gated: DOCTOR/PHARMACY with a matching grant, CAREGIVER with an active caregiver link (section 10).

**Schedule generation (as built):** doses are created server-side when a prescription is issued, from its times over its days, starting from the issue date (dispensing is not tracked yet, so it does not wait for a dispense). Times are local (Asia/Kolkata), stored as UTC instants; a time already past when the prescription is issued gets no dose. A prescription with no end date is scheduled for a rolling 30 days; "as needed" (no frequency) gets none. A modify that changes the times or the length cancels the doses still ahead and rebuilds them (a dose changed only in text leaves them alone); anything already taken keeps its slot. A discontinue cancels the doses still ahead; past doses, taken or missed, stay as history. Dose statuses: `UPCOMING`, `TAKEN`, `MISSED`. Only the server ever sets `MISSED`; no endpoint accepts a status.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `GET /patient/me/medications` | PATIENT | none | current medication profile (below) |
| NEW | `GET /patient/{careId}/medications` | DOCTOR, PHARMACY, CAREGIVER | none | same, gated (`MEDICATIONS`) |
| NEW | `GET /patient/me/doses/today` | PATIENT | none | `[Dose]` due today (Indian local day), earliest first |
| NEW, **NOT BUILT** | `GET /patient/me/dose-schedule?from=&to=` | PATIENT | none | `[Dose]` between the dates (default today to +7 days). **Not built yet**; use `doses/today` |
| NEW | `GET /patient/{careId}/dose-schedule?from=&to=` | DOCTOR, CAREGIVER | none | same, gated (`MEDICATIONS`) |
| NEW | `POST /doses/{id}/taken` | PATIENT (own dose). CAREGIVER is not built yet | `{takenAt?}` (default now; must not be in the future, else 400 `INVALID_TIME`; any other field, such as a status, is ignored) | The Dose with `status:"TAKEN"`, `takenAt` and `markedBy`. 404 `DOSE_NOT_FOUND` if unknown **or not yours**; 409 `DOSE_ALREADY_TAKEN` (the first record is kept; two taps at once give exactly one 200). `late:true` if taken more than the grace period (30 min) after it was due, or if it had already been marked `MISSED`; a missed dose can still be marked taken |
| NEW | `GET /patient/me/dose-log?from=&to=&status=&page=&size=` | PATIENT | none | paged `[Dose]` history, newest first. Without `status` it shows what has happened (`TAKEN` and `MISSED`); `status=UPCOMING` shows what is ahead |
| NEW, **NOT BUILT** | `GET /patient/{careId}/dose-log?...` | DOCTOR, CAREGIVER | none | same, gated (`DOSES`) |

**Current medication profile** (`GET /patient/me/medications` and `GET /patient/{careId}/medications`): exactly the fields the code returns today. A field that is null is left out of the JSON.
- Top level: `careId` (string), `asOf` (instant, when it was built), `medications` (array), `allergies` (array of strings; present for the patient themself, or for a clinician or caregiver whose grant also covers `PROFILE`, otherwise absent).
- Each item in `medications`: `prescriptionId` (string), `drug` (string), `dose` (string), `frequency` (`ONCE_DAILY`, `TWICE_DAILY`, `THREE_TIMES_DAILY` or `FOUR_TIMES_DAILY`; absent for "as needed"), `times` (array of `HH:mm`, local time), `startDate` (`YYYY-MM-DD`, the issue date in Indian time), `endDate` (`YYYY-MM-DD`, last day inclusive; absent when no duration was given), `status` (`ACTIVE` or `DISPENSED`), `currentVersion` (integer), `prescribedBy` (`{name, hospitalName}`; either may be absent).
```json
{
  "careId": "CID-7K3MQ9",
  "asOf": "2026-10-23T12:00:00Z",
  "medications": [
    {
      "prescriptionId": "665f...f001",
      "drug": "Amoxicillin",
      "dose": "500 mg",
      "frequency": "THREE_TIMES_DAILY",
      "times": ["08:00", "14:00", "20:00"],
      "startDate": "2026-10-23",
      "endDate": "2026-10-29",
      "status": "ACTIVE",
      "currentVersion": 1,
      "prescribedBy": { "name": "Dr Test Doctor", "hospitalName": "Test Hospital" }
    }
  ],
  "allergies": ["Penicillin"]
}
```
Only current medications are listed: not discontinued and not past their last day (dispensing is not tracked yet, so a course starts on its issue date). `startDate` is the issue date in Indian time; `endDate` is the last day, inclusive, and is absent when no duration was given. `allergies` is shown to the patient themself, and to a clinician or caregiver only if the grant also covers `PROFILE`; otherwise the field is left out. Reading it is logged as a `READ` of `MEDICATIONS`.

**Dose**
```json
{
  "id": "665f0000000000000000g001",
  "prescriptionId": "665f...f001",
  "drug": "Amoxicillin",
  "dose": "500 mg",
  "scheduledAt": "2026-10-23T14:00:00Z",
  "status": "TAKEN",
  "takenAt": "2026-10-23T14:12:00Z",
  "late": false,
  "markedBy": { "userId": "665f...a001", "role": "PATIENT" }
}
```

## 9. Interaction and allergy check (non-blocking)

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW, **NOT BUILT** | `POST /checks/interaction` | DOCTOR, PHARMACY (grant: `PROFILE` + `MEDICATIONS`) | `{careId, drug, dose?, durationDays?}` | `{severity, warning, explanation}` |
| CHANGE, **NOT BUILT** | `POST /ai/risk-score` | DOCTOR, ADMIN | old: `{drug,careId,allergies,duration}` | **Replaced by `/checks/interaction`.** Old response `{riskLevel,riskScore,blocked,awareCategory,explanation}` is not carried over; `blocked` is removed everywhere. The `awareCategory` / `isAntibiotic` flags move to the prescription detail objects (section 12) |

```json
// response
{
  "severity": "MODERATE",
  "warning": "Possible interaction with a current medication.",
  "explanation": "Ibuprofen with warfarin raises bleeding risk. Review the need for both or consider monitoring."
}
```
- `severity`: `NONE`, `LOW`, `MODERATE`, `HIGH`, `UNKNOWN`. `UNKNOWN` means the automated check could not run and the fallback table had no row; the `warning` says to verify manually. It is never reported as `NONE`.
- When `severity` is `NONE`: `warning` is `null` and `explanation` says no conflict was found.
- One object per call, at the worst severity found; `explanation` lists every finding (drug-drug against the current medication profile, and drug against recorded allergies).
- **No blocking field and no blocking behaviour.** The result is advice. Issue, modify and dispense never refuse because of it. `POST /prescription/issue` returns the same objects in `warnings[]`.
- Source: AWS Bedrock first. If Bedrock errors or times out (proposed 5 s), the server answers from the **fallback table** instead. The response header `X-Check-Source: AI` or `X-Check-Source: FALLBACK_TABLE` says which one answered.

**Fallback table** (server-side collection `interactionFallback`, seeded and maintained by the team; clinical content must be reviewed by a clinician before use)
| Field | Meaning |
|---|---|
| `kind` | `DRUG_DRUG` or `DRUG_ALLERGY` |
| `a` | drug name or class, lower case |
| `b` | second drug/class, or allergen class |
| `severity` | `LOW`, `MODERATE` or `HIGH` |
| `warning` | short text returned as `warning` |
| `explanation` | text returned as `explanation` |

Example rows (illustrative only): `DRUG_DRUG, warfarin, nsaid, HIGH`; `DRUG_ALLERGY, amoxicillin, penicillin, HIGH`.

## 10. Alerts, caregiver links, adherence, scheduler

### Alerts (as built)
Every user has an inbox of their own alerts. Patients, caregivers and doctors all receive some. Each alert is stored first, then pushed through one `NotificationSender` (see Delivery below); a delivery failure is recorded on the alert and never loses it.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `GET /alerts?unread=&page=&size=` | any signed-in user (their own alerts only) | none | paged `[{id,type,patientCareId,message,doseId,prescriptionId,channel,status,sentAt,readAt}]`, newest first. `unread=true` hides read ones. `readAt` is absent while unread |
| NEW | `POST /alerts/{id}/read` | the recipient | none | the alert with `readAt` set (the first read time is kept). Someone else's alert is `404 ALERT_NOT_FOUND` |

- `type`: `UPCOMING_DOSE` (reminder to the patient), `MISSED_DOSE`, `ADHERENCE` (escalation). The earlier `DOSE_REMINDER`, `GRANT_REQUEST`, `CAREGIVER_REQUEST` and `PRESCRIPTION_CHANGED` types are not built.
- `channel`: `EMAIL` when sent through Amazon SNS, `LOG` when SNS is not configured. `status`: `SENT` (handed to the delivery service) or `FAILED`.
- `message` is the inbox text and may name the drug and the patient. **What is sent outside the app never does**: the email only says that a dose is due or was missed, and for whom by CareID.

### Caregiver link (as built)
A patient adds a caregiver by the caregiver's CareID. This replaces the earlier request-and-approve flow: the patient is the one who consents, and the caregiver can step down at any time.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `POST /caregiver-links` | PATIENT | `{careId, relationship?}` (the caregiver's CareID) | `201 {id,patientCareId,patientName,caregiverCareId,caregiverName,relationship,status:"ACTIVE",createdAt}`. `404 CAREGIVER_NOT_FOUND` for an unknown CareID **or one that is not a caregiver account** (the same answer for both); `409 LINK_EXISTS` |
| CHANGE | `GET /caregiver-links` | PATIENT, CAREGIVER | none | `[link]`: a patient sees the caregivers they added, a caregiver the patients who added them |
| CHANGE | `POST /caregiver-links/{id}/revoke` | PATIENT or the CAREGIVER on that link | none | link `REVOKED`; the caregiver's access ends at once. Doing it again changes nothing. A link that is not yours is `404 LINK_NOT_FOUND` |
| DROP | `POST /caregiver-links/{id}/approve` | | | no approval step |

Adding a caregiver creates a standing `CAREGIVER` AccessGrant with **only the `MEDICATIONS` and `DOSES` scopes** and no end date. So a caregiver can read the patient's medication list and dose schedule (`GET /patient/{careId}/medications`, `GET /patient/{careId}/dose-schedule`) and receives their missed-dose alerts, but **cannot read prescriptions or the profile (allergies), and cannot prescribe**: issuing, modifying and discontinuing are refused for the CAREGIVER role (`403 FORBIDDEN_ROLE`) even if a grant were widened.

`GET /patient/{careId}/dose-schedule?from=&to=` (DOCTOR, CAREGIVER; grant `MEDICATIONS`; every read is audited as `DOSE_SCHEDULE`): doses in `[from, to)`; default is the next 7 days from the start of today (Indian time); at most 31 days, else `400 VALIDATION_ERROR`.

### Adherence report
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW, **NOT BUILT** | `GET /patient/me/adherence?from=&to=` | PATIENT | none | report (below) |
| NEW, **NOT BUILT** | `GET /patient/{careId}/adherence?from=&to=` | DOCTOR, CAREGIVER | none | same, gated (`DOSES`) |

```json
{
  "careId": "CID-7K3MQ9",
  "from": "2026-10-17", "to": "2026-10-23",
  "taken": 15, "missed": 3, "adherencePct": 83.3,
  "byDrug": [ { "drug": "Amoxicillin", "taken": 15, "missed": 3, "adherencePct": 83.3 } ],
  "byDay": [ { "date": "2026-10-23", "taken": 2, "missed": 1 } ]
}
```
`adherencePct = taken / (taken + missed) * 100`, one decimal. Doses still `UPCOMING` (in the future) are excluded. `null` when `taken + missed` is 0. Default range is the last 7 days.

### Scheduler endpoint (EventBridge only)
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `POST /internal/scheduler/dose-tick` | **`X-Internal-Key` header** (env `INTERNAL_API_KEY`), no JWT, never called by app clients | `{ "task": "ALL" }` where `task` is `REMINDERS`, `MISSED` or `ALL` (default `ALL`) | `{ "remindersSent": 2, "markedMissed": 3, "alertsCreated": 9 }` (`alertsCreated` counts reminders, missed-dose alerts and escalations). 401 `INVALID_KEY` for a missing or wrong key, and for every call if `INTERNAL_API_KEY` is not set; 400 for an unknown `task` |

- Called by an EventBridge rule (proposed `rate(5 minutes)`) through an API destination. The key lives in the EventBridge connection and in the server env, never in source or this file. Missing or wrong key: 401.
- `REMINDERS`: for every `UPCOMING` dose due within the next 10 minutes (`alerts.reminder-lead-minutes`) that has not had a reminder, send an `UPCOMING_DOSE` alert to the patient. With a tick every 5 minutes it lands 5 to 10 minutes before the dose. The reminder is claimed with one atomic update (`reminderSentAt`), so overlapping runs never remind twice. Only the patient is reminded.
- `MISSED`: every `UPCOMING` dose that is at least the grace period (default 30 minutes; set `MISSED_GRACE_MINUTES`, 1 to 1440) past `scheduledAt` becomes `MISSED`, in one atomic update that never touches a `TAKEN` dose. On each dose this call marks (and only those), `MISSED_DOSE` alerts go to the patient, to every caregiver with an active caregiver grant covering `DOSES`, and to the prescribing doctor **only while that doctor holds an active grant covering `DOSES`** (without consent they are not told). A dose the patient took first is never alerted.
- **Escalation:** when a miss makes it `alerts.escalation-consecutive-misses` (default 3) misses in a row for the same prescription, and again at every multiple (6, 9, ...), an `ADHERENCE` alert goes to the same recipients. A taken dose resets the count; other prescriptions do not count. Several misses found in one tick escalate once.
- Idempotent: a dose only moves `UPCOMING` to `MISSED` once, so retries, duplicate deliveries and overlapping runs change nothing the second time.
- The shared secret is compared in constant time and is never logged or returned.
- Ownership: the Spring side is Vasuki's; the EventBridge rule, API destination and key are Surya's (infra/).

### Delivery (as built)
All outside messages go through one `NotificationSender` interface, so alert logic never knows how they are sent.
- **Amazon SNS** is used when `ALERTS_SNS_TOPIC_ARN` is set (`AWS_REGION` optional). Messages are published to that topic, whose email subscribers receive them. Each message carries `recipientCareId` and `alertType` as message attributes so a subscription filter policy can route it to one person. AWS credentials come from the standard provider chain (environment variables or an instance role); none are read from this app's config. Creating and filtering the per-person email subscriptions is infrastructure work (Surya) and is not built.
- **Logging fallback** is used when SNS is not configured, or the SNS client cannot be created: the notification is written to the server log instead and the alert shows `channel: LOG`. The app starts and works either way.
- Failures log the exception type only, never the topic ARN or message details.

## 11. Pharmacy and drugs

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| KEEP, **NOT BUILT** | `GET /pharmacy/prescription/{id}` | PHARMACY, ADMIN | none | Prescription. 404. Grant-gated |
| CHANGE | `POST /pharmacy/dispense` | PHARMACY (own pharmacy only) | `{prescriptionId, pharmacyId?, drug?, quantity}`: `quantity` 1 to 10000 (units handed over, recorded in the log). `pharmacyId` is optional but if sent must be the signed-in pharmacist's own pharmacy, otherwise 403 `FORBIDDEN`. `drug`, if sent, must be the prescription's drug (400 `DRUG_MISMATCH`) | `{id,prescriptionId,pharmacyId,qtyDispensed,dispensedAt,status:"DISPENSED",drugName,careId}`. Marks the prescription `DISPENSED` (it stays a current medication) and writes a dispensing log entry. **No stock is checked or reduced.** **Grant-gated** (`PRESCRIPTIONS`, audited as a `WRITE` of `DISPENSE`): 403 `NO_ACTIVE_GRANT`. 404 `PRESCRIPTION_NOT_FOUND`; **409 `ALREADY_DISPENSED`** (one dispense per prescription; the database allows only one log row per prescription, so two simultaneous requests give exactly one 200); 409 `INVALID_STATE` if discontinued or its course has ended; 403 `NO_PHARMACY` / `PHARMACY_NOT_VERIFIED` for an account with no verified pharmacy |
| KEEP, **NOT BUILT** | `GET /pharmacy/log/{pharmacyId}` | PHARMACY, ADMIN | none | `{dispensedCount,rejectedCount,pendingCount,entries:[{id,qtyDispensed,dispensedAt,status,rejectReason,drug,careId}]}` |
| KEEP | `GET /drugs/list` | DOCTOR, ADMIN, PHARMACY | none | `[{id,name,drugClass,scheduleType}]` sorted by name. Backed by the Mongo `drug_master` collection (48 generic drugs seeded on startup). `scheduleType` is `RX` or `OTC` and indicative only. Reference data, so no grant is involved |


**Pharmacy records:** signing up as a PHARMACY also creates its pharmacy record (name, optional address, state) and returns it as `facilityId`. Dispensing acts as that pharmacy only. The record is switched on (`verified`) only when the licence matched the registry; a PENDING pharmacy cannot dispense until a SUPER_ADMIN approves it (not built yet). There is no stock, no coordinates and no nearby finder: those were removed.

## 12. Summary and patient app

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `GET /patient/{careId}/summary` | DOCTOR only (the old row also listed PHARMACY; not built). Grant-gated (`PROFILE`; 403 `NO_ACTIVE_GRANT` without one, and nothing about the patient is read) | none | the doctor Patient screen, below |
| CHANGE, **NOT BUILT** | `GET /patient/{careId}/history` | DOCTOR (old: public). Grant-gated (`PRESCRIPTIONS`) | none | `[{drug,dose,duration,issuedAt,status,drugClass,riskFlag}]` |
| KEEP | `GET /patient/me` | PATIENT | none | `{id,careId,name,phone,allergies,profileCompleted,...}` |
| KEEP | `PUT /patient/me` | PATIENT | any of `{allergies, chronicConditions, dateOfBirth, gender, bloodGroup}`; at least one | updated profile `{id,careId,name,phone,allergies,profileCompleted,dateOfBirth?,gender?,bloodGroup?,chronicConditions}`. A field not sent is left as it is; a list sent as `[]` clears it. Lists: trimmed, blanks and repeats dropped, at most 30 entries of 60 characters. `gender` is `MALE`, `FEMALE` or `OTHER`; `bloodGroup` is `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+` or `O-`; `dateOfBirth` is `YYYY-MM-DD`, in the past and after 1900. A bad value is `400 VALIDATION_ERROR` and changes nothing. `name` cannot be changed yet |
| CHANGE, **NOT BUILT** | `DELETE /patient/me` | PATIENT | none | **204, soft delete.** See below |
| KEEP, **NOT BUILT** | `POST /patient/me/push-token` | PATIENT | `{pushToken}` (old: query param) | 204 |
| KEEP, **NOT BUILT** | `GET /patient/me/prescriptions/detailed` | PATIENT | none | **NOT BUILT. Replaced by `GET /patient/me/medications` (section 8); do not call it.** Its old fields `drugName`, `dosage`, `durationDays`, `hospitalName`, `doctorName`, `createdAt`, `dispensedAt`, `visitId`, `awareCategory`, `isAntibiotic`, `allergyConflict` and `notRecommended` are not returned anywhere today; the only one that carries over is `currentVersion` |
| NEW | `POST /patient/me/share-codes` | PATIENT | see section 3 | see section 3 |
| NEW | `GET /patient/me/access-log` | PATIENT | see section 3 | see section 3 |

**Doctor Patient screen (`GET /patient/{careId}/summary`, built)**
```json
{
  "careId": "CID-DMPAT2",
  "name": "Demo Patient One",
  "ageYears": 46,
  "gender": "MALE",
  "bloodGroup": "B+",
  "allergies": ["Latex"],
  "chronicConditions": ["Type 2 diabetes"],
  "medications": [ { "prescriptionId": "665f...", "drug": "Warfarin", "dose": "5 mg", "status": "ACTIVE", "currentVersion": 1 } ],
  "prescriptions": [ { "id": "665f...", "drug": "Warfarin", "status": "ACTIVE", "issuedAt": "2026-10-01T05:00:00Z", "currentVersion": 1, "versions": [ { "version": 1, "changeType": "ISSUED" } ] } ]
}
```
Only a DOCTOR with an **active PROFILE grant** gets this; otherwise `403 NO_ACTIVE_GRANT` with no profile fields. The check is audited as a `READ` of `PROFILE`. `medications` (the current-medication list of section 8) is included only if the grant also covers `MEDICATIONS`, and `prescriptions` (every prescription, newest first, each with its `versions[]`) only if it covers `PRESCRIPTIONS`; a part the grant does not cover is left out. `ageYears`, `gender` and `bloodGroup` are left out when the patient has not set them; `allergies` and `chronicConditions` are `[]` when empty. The date of birth and phone are never returned. These profile fields are optional and set by the patient with `PUT /patient/me`.

**Soft delete (`DELETE /patient/me`)**
- Sets `status:"DELETED"` and `deletedAt`; does not remove the record. Idempotent: a second call returns 204.
- Takes effect immediately: existing JWTs stop working (status checked on every request), login returns 401 `INVALID_CREDENTIALS`, all AccessGrants and caregiver links are revoked, the dose schedule stops, and the CareID is excluded from lookups and grant requests (the same 403 `NO_ACTIVE_GRANT` a stranger would see).
- Retained: prescription records and the access log (clinical and audit records), keyed by CareID.
- Purged later by a scheduled job after a retention period (value open, below): name, phone, allergies, conditions, push token and credentials are cleared. CareID is never reissued.
- Old behaviour (hard delete, "permanently removed") is not carried over; the patient-app copy ("permanently removed") must change with it.

## 13. DROP: ABHA / ABDM (all of it) and password reset

Nothing below is carried into the new system. No ABHA number is collected, stored or verified. CareID replaces it.

| Where | Endpoint / item |
|---|---|
| hospital-backend | `GET /abdm/test-gateway-auth` |
| hospital-backend | `GET /abdm/test-cert` |
| hospital-backend | `POST /abdm/test-search` |
| hospital-backend | `POST /abdm/test-request-otp` |
| hospital-backend | `POST /abdm/test-verify-otp` |
| hospital-backend | `GET /abdm/verify/{abha}` (called by ReceptionPortal) |
| hospital-backend | `GET /abdm/verify-hpr/{hprId}` (called by Entry signup) |
| patient-app | `POST /abha/send-otp` |
| patient-app | `POST /abha/verify-otp` |
| signup field | `abha_id` |
| doctor fields | `hprId`, `hprVerified` (HPR is an ABDM registry) |
| config | `abdm.client-id`, `abdm.client-secret`; `AbhaRealService`; `AbdmController` |

Also dropped (not ABDM): `POST /auth/send-otp` and `POST /auth/reset-password` (password reset is out of the MVP), `HashGenerator`, mock-OTP-in-response behaviour, hardcoded `API_KEY`, hardcoded external service URL.

## 14. Notes on the old code (fixed by this contract)

- `/patient/**`, `/hospital/**`, `/pharmacy-facility/**` and `/abdm/**` were `permitAll`: patient data was readable without login. Now authenticated and grant-gated; only `GET /hospital/list` and the two facility `register` endpoints stay public.
- verify/reject took `adminUsername` from a query param (spoofable). Now from the JWT.
- Clients hardcoded `http://localhost:8080` in several components.
- The patient-app talked to a different service than the doctor site. Both now use this one backend.
- Interaction checks could block prescribing (`blocked`, 409). Now advisory only.
- Note: patient-app response shapes in sections 2, 8 and 12 are inferred from client code only; the service behind them was not read.

## 15. Open questions (need your decision)

1. **SUPER_ADMIN is not in the CLAUDE.md role list** (`DOCTOR, PATIENT, CAREGIVER, PHARMACY, ADMIN`). This contract keeps it as asked. CLAUDE.md is shared memory and needs the same edit; I have not changed it.
2. **Mock registry only.** `VERIFIED` currently means "matches the invented `licence_registry`", not a real council or drug-controller record. A real registry integration is future work behind the same `LicenceVerificationService` interface. The format rules in section 2 are placeholders.
3. **Pharmacy QR exception.** A QR scan creates a PHARMACY grant for that one prescription. Confirm, or require the patient to approve every pharmacy.
4. **Check-in is not gated.** The front desk (ADMIN) stores only the CareID and typed name; the doctor then requests a grant. Confirm, or auto-create a grant request at check-in.
5. **Share codes** (`POST /patient/me/share-codes`) are six digits as requested (the earlier `SHR-` format is gone). Confirm the 15-minute validity and the limit of 5 wrong codes per 15 minutes. The limiter is in memory, so it resets on restart and is per server instance.
6. **No break-glass.** ADMIN/SUPER_ADMIN and clinicians in an emergency cannot bypass a missing grant. Add an audited emergency override later?
7. **Retention window** for soft-deleted accounts before purge (proposed 30 days; a legal review is needed for prescription records).
8. **`GET /pharmacy-facility/list`** stays authenticated while `GET /hospital/list` is public. Intended?
9. **Scheduler timings** (reminder lead 10 min, missed grace 30 min, run every 5 min) and the EventBridge/API-destination setup are proposals for Surya.
10. **Consent storage:** one versioned object on the user (as drafted) vs a separate collection with history.
11. **Merge:** old patient-app service endpoints (`/patient/me*`, risk, prescription verify/dispense) are folded into this one backend. Confirm.
12. **"Wrong number" is read as a malformed licence number** (`INVALID_LICENCE_NUMBER`). A well-formed number that is simply not in the registry becomes `PENDING` rather than an error, otherwise nothing could ever be PENDING. Confirm, or make unlisted numbers a hard error.
13. **Pharmacy `name` is the licensee name on the drug licence**, since `name` is now the only name field. There is no separate pharmacist name in the MVP. Confirm.
14. **Deferred in code:** `facilityId`, `facilityId` and `address` on signup are in this contract but are not stored or validated until hospitals and pharmacy facilities are ported off the legacy JPA models.
15. **Pending requests never expire** and nothing yet notifies the patient of a new request (alerts are not built). Add an expiry and the `GRANT_REQUEST` alert?
16. **The drug and interaction tables need clinical review.** The 48 drugs, 32 interaction pairs and 4 allergy cross-reactions are a hardcoded starter set (`DrugSeed`, `InteractionTable`, `AllergyTable`). A pharmacist or doctor should check the severities and wording before this is used with real patients.
17. **A doctor with only a `PRESCRIPTIONS` grant still sees allergy warnings** when issuing, because an allergy conflict is safety-critical. The allergy list itself (`PROFILE` scope) is not shown to them.
18. **`POST /checks/interaction` is NOT BUILT.** The check runs on issue only; the standalone endpoint, the dose schedule, QR/group dispensing and the `PRESCRIPTION_CHANGED` alert come later. `/ai/risk-score` and the old `/prescription/*` pharmacy endpoints remain in the disabled legacy code under `/legacy/prescription`.
19. **Patient profile:** only allergies are stored. Name changes, conditions, date of birth and blood group (and so `ageYears` and `gender` on `/careid`) are still to do.
20. **Alerts: what is and is not built.** Upcoming-dose reminders, missed-dose alerts and escalation are built. Caregiver marking of doses, the doctor-facing adherence report and alerts for grant requests or prescription changes are not.
21. **Rolling 30-day horizon for open-ended prescriptions** is never topped up. A prescription with no end date runs out of doses after 30 days unless it is modified. A nightly top-up in the scheduler tick would fix it.
22. **Doses start at the issue date**, not the dispense date, because dispensing is not tracked yet.
23. **Pharmacy approval does not yet make a PENDING pharmacy visible.** Approving the account (SUPER_ADMIN review, not built) must also set the pharmacy record verified.
24. **`GET /pharmacy/log/{pharmacyId}` is not built.** Dispenses are recorded in the dispensing log, but there is no endpoint to read it.
25. **One dispense per prescription.** There is no refill concept yet, so a second fill is refused. Confirm, or add repeats.
26. **Dispensing in development** needs a PHARMACY account (sign one up, or use the demo pharmacist) and a patient grant that covers prescriptions.
27. **Email has no per-person routing yet.** SNS publishes to one topic; delivering to the right person needs an email subscription with a filter on `recipientCareId` for each user, and users have no email address on file. Until then outside notifications reach only whoever subscribed to the topic.
28. **A doctor is alerted only with a `DOSES` grant.** The prescribing doctor does not get missed-dose alerts without one. Confirm, or give prescribers a default dose-alert consent when a patient approves their first request.
29. **Caregivers are added by CareID with no confirmation from the caregiver**, and a "not found" answer reveals whether a CareID belongs to a caregiver account. Confirm, or add an accept step and rate limiting.
30. **Thresholds:** escalation after 3 consecutive misses per prescription and a 10-minute reminder lead are settings (`alerts.*`). Confirm the defaults.
