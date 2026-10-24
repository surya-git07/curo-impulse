// api.js
// Live backend client for Login, Home and Today. Contract: docs/API_CONTRACT.md
// (on origin/main). Everything else in the app still uses mocks/.
//
// Rules this file enforces:
// - The token lives in memory only (session below). Never logged, never persisted.
// - Request bodies and headers are never logged, so the password cannot leak.
// - expiresAt is a UTC instant: compared with Date.now() for logic. IST is only
//   used to format dose times for display (istTime).

import { API_URL } from './config';

const TIMEOUT_MS = 10000;
const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000; // India has no DST

// ---- errors ----------------------------------------------------------------
// kind: 'network' (no response / timeout) | 'http' (non-2xx) | 'shape' (2xx but
// not what the contract says) | 'expired' (our own token is past expiresAt).
export class ApiError extends Error {
  constructor(kind, { status = 0, code = null, path = '', body = null, message = '' } = {}) {
    super(message || `${kind} ${status} ${path}`);
    this.kind = kind;
    this.status = status;
    this.code = code;
    this.path = path;
    this.body = body;
  }
}

// Token missing/expired/rejected. Spring answers 403 with an empty body (no
// `error` field) for a bad or expired JWT, so that counts as an auth failure.
export function isAuthError(e) {
  if (!(e instanceof ApiError)) return false;
  if (e.kind === 'expired') return true;
  if (e.kind !== 'http') return false;
  return e.status === 401 || (e.status === 403 && !e.code);
}

// A route the contract says exists but the server refuses or can't find:
// 404, or 403 carrying an error code while we hold a valid token. These are
// reported to the backend owners, not worked around.
export function isContractError(e) {
  return e instanceof ApiError && e.kind === 'http' && (e.status === 404 || (e.status === 403 && !!e.code));
}

// ---- session (memory only) ---------------------------------------------------
let session = null; // { token, expiresAt, user }

export const getSession = () => session;
export const clearSession = () => { session = null; };
export const hasSession = () => !!session;

function isExpired() {
  if (!session) return true;
  const exp = Date.parse(session.expiresAt); // ISO UTC
  return Number.isNaN(exp) || exp <= Date.now();
}

// ---- transport ---------------------------------------------------------------
async function request(method, path, { body, auth = true } = {}) {
  if (auth && (!session || isExpired())) throw new ApiError('expired', { path, message: 'Session expired' });

  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) headers.Authorization = `Bearer ${session.token}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);

  let res;
  try {
    res = await fetch(`${API_URL.replace(/\/+$/, '')}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (e) {
    throw new ApiError('network', { path, message: e && e.name === 'AbortError' ? 'Request timed out' : 'Network error' });
  } finally {
    clearTimeout(timer);
  }

  let parsed = null;
  const text = await res.text().catch(() => '');
  if (text) {
    try { parsed = JSON.parse(text); } catch (e) { parsed = null; }
  }

  if (!res.ok) {
    const err = new ApiError('http', {
      status: res.status,
      code: parsed && typeof parsed.error === 'string' ? parsed.error : null,
      path,
      body: parsed,
    });
    // Status + response body only. Request bodies/headers are never logged.
    const detail = isContractError(err) ? ` ${JSON.stringify(parsed)}` : '';
    console.warn(`[api] ${method} ${path} -> ${res.status}${detail}`);
    throw err;
  }

  return parsed;
}

const shapeError = (path, what) => new ApiError('shape', { path, message: `Unexpected response: ${what}` });
const isStr = (v) => typeof v === 'string' && v.length > 0;

// ---- display helpers ---------------------------------------------------------
// UTC instant -> "HH:mm" in IST. Display only.
export function istTime(iso) {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) return '';
  const d = new Date(ms + IST_OFFSET_MS);
  const pad = (n) => String(n).padStart(2, '0');
  return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}

// ---- mappers: backend shape -> the shape the screens already use -------------
export function toAppPatient(me, user) {
  return {
    id: me.id,
    careId: me.careId,
    name: me.name,
    phone: me.phone || '',
    allergies: Array.isArray(me.allergies) ? me.allergies.filter(isStr) : [],
    profileCompleted: user ? !!user.profileCompleted : !!me.profileCompleted,
  };
}

