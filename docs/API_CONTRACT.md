# Curo API Contract (DRAFT for review)

Status: **DRAFT v0.2, not yet agreed.** Owner: Vasuki (backend/frontend). Consumers: frontend (Vasuki), patient-app (Sangami), infra (Surya).
Any change to this file ships in the same commit as the code change and is announced (CLAUDE.md).

Derived by reading the old apps in `_reference/` (hospital-backend, hospital-site, patient-app). All sample data below is fake.

**Changes in v0.2:** AccessGrants and access log (section 3); prescription versions, modify, discontinue and history (section 7); medication profile, dose schedule and dose log (section 8); interaction and allergy check, non-blocking (section 9); alerts, caregiver links, adherence and the scheduler endpoint (section 10); nearby pharmacies with coordinates (section 11); licence fields in signup (section 2); `/auth/consent` renamed `/auth/policy-consent`; one consent field name (`consent`); soft delete on `DELETE /patient/me`; `GET /hospital/list` stays public; RECEPTION folded into ADMIN, SUPER_ADMIN kept; password reset dropped from the MVP. Sections after 2 were renumbered.

## 1. Conventions

- Base URL comes from config/env (`API_BASE_URL`); never hardcode `localhost` or a hosted URL in clients.
- JSON in and out. Field names are **camelCase** (the old patient-app used snake_case against a separate Python service; that service is merged into this backend, see Open Questions).
- Auth: `Authorization: Bearer <JWT>`. JWT is signed with `JWT_SECRET` (env only).
- IDs: old system used SQL integer ids. New system uses MongoDB string ids (`"id": "665f..."`). Patients are identified externally by **CareID**, never by ABHA.
- Roles: `DOCTOR`, `PATIENT`, `CAREGIVER`, `PHARMACY`, `ADMIN`, `SUPER_ADMIN`. The old `RECEPTION` role is folded into `ADMIN`. `SUPER_ADMIN` (old role, kept) verifies facilities and staff accounts. Neither ADMIN nor SUPER_ADMIN can read patient data (section 3).
- Errors: the old backend returned bare strings as bodies. New contract: `{ "error": "CODE", "message": "human text" }` with the same HTTP statuses.
- Paged lists (new endpoints only): `?page=0&size=20` (size max 100), response `{ "page": 0, "size": 20, "total": 134, "items": [ ... ] }`. Older endpoints keep returning plain arrays.
- Timestamps are ISO-8601 UTC strings. Dates are `YYYY-MM-DD`.
- Status legend: **KEEP** (same behaviour), **CHANGE** (kept, shape or auth changes), **NEW**, **DROP** (not carried over).

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
  "fullName": "Dr Test Doctor",
  "phone": "9000000002",
  "password": "min 8 chars",
  "facilityId": "665f0000000000000000b001",
  "licenceNumber": "TN-12345",
  "stateCouncil": "Tamil Nadu Medical Council",
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response
{
  "id": "665f0000000000000000a002",
  "careId": "CID-4HN8XD",
  "fullName": "Dr Test Doctor",
  "role": "DOCTOR",
  "status": "VERIFIED",
  "statusReason": null,
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:20:00Z" }
}
```

**PHARMACY** (licence fields required when `facilityId` is omitted, because signup then creates the pharmacy facility; if `facilityId` of an existing VERIFIED facility is sent, only the account fields are needed)
```json
// request
{
  "role": "PHARMACY",
  "fullName": "Test Pharmacist",
  "name": "Test Pharmacy",
  "phone": "9000000003",
  "password": "min 8 chars",
  "drugLicenceNumber": "TN/DL/12345",
  "gstin": "33AAAAA0000A1Z5",
  "state": "TN",
  "address": "1 Test Street, Chennai",
  "lat": 13.0827,
  "lng": 80.2707,
  "consent": { "accepted": true, "policyVersion": "2026-10-01" }
}
// 201 response
{
  "id": "665f0000000000000000a003",
  "careId": "CID-9PW2TC",
  "fullName": "Test Pharmacist",
  "role": "PHARMACY",
  "facilityId": "665f0000000000000000c001",
  "status": "PENDING",
  "statusReason": "LICENCE_FORMAT_UNRECOGNISED",
  "consent": { "accepted": true, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T10:25:00Z" }
}
```
`lat`/`lng` are optional; if omitted the server geocodes `address` with AWS Location Service. If both fail the facility is saved without a location and does not appear in `/pharmacies/nearby` until fixed.

**Status rules (DOCTOR and PHARMACY only; the `status` field is absent for PATIENT and CAREGIVER)**
- `VERIFIED`: all licence fields present, formats valid, not a duplicate, state/council recognised. **This means format-checked and unique, not confirmed against any government registry** (no ABDM/HPR call is made).
- `PENDING`: fields present but not machine-verifiable (for example an unrecognised council format). A SUPER_ADMIN reviews it (see below). A PENDING user cannot log in (403 `ACCOUNT_PENDING`).
- Hard failures return 400, not PENDING: missing licence field (`LICENCE_FIELDS_REQUIRED`), malformed GSTIN (`INVALID_GSTIN`, regex in section 4), unknown `state`.
- Format rules are placeholders to confirm: `licenceNumber` `^[A-Za-z0-9/\-]{4,25}$`; `drugLicenceNumber` `^[A-Za-z0-9/\-]{5,30}$`; `state` is an ISO 3166-2:IN code (`TN`, `KA`, ...); `stateCouncil` must be on a server-side list.
- Duplicate licence (same licence number and council or state): 409 `LICENCE_TAKEN`.

Other errors: 400 `CONSENT_REQUIRED`, 400 `WEAK_PASSWORD`, 409 `PHONE_TAKEN` (a soft-deleted account's phone stays taken until its data is purged). The consent timestamp (`acceptedAt`) is set server-side; clients never send timestamps.
`name` is used for PATIENT/CAREGIVER and for the pharmacy facility name; `fullName` is used for DOCTOR and the pharmacist (matches the old `Staff.fullName`). Responses always echo the field that was sent.

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
JWT claims: `sub` (user id), `role`, `careId`, `facilityId` (nullable). Errors: 401 `INVALID_CREDENTIALS` (same message for unknown user, wrong password and deleted account), 403 `ACCOUNT_PENDING`.
Old response was `{token, username, role, fullName, hospitalName}`; old claims were `role, facilityId, facilityName, specialization, staffId`.

### NEW: GET /auth/me (any role)
Returns the `user` object above plus `consent`, and `status` for DOCTOR/PHARMACY.

### NEW: POST /auth/policy-consent (any role)  (renamed from /auth/consent)
Records withdrawal or re-acceptance of the privacy policy. Not the same thing as an AccessGrant (section 3), which controls who may see which patient's data.
```json
// request
{ "consent": { "accepted": false, "policyVersion": "2026-10-01" } }
// 200 response
{ "consent": { "accepted": false, "policyVersion": "2026-10-01", "acceptedAt": "2026-10-23T11:00:00Z" } }
```
Withdrawal blocks new prescriptions for that CareID.

### NEW: GET /careid/{careId} (DOCTOR, PHARMACY; active grant required)
Gated (section 3, scope `PROFILE`); 403 `NO_ACTIVE_GRANT` otherwise. Returns `{ "careId", "name", "ageYears", "gender", "consent": { "accepted": true } }`. 404 if unknown (only after the grant check, so an unknown CareID and an ungranted one are indistinguishable: both return 403 `NO_ACTIVE_GRANT`). Never returns phone or full record. To get access to a CareID, start with `POST /access-grants`.

### NEW: Staff account review (SUPER_ADMIN)
| Method + path | Request | Response |
|---|---|---|
| `GET /admin/users?status=PENDING` | none | paged `[{id,careId,role,fullName,facilityId,licenceNumber,stateCouncil,drugLicenceNumber,gstin,state,status,statusReason,createdAt}]` |
| `POST /admin/users/{id}/verify` | none | user with `status:"VERIFIED"` |
| `POST /admin/users/{id}/reject` | `{reason}` | user with `status:"REJECTED"` |

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
`POST /patient/me/share-codes` (PATIENT), request `{ "scope": ["PROFILE","PRESCRIPTIONS"], "durationHours": 24 }`, response `{ "shareCode": "SHR-4TQ7ZK", "expiresAt": "2026-10-23T10:45:00Z" }`. Format `SHR-` plus 6 characters from the CareID alphabet. Single use, valid 15 minutes (configurable). Showing the code to a clinician is the patient's approval in advance.

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

**Endpoints covered by the grant rule**

| Endpoint (details in later sections) | Scope needed |
|---|---|
| `GET /careid/{careId}`, `GET /patient/{careId}/summary` | `PROFILE` |
| `GET /patient/{careId}/history`, `GET /prescription/patient/{careId}`, `GET /prescription/for-patient/{careId}/active`, `GET /prescription/active-conflicts/{careId}` | `PRESCRIPTIONS` |
| `POST /prescription/issue`, `PUT /prescription/{id}`, `POST /prescription/{id}/discontinue`, `GET /prescription/{id}/history`, `GET /prescription/group/{groupId}`, group `recall`, `extend`, `dispense-full`, `DELETE /prescription/{id}`, `GET /pharmacy/prescription/{id}`, `POST /pharmacy/dispense` | `PRESCRIPTIONS` (writes need a `CLINICAL` or `PHARMACY` grant as fitting) |
| `GET /patient/{careId}/medications`, `GET /patient/{careId}/dose-schedule` | `MEDICATIONS` |
| `GET /patient/{careId}/dose-log`, `GET /patient/{careId}/adherence`, `POST /doses/{id}/taken` (as caregiver) | `DOSES` |
| `POST /checks/interaction` | `PROFILE` + `MEDICATIONS` |
| `GET /prescription/recent`, `GET /prescription/for-pharmacy/{pharmacyId}` (lists) | list returns only rows covered by one of the caller's active grants; other rows are omitted, not errored |

Not covered (not patient data): `/reception/checkin`, `/reception/queue/...` (hold only the CareID and the name typed at the desk), `/pharmacy/log/{pharmacyId}` (the pharmacy's own dispensing record), doctors, leave, drugs, stock, facilities, `/pharmacies/nearby`.

**Pharmacy QR exception:** `GET /prescription/by-qr/{qrCode}` needs no prior grant. A valid QR is the patient's act of handing the prescription over, so the call creates an `ACTIVE` PHARMACY grant scoped to that one prescription group (short expiry, proposed 24 h), logged as `QR_SCAN`. Needs confirmation (Open Questions).

## 4. Facilities (hospital, pharmacy)

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `POST /hospital/register` | public (rate-limited) | `{name,type,registrationNumber,gstin}` | Hospital `{id,name,type,registrationNumber,gstin,verificationStatus:"PENDING",verifiedAt,verifiedBy}` |
| KEEP | `GET /hospital/list` | **public** (needed by the doctor signup dropdown) | none | `Hospital[]` |
| CHANGE | `POST /hospital/{id}/verify` | SUPER_ADMIN | none | Hospital with `VERIFIED` |
| CHANGE | `POST /hospital/{id}/reject` | SUPER_ADMIN | none | Hospital with `REJECTED` |
| CHANGE | `POST /pharmacy-facility/register` | public (rate-limited) | `{name,type,drugLicenceNumber,gstin,state,address,lat?,lng?}` (old key `drugLicenseNumber`) | PharmacyFacility (same status fields plus `state,address,location:{lat,lng}`) |
| CHANGE | `GET /pharmacy-facility/list` | any authed | none | `PharmacyFacility[]` |
| CHANGE | `POST /pharmacy-facility/{id}/verify` | SUPER_ADMIN | none | PharmacyFacility `VERIFIED` |
| CHANGE | `POST /pharmacy-facility/{id}/reject` | SUPER_ADMIN | none | PharmacyFacility `REJECTED` |

Rules kept: name unique (409), registration/licence number required (400), GSTIN must match `^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` (400).
Change: `?adminUsername=` query param is removed; the verifying admin comes from the JWT (`verifiedBy`). The old code also required the SUPER_ADMIN role for these four routes; that is restored here.

## 5. Doctors, leave, profile

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| KEEP | `GET /staff/doctors/{hospitalId}` | DOCTOR, ADMIN | none | `[{id,fullName,specialization,available:true}]`, or when on leave `[{id,fullName,specialization,available:false,leaveUntil,reason,substituteId,substituteName}]` |
| CHANGE | `GET /doctor/profile` | DOCTOR | none | `{fullName,username,specialization,licenceNumber,stateCouncil,status}`. Dropped: `hprId`, `hprVerified`, `hprDetails` (the old `hprDetails` was hardcoded fake data) |
| KEEP | `POST /doctor/leave/mark` | DOCTOR | `{leaveFrom:"2026-11-01",leaveUntil:"2026-11-03",reason:"...",substituteDoctorId}` | DoctorLeave `{id,doctorId,leaveFrom,leaveUntil,reason,substituteDoctorId,status:"ACTIVE"}`. 400 if substitute is self |
| KEEP | `GET /doctor/leave/mine` | DOCTOR | none | active DoctorLeave or `null` |
| KEEP | `POST /doctor/leave/{id}/end` | DOCTOR | none | DoctorLeave. 403 if not own leave, 404 unknown |
| KEEP | `GET /admin/leave/active` | ADMIN | none | `DoctorLeave[]` |
| DROP | `GET /doctor/leave/profile` | | | duplicate of `/doctor/profile` |

## 6. Check-in and queue (old "reception")

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `POST /reception/checkin` | ADMIN (front desk, replaces RECEPTION) or DOCTOR | `{careId,hospitalId,doctorId,patientName,age,gender}` (old key `abha`) | Visit `{id,careId,hospitalId,doctorId,status:"WAITING",checkedInAt}`. 409 if already waiting. Not a grant-gated endpoint; the doctor still needs a grant to open the patient's data |
| KEEP | `GET /reception/queue/{hospitalId}?doctorId=` | DOCTOR, ADMIN | none | `Visit[]` (doctorId optional filter) |
| KEEP | `DELETE /reception/visit/{id}` | DOCTOR, ADMIN | none | 200. 404 unknown |

## 7. Prescriptions

Request/response keep old shapes with `patientAbha` renamed `careId`. All patient-data rows below are grant-gated (section 3).

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `POST /prescription/issue` | DOCTOR | `{careId,doctorId,hospitalId,drugs:[{drug,dose,duration,frequency?,durationDays?,times?}]}` (new optional dose-schedule fields, section 8) | `{groupId,qrCode,drugs:[Prescription],warnings:[{drug,severity,warning,explanation}]}`. 400 no drugs. **Never blocks on an interaction or allergy:** the old 409 on drug conflict is removed; findings come back in `warnings` (section 9). Old code also pushed each drug to the external patient service (now internal) |
| KEEP | `GET /prescription/by-qr/{qrCode}` | PHARMACY, ADMIN | none | `{groupId,careId,status,issuedAt,drugs}`. 404 invalid; 409 already dispensed; 409 recalled. See QR exception in section 3 |
| KEEP | `GET /prescription/group/{groupId}` | DOCTOR, PHARMACY, ADMIN | none | `{groupId,careId,status,issuedAt,drugs}`. 404 |
| DROP | `POST /prescription/group/{groupId}/dispense` | | | superseded by `dispense-full`; not called by any client |
| KEEP | `POST /prescription/group/{groupId}/dispense-full` | PHARMACY, ADMIN | none (old body unused) | PrescriptionGroup with `status:"DISPENSED"`. 404; 409 not issuable; 409 no pharmacy routing |
| KEEP | `POST /prescription/group/{groupId}/recall` | DOCTOR | none | PrescriptionGroup `RECALLED`. 404; 409 not active; 403 after 30 minutes |
| KEEP | `POST /prescription/group/{groupId}/extend` | DOCTOR | `{extendByDays: 3, note: "..."}` | PrescriptionGroup with `extendedDays`, `extensionNote`. 400 if not positive; 409 wrong status |
| CHANGE | `GET /prescription/patient/{careId}` | DOCTOR, ADMIN | none | `Prescription[]` |
| CHANGE | `GET /prescription/recent` | DOCTOR, PHARMACY, ADMIN | none | `[{id,careId,status,issuedAt,extendedDays,drugs}]`, only rows under an active grant |
| CHANGE | `GET /prescription/for-pharmacy/{pharmacyId}` | DOCTOR, PHARMACY, ADMIN | none | `PrescriptionGroup[]`, only rows under an active grant |
| CHANGE | `GET /prescription/for-patient/{careId}/active` | DOCTOR, PHARMACY, ADMIN | none | `[{groupId,careId,status,issuedAt,drugs}]` |
| CHANGE | `GET /prescription/active-conflicts/{careId}` | DOCTOR | none | `[{drug,drugClass,doctorId,issuedAt,dose}]` |
| CHANGE | `DELETE /prescription/{id}` | DOCTOR, ADMIN | none | 200. 404. **409 `HAS_HISTORY` once the item has been dispensed or modified**; use discontinue instead. (ADMIN gets 403 `NO_ACTIVE_GRANT` in practice, see section 3) |
| NEW | `PUT /prescription/{id}` | DOCTOR (the issuing doctor) | `{dose?, duration?, durationDays?, frequency?, times?, reason}` (at least one change; `reason` required) | Prescription with the new version appended. 404; 403 not the issuing doctor; 409 `INVALID_STATE` if discontinued or recalled. If already dispensed, future doses in the schedule are regenerated from the new version (section 8) and a `PRESCRIPTION_CHANGED` alert goes to the patient |
| NEW | `POST /prescription/{id}/discontinue` | DOCTOR (the issuing doctor) | `{reason}` | Prescription with `status:"DISCONTINUED"` and a final version appended. 409 `INVALID_STATE` if already discontinued. Pending future doses are cancelled; past dose records stay. `PRESCRIPTION_CHANGED` alert to patient |
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

## 8. Medication profile, dose schedule, dose log

Self-view uses `/patient/me/...` (no grant). The same data for others uses `/patient/{careId}/...` and is grant-gated: DOCTOR/PHARMACY with a matching grant, CAREGIVER with an active caregiver link (section 10).

**Schedule generation:** doses are generated server-side when a prescription group is `DISPENSED`, from `frequency` / `times` over `durationDays`, starting at dispense time. A modify regenerates unfulfilled future doses; a discontinue cancels them. Dose statuses: `PENDING`, `TAKEN`, `MISSED`.

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `GET /patient/me/medications` | PATIENT | none | current medication profile (below) |
| NEW | `GET /patient/{careId}/medications` | DOCTOR, PHARMACY, CAREGIVER | none | same, gated (`MEDICATIONS`) |
| NEW | `GET /patient/me/dose-schedule?from=&to=` | PATIENT | none | `[Dose]` between the dates (default today to +7 days) |
| NEW | `GET /patient/{careId}/dose-schedule?from=&to=` | DOCTOR, CAREGIVER | none | same, gated (`MEDICATIONS`) |
| NEW | `POST /doses/{id}/taken` | PATIENT (own dose), CAREGIVER (active link, `DOSES`) | `{takenAt?}` (default now; must not be in the future, else 400) | Dose with `status:"TAKEN"`. 404 unknown; 403 `NO_ACTIVE_GRANT` for a caregiver without a link; 409 `DOSE_ALREADY_TAKEN`. A `MISSED` dose can still be marked taken and is then flagged `late:true` |
| NEW | `GET /patient/me/dose-log?from=&to=&status=&page=&size=` | PATIENT | none | paged `[Dose]` history, newest first |
| NEW | `GET /patient/{careId}/dose-log?...` | DOCTOR, CAREGIVER | none | same, gated (`DOSES`) |

**Current medication profile**
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
Only active (dispensed, not discontinued, not past `endDate`) medications are listed.

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
| NEW | `POST /checks/interaction` | DOCTOR, PHARMACY (grant: `PROFILE` + `MEDICATIONS`) | `{careId, drug, dose?, durationDays?}` | `{severity, warning, explanation}` |
| CHANGE | `POST /ai/risk-score` | DOCTOR, ADMIN | old: `{drug,careId,allergies,duration}` | **Replaced by `/checks/interaction`.** Old response `{riskLevel,riskScore,blocked,awareCategory,explanation}` is not carried over; `blocked` is removed everywhere. The `awareCategory` / `isAntibiotic` flags move to the prescription detail objects (section 12) |

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

### Alerts
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `GET /alerts?unread=&page=&size=` | PATIENT, CAREGIVER | none | paged `[{id,type,careId,message,relatedId,createdAt,readAt}]`. CAREGIVER sees alerts for patients with an active link |
| NEW | `POST /alerts/{id}/read` | PATIENT, CAREGIVER | none | alert with `readAt` set |

`type`: `DOSE_REMINDER`, `MISSED_DOSE`, `GRANT_REQUEST`, `CAREGIVER_REQUEST`, `PRESCRIPTION_CHANGED`. Alerts are stored and also pushed through the patient's registered push token (SNS / Expo). No clinician alerts in the MVP.

### Caregiver link
A caregiver link is a request to hold a standing `CAREGIVER` AccessGrant on one patient.
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `POST /caregiver-links` | CAREGIVER | `{careId}` or `{shareCode}` (same share code mechanism, section 3), plus optional `relationship` | link `{id,careId,caregiverId,relationship,status:"PENDING"}`. With a share code, created `ACTIVE`. A `CAREGIVER_REQUEST` alert goes to the patient |
| NEW | `POST /caregiver-links/{id}/approve` | PATIENT (owner) | `{scope?}` (default `MEDICATIONS`, `DOSES`; may include `PROFILE`) | link `ACTIVE` plus its `grantId`; the AccessGrant (`type:"CAREGIVER"`, no expiry) is created |
| NEW | `POST /caregiver-links/{id}/revoke` | PATIENT (owner) or the CAREGIVER | none | link `REVOKED`; the grant is revoked at the same time |
| NEW | `GET /caregiver-links` | PATIENT, CAREGIVER | none | `[link]`: the patient's links, or the caregiver's links |

### Adherence report
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `GET /patient/me/adherence?from=&to=` | PATIENT | none | report (below) |
| NEW | `GET /patient/{careId}/adherence?from=&to=` | DOCTOR, CAREGIVER | none | same, gated (`DOSES`) |

```json
{
  "careId": "CID-7K3MQ9",
  "from": "2026-10-17", "to": "2026-10-23",
  "taken": 15, "missed": 3, "adherencePct": 83.3,
  "byDrug": [ { "drug": "Amoxicillin", "taken": 15, "missed": 3, "adherencePct": 83.3 } ],
  "byDay": [ { "date": "2026-10-23", "taken": 2, "missed": 1 } ]
}
```
`adherencePct = taken / (taken + missed) * 100`, one decimal. Doses still `PENDING` (in the future) are excluded. `null` when `taken + missed` is 0. Default range is the last 7 days.

### Scheduler endpoint (EventBridge only)
| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| NEW | `POST /internal/scheduler/dose-tick` | **`X-Internal-Key` header** (env `INTERNAL_API_KEY`), no JWT, never called by app clients | `{ "task": "ALL" }` where `task` is `REMINDERS`, `MISSED` or `ALL` (default `ALL`) | `{ "remindersSent": 4, "markedMissed": 2, "alertsCreated": 6 }` |

- Called by an EventBridge rule (proposed `rate(5 minutes)`) through an API destination. The key lives in the EventBridge connection and in the server env, never in source or this file. Missing or wrong key: 401.
- `REMINDERS`: for every `PENDING` dose due within the reminder lead time (proposed 10 min) with no reminder yet, create a `DOSE_REMINDER` alert and push to the patient.
- `MISSED`: for every `PENDING` dose more than the grace period past `scheduledAt` (proposed 30 min), set `status:"MISSED"`, create a `MISSED_DOSE` alert for the patient and for each caregiver with an active link.
- Idempotent: each dose records `reminderSentAt` and a status transition, so retries, duplicate deliveries and overlapping runs do not send twice.
- Ownership: the Spring side is Vasuki's; the EventBridge rule, API destination and key are Surya's (infra/).

## 11. Pharmacy and drugs

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| KEEP | `GET /pharmacy/stock/{pharmacyId}` | PHARMACY, ADMIN | none | `[{id,pharmacyId,drug,quantity}]` |
| KEEP | `GET /pharmacy/prescription/{id}` | PHARMACY, ADMIN | none | Prescription. 404. Grant-gated |
| KEEP | `POST /pharmacy/dispense` | PHARMACY, ADMIN | `{prescriptionId,pharmacyId,drug,quantity}` | DispensingLog `{id,prescriptionId,pharmacyId,qtyDispensed,dispensedAt,status,drugName,careId,rejectReason}`. 404 not stocked; 409 insufficient stock. Grant-gated |
| KEEP | `GET /pharmacy/log/{pharmacyId}` | PHARMACY, ADMIN | none | `{dispensedCount,rejectedCount,pendingCount,entries:[{id,qtyDispensed,dispensedAt,status,rejectReason,drug,careId}]}` |
| KEEP | `GET /drugs/list` | DOCTOR, ADMIN, PHARMACY | none | `[{id,name,drugClass,scheduleType}]` |
| NEW | `GET /pharmacies/nearby?lat=&lng=&drug=&radiusKm=&limit=` | any authenticated role | query only. `lat` (-90 to 90) and `lng` (-180 to 180) required; `drug` optional; `radiusKm` default 5, max 50; `limit` default 20, max 50 | `[{id,name,address,state,location:{lat,lng},distanceKm,inStock}]` nearest first. 400 `INVALID_COORDINATES` |

Nearby rules: only `VERIFIED` pharmacy facilities with a stored location are returned. `drug` matches stock by name, case-insensitive. When `drug` is given, `inStock` is `true` and only facilities with quantity above zero are returned; when absent, `inStock` is `null`. Quantities are never exposed. Facilities need a 2dsphere index on `location`; the index and the coordinates (from signup, `pharmacy-facility/register`, or AWS Location Service geocoding of `address`) are the backend's job.

## 12. Summary and patient app

| Status | Method + path | Auth | Request | Response |
|---|---|---|---|---|
| CHANGE | `GET /patient/{careId}/summary` | DOCTOR, PHARMACY (old: public). Grant-gated (`PROFILE`) | none | `{careId,name,allergies,conditions}`. Old fallback `{offline:true}` removed; use 503 |
| CHANGE | `GET /patient/{careId}/history` | DOCTOR (old: public). Grant-gated (`PRESCRIPTIONS`) | none | `[{drug,dose,duration,issuedAt,status,drugClass,riskFlag}]` |
| KEEP | `GET /patient/me` | PATIENT | none | `{id,careId,name,phone,allergies,profileCompleted,...}` |
| KEEP | `PUT /patient/me` | PATIENT | `{name,allergies,conditions,dob,bloodGroup}` | updated profile. (Old app called this to complete profile; exact body inferred) |
| CHANGE | `DELETE /patient/me` | PATIENT | none | **204, soft delete.** See below |
| KEEP | `POST /patient/me/push-token` | PATIENT | `{pushToken}` (old: query param) | 204 |
| KEEP | `GET /patient/me/prescriptions/detailed` | PATIENT | none | `[{id,drugName,dosage,durationDays,status,hospitalName,doctorName,createdAt,dispensedAt,visitId,awareCategory,isAntibiotic,allergyConflict,notRecommended,currentVersion}]` |
| NEW | `POST /patient/me/share-codes` | PATIENT | see section 3 | see section 3 |
| NEW | `GET /patient/me/access-log` | PATIENT | see section 3 | see section 3 |

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
2. **Auto-VERIFIED on format only.** Signup marks a doctor or pharmacy `VERIFIED` when licence formats pass and are unique, with no registry check. Acceptable for the MVP? The format rules in section 2 are placeholders.
3. **Pharmacy QR exception.** A QR scan creates a PHARMACY grant for that one prescription. Confirm, or require the patient to approve every pharmacy.
4. **Check-in is not gated.** The front desk (ADMIN) stores only the CareID and typed name; the doctor then requests a grant. Confirm, or auto-create a grant request at check-in.
5. **Share codes are my addition** (`POST /patient/me/share-codes`), needed for "request by share code". Confirm shape, 15-minute validity and the `SHR-` prefix.
6. **No break-glass.** ADMIN/SUPER_ADMIN and clinicians in an emergency cannot bypass a missing grant. Add an audited emergency override later?
7. **Retention window** for soft-deleted accounts before purge (proposed 30 days; a legal review is needed for prescription records).
8. **`name` vs `fullName`.** Doctor and pharmacist signup use `fullName` as requested; patient and caregiver use `name`. Unify to one?
9. **`GET /pharmacy-facility/list`** stays authenticated while `GET /hospital/list` is public. Intended?
10. **Scheduler timings** (reminder lead 10 min, missed grace 30 min, run every 5 min) and the EventBridge/API-destination setup are proposals for Surya.
11. **Consent storage:** one versioned object on the user (as drafted) vs a separate collection with history.
12. **Merge:** old patient-app service endpoints (`/patient/me*`, risk, prescription verify/dispense) are folded into this one backend. Confirm.
