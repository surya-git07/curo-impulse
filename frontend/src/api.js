import axios from 'axios';
import { API } from './config';

export const POLICY_VERSION = '2026-10-01';
export const CARE_ID_RE = /^CID-[A-HJ-NP-Z2-9]{6}$/;

// Contract errors are { error, message }; older endpoints returned bare strings.
export const errorMessage = (err, fallback) => {
  const d = err?.response?.data;
  if (typeof d === 'string' && d) return d;
  return d?.message || fallback;
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
