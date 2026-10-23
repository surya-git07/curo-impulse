import axios from 'axios';
import { API } from './config';

export const POLICY_VERSION = '2026-10-01';
export const CARE_ID_RE = /^CID-[A-HJ-NP-Z2-9]{6}$/;

// Indian mobile numbers: exactly 10 digits, starting 6-9. Sent to the API as typed (digits only).
export const PHONE_RE = /^[6-9][0-9]{9}$/;
export const PHONE_PATTERN = '^[6-9][0-9]{9}$';
export const PHONE_MESSAGE = 'Enter a 10-digit mobile number';
export const phoneDigits = (v) => v.replace(/\D/g, '').slice(0, 10);

// Login field takes a CareID or a phone. A value that starts with a digit is a phone number.
export const LOGIN_ID_PATTERN = '^(CID-[A-HJ-NP-Z2-9]{6}|[6-9][0-9]{9})$';
export const isPhoneLike = (v) => /^\d/.test(v);
export const loginIdentifier = (v) => (isPhoneLike(v) ? phoneDigits(v) : v.slice(0, 10));
export const loginIdentifierError = (v) => {
  if (!v) return '';
  if (isPhoneLike(v)) return PHONE_RE.test(v) ? '' : PHONE_MESSAGE;
  return CARE_ID_RE.test(v.toUpperCase()) ? '' : 'Enter a CareID like CID-7K3MQ9 or a 10-digit mobile number';
};

// Contract errors are { error, message }; older endpoints returned bare strings.
export const errorMessage = (err, fallback) => {
  const d = err?.response?.data;
  if (typeof d === 'string' && d) return d;
  return d?.message || fallback;
};

// Signup failures: a clear message for the codes users can fix, otherwise whatever the API said (or the fallback).
const SIGNUP_MESSAGES = {
  INVALID_LICENCE_NUMBER: 'Licence number format is invalid (example: TN-61907)',
  GSTIN_STATE_MISMATCH: 'Check the GSTIN and state',
  INVALID_GSTIN: 'Check the GSTIN and state',
  LICENCE_GSTIN_MISMATCH: "Licence and GSTIN don't match",
};

export const signupErrorMessage = (err, fallback) => {
  const mapped = SIGNUP_MESSAGES[err?.response?.data?.error];
  if (mapped) return mapped;
  if (err?.response?.status === 409) return 'This phone or licence is already registered';
  return errorMessage(err, fallback);
};

export const login = async (identifier, password) => {
  const { data } = await axios.post(`${API}/auth/login`, { identifier, password });
  const { token, user } = data;
  sessionStorage.setItem('token', token);
  sessionStorage.setItem('role', user.role);
  sessionStorage.setItem('name', user.name || '');
  sessionStorage.setItem('careId', user.careId || '');
  sessionStorage.setItem('facilityName', user.facilityName || '');
  return user;
};

// consent.accepted must be true; the server sets acceptedAt.
export const signup = async (fields, accepted) => {
  const { data } = await axios.post(`${API}/auth/signup`, {
    ...fields,
    consent: { accepted, policyVersion: POLICY_VERSION },
  });
  return data;
};

export const lookupCareId = async (careId, token) => {
  const { data } = await axios.get(`${API}/careid/${encodeURIComponent(careId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return data;
};
