// mocks/shares.js
// Mock logic for "Share my record" — no backend.
//
// PROPOSED CONTRACT (not in docs/API_CONTRACT.md yet — agree with Vasuki, then
// move this block into the contract in the same commit and announce it):
//
//   POST   /api/shares          body { scopes: ["MEDICINES","ALLERGIES","HISTORY"], duration_hours: 1|24|168 }
//                               -> 201 { id, code, link, scopes, created_at, expires_at }
//   GET    /api/shares          -> 200 [ { id, code, link, scopes, created_at, expires_at } ]   (active only)
//   DELETE /api/shares/{id}     -> 204   (patient revokes)
//   POST   /api/shares/redeem   body { code } (public, no JWT, rate-limited)
//                               -> 200 { shared_by, expires_at, scopes, data }
//                               -> 404 INVALID_CODE | 410 SHARE_EXPIRED | 410 SHARE_REVOKED
//                               -> 429 TOO_MANY_ATTEMPTS
//
// Redeeming a share also writes an access-log entry with role "Guest".

import { MOCK_PATIENT, MOCK_PRESCRIPTIONS } from './data';

export const SCOPES = ['MEDICINES', 'ALLERGIES', 'HISTORY'];
export const DURATIONS = [
  { label: '1 hour', hours: 1 },
  { label: '24 hours', hours: 24 },
  { label: '7 days', hours: 168 },
];

const MAX_BAD_ATTEMPTS = 5;
const LOCKOUT_SECONDS = 60;
const HOUR_MS = 3600 * 1000;

// No 0/O/1/I so codes are easy to read out loud.
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

const now = () => Date.now();
const iso = (ms) => new Date(ms).toISOString();

// ---- in-memory store -------------------------------------------------------
// Two seeded shares so every redeem outcome can be demoed.
let nextId = 3;
let shares = [
  {
    id: 's1', code: 'EXP4KD', link: 'https://curo.example/s/EXP4KD', scopes: ['MEDICINES'],
    created_at: iso(now() - 3 * HOUR_MS), expires_at: iso(now() - 2 * HOUR_MS), revoked: false,
  },
  {
    id: 's2', code: 'RVK7PQ', link: 'https://curo.example/s/RVK7PQ', scopes: ['MEDICINES', 'ALLERGIES'],
    created_at: iso(now() - HOUR_MS), expires_at: iso(now() + 23 * HOUR_MS), revoked: true,
  },
];

let badAttempts = 0;
let lockedUntil = 0;

function makeCode() {
  let code;
  do {
    code = Array.from({ length: 6 }, () => CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)]).join('');
  } while (shares.some((s) => s.code === code));
  return code;
}

const isExpired = (s) => new Date(s.expires_at).getTime() <= now();
const isActive = (s) => !s.revoked && !isExpired(s);

// ---- API -------------------------------------------------------------------
export function createShare({ scopes, durationHours }) {
  const code = makeCode();
  const share = {
    id: `s${nextId++}`,
    code,
    link: `https://curo.example/s/${code}`,
    scopes: SCOPES.filter((s) => scopes.includes(s)),
    created_at: iso(now()),
    expires_at: iso(now() + durationHours * HOUR_MS),
    revoked: false,
  };
  shares = [share, ...shares];
  return share;
}

export function revokeShare(id) {
  shares = shares.map((s) => (s.id === id ? { ...s, revoked: true } : s));
}

export function listActiveShares() {
  return shares.filter(isActive);
}

// Medicines = issued, or dispensed and still inside the course.
function currentMedicines() {
  return MOCK_PRESCRIPTIONS.filter((p) => {
    if (p.status !== 'dispensed') return true;
    const end = new Date(`${p.dispensed_at || p.created_at}Z`);
    end.setDate(end.getDate() + (parseInt(p.duration_days, 10) || 0));
    return new Date() <= end;
  }).map((p) => ({ drug_name: p.drug_name, dosage: p.dosage, duration_days: p.duration_days }));
}

function buildData(scopes) {
  const data = {};
  if (scopes.includes('MEDICINES')) data.medicines = currentMedicines();
  if (scopes.includes('ALLERGIES')) data.allergies = MOCK_PATIENT.allergies;
  if (scopes.includes('HISTORY')) {
    data.history = MOCK_PRESCRIPTIONS.map((p) => ({
      drug_name: p.drug_name, doctor_name: p.doctor_name, created_at: p.created_at,
    }));
  }
  return data;
}

// Returns { ok: true, share, data } or { ok: false, error, ... }.
export function redeemShare(rawCode) {
  if (now() < lockedUntil) {
    return { ok: false, error: 'TOO_MANY_ATTEMPTS', retry_after: Math.ceil((lockedUntil - now()) / 1000) };
  }

  const code = (rawCode || '').trim().toUpperCase();
  const share = shares.find((s) => s.code === code);

  if (!share) {
    badAttempts += 1;
    if (badAttempts >= MAX_BAD_ATTEMPTS) {
      badAttempts = 0;
      lockedUntil = now() + LOCKOUT_SECONDS * 1000;
      return { ok: false, error: 'TOO_MANY_ATTEMPTS', retry_after: LOCKOUT_SECONDS };
    }
    return { ok: false, error: 'INVALID_CODE' };
  }

  badAttempts = 0;
  if (share.revoked) return { ok: false, error: 'SHARE_REVOKED' };
  if (isExpired(share)) return { ok: false, error: 'SHARE_EXPIRED', expires_at: share.expires_at };

  return { ok: true, share, shared_by: MOCK_PATIENT.name, data: buildData(share.scopes) };
}

// Demo codes shown as a hint on the preview box.
export const DEMO_CODES = { expired: 'EXP4KD', revoked: 'RVK7PQ' };
