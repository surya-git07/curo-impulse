import { useState, useEffect } from 'react';
import axios from 'axios';
import { decodeToken } from '../utils/jwt';

import { API } from '../config';
import { errorMessage, CARE_ID_RE } from '../api';
import { Spinner, EmptyState, StatusBadge } from './ui';

// What to tell the pharmacist for each way a lookup or a dispense can fail (codes from docs/API_CONTRACT.md).
const lookupMessage = (e) => {
  const status = e.response?.status;
  const code = e.response?.data?.error;
  if (code === 'NO_ACTIVE_GRANT') return 'No access to this patient. Ask the patient to approve access, then try again.';
  if (status === 404) return 'Nothing found for that code or CareID. Check it and try again.';
  if (status === 409) return code === 'ALREADY_DISPENSED' || code === 'PRESCRIPTION_DISPENSED'
    ? 'This prescription has already been dispensed.'
    : errorMessage(e, 'This prescription cannot be dispensed.');
  return errorMessage(e, 'Could not look that up.');
};

const dispenseMessage = (e) => {
  const status = e.response?.status;
  const code = e.response?.data?.error;
  if (status === 404) return 'That prescription no longer exists.';
  if (status === 409 && code === 'ALREADY_DISPENSED') return 'Already dispensed. This prescription was handed out before, so it cannot be dispensed again.';
  if (status === 409) return errorMessage(e, 'This prescription was stopped or its course has ended, so it cannot be dispensed.');
  if (status === 403 && (code === 'WRONG_PHARMACY' || code === 'FORBIDDEN')) return 'Wrong pharmacy. You can only dispense as your own pharmacy.';
  if (code === 'NO_ACTIVE_GRANT') return 'No access to this patient. Ask the patient to approve access, then try again.';
  if (code === 'PHARMACY_NOT_VERIFIED') return 'Your pharmacy has not been verified yet.';
  return errorMessage(e, 'Failed to dispense.');
};