export function toAppDose(d) {
  return {
    id: d.id,
    drugName: d.drug,
    dosage: d.dose,
    scheduledAt: d.scheduledAt,          // UTC instant, kept for logic
    time: istTime(d.scheduledAt),        // IST, display only
    status: d.status,
    taken: d.status === 'TAKEN',
    late: !!d.late,
  };
}

// ---- calls -------------------------------------------------------------------
export async function login(identifier, password) {
  const path = '/auth/login';
  const res = await request('POST', path, { body: { identifier, password }, auth: false });
  if (!res || !isStr(res.token) || !isStr(res.expiresAt) || !res.user || !isStr(res.user.careId) || !isStr(res.user.role)) {
    throw shapeError(path, 'login');
  }
  session = { token: res.token, expiresAt: res.expiresAt, user: res.user };
  return res.user;
}

export async function getMe(user) {
  const path = '/patient/me';
  const me = await request('GET', path);
  if (!me || !isStr(me.careId) || !isStr(me.name)) throw shapeError(path, 'patient');
  return toAppPatient(me, user);
}

// prescribedBy is { name, hospitalName } (either may be missing); a plain string is taken as the name.
function parsePrescriber(p) {
  if (isStr(p)) return { name: p.trim(), hospital: null };
  if (!p || typeof p !== 'object') return { name: null, hospital: null };
  return { name: isStr(p.name) ? p.name.trim() : null, hospital: isStr(p.hospitalName) ? p.hospitalName.trim() : null };
}

export async function getMedications() {
  const path = '/patient/me/medications';
  const res = await request('GET', path);
  if (!res || !Array.isArray(res.medications)) throw shapeError(path, 'medications');
  return res.medications.map((m) => {
    const who = parsePrescriber(m.prescribedBy);
    return {
      id: m.prescriptionId,
      drug: m.drug,
      dose: m.dose,
      times: Array.isArray(m.times) ? m.times : [],
      endDate: m.endDate || null, // YYYY-MM-DD, absent when no duration
      startDate: isStr(m.startDate) ? m.startDate : null, // issue date, YYYY-MM-DD
      prescriberName: who.name,
      hospitalName: who.hospital,
    };
  });
}

export async function getTodayDoses() {
  const path = '/patient/me/doses/today';
  const res = await request('GET', path);
  if (!Array.isArray(res) || res.some((d) => !d || !isStr(d.id) || !isStr(d.drug) || !isStr(d.scheduledAt))) throw shapeError(path, 'doses');
  return res.map(toAppDose);
}

// Empty object body: takenAt defaults to "now" server-side. Returns the raw
// Dose (status, takenAt, late, markedBy) for the caller to merge.
export async function markDoseTaken(id) {
  return request('POST', `/doses/${encodeURIComponent(id)}/taken`, { body: {} });
}

// ---- share codes ---------------------------------------------------------
// shareCode is a 6-digit code but MUST be treated as a string (leading zeros
// like "042913" are significant — never parse as a number).
export async function createShareCode(scope = ['PROFILE', 'PRESCRIPTIONS'], durationHours = 24) {
  const path = '/patient/me/share-codes';
  const res = await request('POST', path, { body: { scope, durationHours } });
  if (!res || !isStr(res.shareCode) || !isStr(res.expiresAt)) {
    throw shapeError(path, 'share code');
  }
  return {
    shareCode: res.shareCode,       // string, keep as-is, never Number(res.shareCode)
    expiresAt: res.expiresAt,       // UTC instant
    expiresAtDisplay: istTime(res.expiresAt), // for on-screen display
  };
}

// ---- profile ---------------------------------------------------------------
// PUT /patient/me. As built, only `allergies` is stored (trimmed, blanks and
// repeats dropped, at most 30 entries of 60 characters). Returns what the server kept.
export async function updateAllergies(allergies) {
  const path = '/patient/me';
  const res = await request('PUT', path, { body: { allergies } });
  if (!res || !Array.isArray(res.allergies)) throw shapeError(path, 'profile update');
  return { allergies: res.allergies.filter(isStr) };
}