function PharmacyPortal() {
  const [code, setCode] = useState('');
  const [careId, setCareId] = useState('');
  const [collectedBy, setCollectedBy] = useState('');
  const [groups, setGroups] = useState(null); // null = nothing looked up yet
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState('');
  const [now, setNow] = useState(new Date());
  const [lastLookup, setLastLookup] = useState(null);

  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  // Only ACTIVE prescriptions can be dispensed, so only those are shown.
  const activeOnly = (list) => list
    .map((g) => ({ ...g, drugs: (g.drugs || []).filter((d) => d.status === 'ACTIVE') }))
    .filter((g) => g.drugs.length > 0);

  const run = async (kind, fetcher) => {
    setErr(''); setMsg(''); setGroups(null); setBusy(kind);
    try {
      const res = await fetcher();
      const list = Array.isArray(res.data) ? res.data : [res.data];
      setGroups(activeOnly(list));
      setLastLookup(kind);
    } catch (e) {
      setErr(lookupMessage(e));
    } finally {
      setBusy('');
    }
  };

  const byCode = (e) => {
    e?.preventDefault();
    const value = code.trim();
    if (!value) { setErr('Enter the prescription code.'); return; }
    run('code', () => axios.get(`${API}/prescription/by-qr/${encodeURIComponent(value)}`, auth));
  };

  const byCareId = (e) => {
    e?.preventDefault();
    const value = careId.trim().toUpperCase();
    if (!CARE_ID_RE.test(value)) { setErr('A CareID looks like CID-7K3MQ9.'); return; }
    run('careId', () => axios.get(`${API}/prescription/for-patient/${encodeURIComponent(value)}/active`, auth));
  };

  const dispense = async (rx) => {
    setErr(''); setMsg(''); setBusy(rx.id);
    const caregiver = collectedBy.trim().toUpperCase();
    if (caregiver && !CARE_ID_RE.test(caregiver)) {
      setErr('The caregiver CareID looks like CID-7K3MQ9.');
      setBusy('');
      return;
    }
    try {
      // The contract still requires a quantity; there is no quantity screen, so one dispense is recorded as 1.
      await axios.post(`${API}/pharmacy/dispense`, {
        prescriptionId: rx.id,
        quantity: 1,
        ...(caregiver ? { collectedByCareId: caregiver } : {}),
      }, auth);
      setMsg(`Dispensed ${rx.drug}${caregiver ? ` (collected by ${caregiver})` : ''}.`);
      // drop it from the list
      setGroups((prev) => (prev || [])
        .map((g) => ({ ...g, drugs: g.drugs.filter((d) => d.id !== rx.id) }))
        .filter((g) => g.drugs.length > 0));
    } catch (e) {
      setErr(dispenseMessage(e));
    } finally {
      setBusy('');
    }
  };

  const initials = (s) => (s || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const dateStrShort = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

  return (
    <div className="mt-shell">
      <div className="mt-sidebar">
        <div className="mt-sidebar-brand">{sessionStorage.getItem('sector') || 'Pharmacy'}</div>
        <div className="mt-sidebar-sub">{user?.username}</div>

        <div className="mt-sidebar-nav">
          <button className="mt-sidebar-item active">Dispense</button>
        </div>

        <div className="mt-sidebar-footer">
          <div className="avatar" style={{ background: 'rgba(201, 123, 46, 0.25)', color: '#F5D9B8', width: 30, height: 30 }}>
            {initials(user?.username)}
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="mt-sidebar-time">{timeStr}</div>
            <div className="mt-sidebar-date">{dateStrShort}</div>
          </div>
        </div>
      </div>

      <div className="mt-main">
        <div className="mt-page">
          <div className="mt-card">
            <div className="mt-card-title">Find the prescription</div>

            <form onSubmit={byCode} style={{ marginBottom: 12 }}>
              <label className="mt-label">Prescription code (from the patient's QR)</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <input
                  className="mt-input"
                  style={{ flex: 1 }}
                  placeholder="Type or paste the code under the QR"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
                <button className="mt-btn mt-btn-orange" type="submit" disabled={busy === 'code'}>
                  {busy === 'code' ? <><Spinner /> Looking…</> : 'Find'}
                </button>
              </div>
            </form>

            <form onSubmit={byCareId}>
              <label className="mt-label">Patient CareID</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <input
                  className="mt-input"
                  style={{ flex: 1 }}
                  placeholder="e.g. CID-7K3MQ9"
                  value={careId}
                  onChange={(e) => setCareId(e.target.value)}
                />
                <button className="mt-btn mt-btn-orange" type="submit" disabled={busy === 'careId'}>
                  {busy === 'careId' ? <><Spinner /> Looking…</> : 'Find'}
                </button>
              </div>
            </form>

            <div style={{ marginTop: 12 }}>
              <label className="mt-label">Collected by caregiver CareID (optional)</label>
              <input
                className="mt-input"
                placeholder="Leave empty if the patient collects"
                value={collectedBy}
                onChange={(e) => setCollectedBy(e.target.value)}
              />
            </div>

            {err && <div className="alert-box alert-red" role="alert" style={{ marginTop: 12 }}>{String(err)}</div>}
            {msg && <div className="alert-box alert-green" style={{ marginTop: 12 }}>{msg}</div>}
          </div>

          {busy === 'code' || busy === 'careId' ? (
            <div className="mt-card"><div className="loading-row"><Spinner large /> <span>Looking up prescriptions…</span></div></div>
          ) : null}

          {groups && (
            <div className="mt-card">
              <div className="mt-card-title">Active prescriptions</div>
              {groups.length === 0 ? (
                <EmptyState
                  icon="✓"
                  title={lastLookup === 'code' ? 'Nothing left to dispense' : 'No active prescriptions'}
                  text={lastLookup === 'code' ? 'Every medicine on this prescription has been handed out.' : 'This patient has nothing waiting at the pharmacy.'}
                />
              ) : groups.map((g) => (
                <div key={g.groupId || g.id || g.drugs[0].id} style={{ marginBottom: 10 }}>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 4 }}>
                    {g.careId}{g.issuedAt ? ` · issued ${new Date(g.issuedAt).toLocaleDateString()}` : ''}
                  </div>
                  {g.drugs.map((d) => (
                    <div key={d.id} className="prow">
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{d.drug}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{[d.dose, d.duration].filter(Boolean).join(' · ')}</div>
                      </div>
                      <StatusBadge status="ACTIVE" />
                      <button
                        className="mt-btn mt-btn-green"
                        style={{ marginLeft: 8 }}
                        onClick={() => dispense(d)}
                        disabled={busy === d.id}
                      >
                        {busy === d.id ? <><Spinner /> Dispensing…</> : 'Dispense'}
                      </button>
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default PharmacyPortal;
