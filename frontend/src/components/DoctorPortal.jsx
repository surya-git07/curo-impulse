import { useState, useEffect } from 'react';
import axios from 'axios';
import { decodeToken } from '../utils/jwt';

import { API } from '../config';
import { errorMessage, stripDr } from '../api';
import RxQrCode from './RxQrCode';
import { EmptyState, StatusBadge, SeverityCard, Loading } from './ui';

const FREQUENCY_CODE = {
  'Once daily': 'ONCE_DAILY',
  'Twice daily': 'TWICE_DAILY',
  'Three times daily': 'THREE_TIMES_DAILY',
  'Four times daily': 'FOUR_TIMES_DAILY',
};

const buildHistoryStats = (history) => {
  const now = new Date();
  const coursesLast30 = history.filter(p => {
    if (!p.issuedAt) return false;
    const days = (now - new Date(p.issuedAt)) / 86400000;
    return days <= 30;
  }).length;

  const antibiotics90Days = history.filter(p => {
    if (!p.issuedAt) return false;
    const days = (now - new Date(p.issuedAt)) / 86400000;
    return days <= 90;
  }).length;

  const sorted = [...history].filter(p => p.issuedAt).sort((a, b) => new Date(b.issuedAt) - new Date(a.issuedAt));
  const daysSinceLast = sorted.length
    ? Math.floor((now - new Date(sorted[0].issuedAt)) / 86400000)
    : null;

  return { coursesLast30, antibiotics90Days, daysSinceLast, lifetimeTotal: history.length };
};

const pad2 = (n) => String(n).padStart(2, '0');
const todayIso = () => {
  const d = new Date();
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
};
// "2026-11-05" -> a local date string, without a timezone shift
const fmtDay = (iso) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
  return m ? new Date(+m[1], +m[2] - 1, +m[3]).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
};
const addDaysIso = (iso, n) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  const d = new Date(+m[1], +m[2] - 1, +m[3] + n);
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
};
const daysLeftText = (n) => {
  if (n === null || n === undefined) return 'No end date';
  if (n === 0) return 'Last day';
  return n === 1 ? '1 day left' : `${n} days left`;
};
const FREQ_LABEL = { ONCE_DAILY: 'Once daily', TWICE_DAILY: 'Twice daily', THREE_TIMES_DAILY: 'Three times daily', FOUR_TIMES_DAILY: 'Four times daily' };

function DoctorPortal() {
  const [tab, setTab] = useState('queue');
  const [queue, setQueue] = useState([]);
  const [queueCounts, setQueueCounts] = useState(null);
  const [queueErr, setQueueErr] = useState('');
  const [doneBusy, setDoneBusy] = useState('');
  const [selectedCareId, setSelectedCareId] = useState(null);
  const [summary, setSummary] = useState(null);
  const [history, setHistory] = useState([]);
  const [meds, setMeds] = useState([]);
  const [grantErr, setGrantErr] = useState('');
  const [shareCode, setShareCode] = useState('');
  const [accessMsg, setAccessMsg] = useState('');
  const [accessErr, setAccessErr] = useState('');
  const [accessBusy, setAccessBusy] = useState(false);
  const [drugs, setDrugs] = useState([]);
  const [extendList, setExtendList] = useState(null); // null until loaded for the open patient
  const [extendLoading, setExtendLoading] = useState(false);
  const [extendLoadErr, setExtendLoadErr] = useState('');
  const [extendMsg, setExtendMsg] = useState('');
  const [extendOpen, setExtendOpen] = useState(null); // prescription id whose control is open
  const [extendDays, setExtendDays] = useState(7);
  const [extendDate, setExtendDate] = useState('');
  const [extendFormErr, setExtendFormErr] = useState('');
  const [extendBusy, setExtendBusy] = useState(false);
  const [profile, setProfile] = useState(null);
  const [patientView, setPatientView] = useState('main'); // 'main' | 'activeDetail' | 'pastDetail'

const [rxDrugs, setRxDrugs] = useState([{ drug: '', strength: '', frequency: '', duration: '', risk: null }]);
  const [rxMsg, setRxMsg] = useState('');
  const [rxErr, setRxErr] = useState('');
  const [rxWarnings, setRxWarnings] = useState([]);
  const [issuedQr, setIssuedQr] = useState(null);
  const [refreshingQueue, setRefreshingQueue] = useState(false);
  const [now, setNow] = useState(new Date());

  const [myLeave, setMyLeave] = useState(null);
  const [otherDoctors, setOtherDoctors] = useState([]);
  const [leaveFrom, setLeaveFrom] = useState('');
  const [leaveUntil, setLeaveUntil] = useState('');
  const [leaveReason, setLeaveReason] = useState('');
  const [substituteDoctor, setSubstituteDoctor] = useState('');
  const [leaveMsg, setLeaveMsg] = useState('');
  const [leaveErr, setLeaveErr] = useState('');

  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();
  // the stored name may already start with "Dr": strip it so "Dr. {displayName}" never shows "Dr. Dr."
  const displayName = stripDr(user?.username) || 'Doctor';
  const hospitalId = user?.facilityId;

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  const fetchQueue = async () => {
    try {
      const res = await axios.get(`${API}/doctor/queue`, auth);
      // the API answers { counts: { total, done, pending }, entries: [ ... ] }
      const d = res.data;
      setQueue(Array.isArray(d?.entries) ? d.entries : []);
      setQueueCounts(d?.counts || null);
      setQueueErr('');
    } catch (e) {
      setQueueErr(errorMessage(e, 'Could not load the queue. It will retry automatically.'));
    }
  };

  const markDone = async (id) => {
    setDoneBusy(id);
    try {
      await axios.patch(`${API}/doctor/queue/${id}/done`, {}, auth);
      await fetchQueue();
    } catch (e) {
      setQueueErr(errorMessage(e, 'Could not mark this patient as done.'));
    } finally {
      setDoneBusy('');
    }
  };

  const fetchDrugs = async () => {
    try {
      const res = await axios.get(`${API}/drugs/list`, auth);
      setDrugs(res.data);
    } catch (e) { console.error(e); }
  };

  const fetchMyLeave = async () => {
    try {
      const res = await axios.get(`${API}/doctor/leave/mine`, auth);
      setMyLeave(res.data);
    } catch (e) { console.error(e); }
  };

  const fetchOtherDoctors = async () => {
    try {
      const res = await axios.get(`${API}/staff/doctors/${hospitalId}`, auth);
      setOtherDoctors(res.data.filter(d => d.id !== user?.id));
    } catch (e) { console.error(e); }
  };
const fetchProfile = async () => {
  try {
    const res = await axios.get(`${API}/doctor/profile`, auth);
    setProfile(res.data);
  } catch (e) { console.error(e); }
};

  useEffect(() => {
    fetchQueue();
    fetchDrugs();
    const i = setInterval(fetchQueue, 10000);
    return () => clearInterval(i);
  }, []);

  const refreshQueue = async () => {
    setRefreshingQueue(true);
    await fetchQueue();
    setTimeout(() => setRefreshingQueue(false), 400);
  };

  const noGrant = (e) => e.response?.status === 403 && e.response?.data?.error === 'NO_ACTIVE_GRANT';
  const NO_GRANT_TEXT = 'No active access to this patient (NO_ACTIVE_GRANT). Ask the patient for a share code and enter it on the Queue tab, or request access first.';

  const openPatient = async (careId) => {
    setSelectedCareId(careId);
    setSummary(null);
    setHistory([]);
    setMeds([]);
    setGrantErr('');
    setPatientView('main');
    setTab('patient');
    let denied = false;
    const enc = encodeURIComponent(careId);
    try {
      const s = await axios.get(`${API}/patient/${enc}/summary`, auth);
      setSummary(s.data);
    } catch (e) {
      if (noGrant(e)) denied = true; else console.error(e);
    }
    try {
      const m = await axios.get(`${API}/patient/${enc}/medications`, auth);
      setMeds(m.data.medications || []);
      // allergies also come with the profile; use them if the summary was not readable
      setSummary((prev) => prev || (m.data.allergies ? { careId, allergies: m.data.allergies } : prev));
    } catch (e) {
      if (noGrant(e)) denied = true; else console.error(e);
    }
    try {
      const h = await axios.get(`${API}/patient/${enc}/history`, auth);
      setHistory(h.data);
    } catch (e) {
      if (noGrant(e)) denied = true; else console.error(e);
    }
    if (denied) setGrantErr(NO_GRANT_TEXT);
  };

  // The patient shows a 6-digit code; entering it creates the access grant straight away.
  const redeemShareCode = async (e) => {
    e.preventDefault();
    setAccessMsg(''); setAccessErr('');
    const value = shareCode.trim();
    if (!/^\d{6}$/.test(value)) { setAccessErr('The share code is 6 digits.'); return; }
    setAccessBusy(true);
    try {
      const res = await axios.post(`${API}/access-grants`, { shareCode: value, reason: 'Consultation' }, auth);
      setShareCode('');
      setAccessMsg(`Access granted for ${res.data.careId}.`);
      openPatient(res.data.careId);
    } catch (err) {
      const code = err.response?.data?.error;
      if (code === 'INVALID_SHARE_CODE') setAccessErr('That code is not valid, or it was already used. Ask the patient for a new one.');
      else if (code === 'SHARE_CODE_EXPIRED') setAccessErr('That code has expired. Ask the patient for a new one.');
      else if (code === 'TOO_MANY_ATTEMPTS') setAccessErr('Too many wrong codes. Wait a few minutes and try again.');
      else if (code === 'GRANT_EXISTS') setAccessErr('You already have access to this patient. Open them from the queue.');
      else setAccessErr(errorMessage(err, 'Could not use that code.'));
    } finally {
      setAccessBusy(false);
    }
  };

  const issueRx = async (e) => {
    e.preventDefault();
    setRxMsg(''); setRxErr(''); setRxWarnings([]); setIssuedQr(null);

    const validDrugs = rxDrugs.filter(d => d.drug && d.strength && d.frequency && d.duration);
    if (validDrugs.length === 0) {
      setRxErr('Add at least one drug with strength, frequency, and duration.');
      return;
    }

    try {
      const res = await axios.post(`${API}/prescription/issue`, {
        careId: selectedCareId,
        drugs: validDrugs.map(d => ({
          drug: d.drug,
          dose: d.strength,
          duration: d.duration,
          ...(FREQUENCY_CODE[d.frequency] ? { frequency: FREQUENCY_CODE[d.frequency] } : {}),
        })),
      }, auth);
      const n = res.data.drugs.length;
      setRxMsg(`Issued ${n} drug${n > 1 ? 's' : ''} for ${selectedCareId}.`);
      setRxWarnings(res.data.warnings || []);
      setIssuedQr({ code: res.data.qrCode || null });
      setRxDrugs([{ drug: '', strength: '', frequency: '', duration: '', risk: null }]);
      fetchQueue();
      setExtendList(null);
    } catch (err) {
      if (noGrant(err)) setRxErr(NO_GRANT_TEXT);
      else setRxErr(errorMessage(err, 'Failed to issue prescription.'));
    }
  };

  // Extend tab: the open patient's current prescriptions. Returns the list so callers can read the new end date.
  const loadExtend = async () => {
    if (!selectedCareId) return null;
    setExtendLoading(true);
    setExtendLoadErr('');
    try {
      const res = await axios.get(`${API}/doctor/patients/${encodeURIComponent(selectedCareId)}/prescriptions/active`, auth);
      const list = res.data?.prescriptions || [];
      setExtendList(list);
      return list;
    } catch (e) {
      setExtendList([]);
      setExtendLoadErr(noGrant(e) ? NO_GRANT_TEXT : errorMessage(e, 'Could not load the prescriptions. Please try again.'));
      return null;
    } finally {
      setExtendLoading(false);
    }
  };

  const openExtend = (rx) => {
    setExtendMsg('');
    setExtendFormErr('');
    setExtendOpen(rx.id);
    setExtendDays(7);
    setExtendDate('');
  };

  const EXTEND_MESSAGES = {
    INVALID_END_DATE: 'That is not a valid date. Pick the new end date from the calendar.',
    END_DATE_NOT_LATER: null, // the API message names the current end date
    COURSE_TOO_LONG: 'A course cannot run for more than 365 days in total. Choose an earlier date.',
    END_DATE_REQUIRED: 'This prescription has no end date yet. Pick a new end date instead of a number of days.',
    PRESCRIPTION_DISPENSED: 'This prescription was already dispensed in full, so it cannot be extended. Issue a new prescription instead.',
    PRESCRIPTION_DISCONTINUED: 'This prescription was discontinued, so it cannot be extended.',
  };

  const confirmExtend = async (rx, mode) => {
    setExtendFormErr('');
    let body;
    if (mode === 'date') {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(extendDate)) { setExtendFormErr('Pick the new end date.'); return; }
      const floor = rx.endDate && rx.endDate > todayIso() ? rx.endDate : todayIso();
      if (extendDate <= floor) {
        setExtendFormErr(rx.endDate && rx.endDate >= todayIso()
          ? `The new end date must be later than the current end date (${fmtDay(rx.endDate)}).`
          : 'The new end date must be later than today.');
        return;
      }
      body = { newEndDate: extendDate };
    } else {
      body = { extraDays: extendDays };
    }
    setExtendBusy(true);
    try {
      await axios.patch(`${API}/prescriptions/${encodeURIComponent(rx.id)}/extend`, body, auth);
      setExtendOpen(null);
      const list = await loadExtend();
      const updated = (list || []).find((x) => x.id === rx.id);
      setExtendMsg(updated?.endDate
        ? `${rx.drug} now ends on ${fmtDay(updated.endDate)}.`
        : `${rx.drug} was extended.`);
    } catch (e) {
      const code = e.response?.data?.error;
      if (noGrant(e)) setExtendFormErr(NO_GRANT_TEXT);
      else if (EXTEND_MESSAGES[code]) setExtendFormErr(EXTEND_MESSAGES[code]);
      else setExtendFormErr(errorMessage(e, 'Could not extend this prescription. Please try again.'));
    } finally {
      setExtendBusy(false);
    }
  };

  const markMyLeave = async () => {
    setLeaveMsg(''); setLeaveErr('');
    if (!leaveFrom || !leaveUntil || !substituteDoctor) {
      setLeaveErr('Please fill in leave dates and pick a substitute doctor.');
      return;
    }
    try {
      await axios.post(`${API}/doctor/leave/mark`, {
        leaveFrom,
        leaveUntil,
        reason: leaveReason,
        substituteDoctorId: substituteDoctor,
      }, auth);
      setLeaveMsg('Leave marked. Reception will see your substitute automatically.');
      setLeaveFrom(''); setLeaveUntil(''); setLeaveReason(''); setSubstituteDoctor('');
      fetchMyLeave();
    } catch (e) {
      setLeaveErr(errorMessage(e, 'Failed to mark leave.'));
    }
  };

  const endMyLeave = async () => {
    if (!myLeave) return;
    if (!window.confirm('End your leave now? You will be marked available again immediately.')) return;
    try {
      await axios.post(`${API}/doctor/leave/${myLeave.id}/end`, {}, auth);
      fetchMyLeave();
    } catch (e) {
      alert(errorMessage(e, 'Failed to end leave.'));
    }
  };

const addDrugRow = () => {
  setRxDrugs([...rxDrugs, { drug: '', strength: '', frequency: '', duration: '', risk: null }]);
};

  const removeDrugRow = (index) => {
    setRxDrugs(rxDrugs.filter((_, i) => i !== index));
  };

  const updateDrugRow = (index, field, value) => {
    const updated = [...rxDrugs];
    updated[index][field] = value;
    setRxDrugs(updated);
  };

  const checkRiskForRow = async (index, drugName, strength, duration) => {
    if (!drugName) return;
    const days = /^\s*(\d+)\s*d(ays?)?\s*$/i.exec(duration || '');
    try {
      const res = await axios.post(`${API}/checks/interaction`, {
        careId: selectedCareId,
        drug: drugName,
        ...(strength ? { dose: strength } : {}),
        ...(days ? { durationDays: Number(days[1]) } : {}),
      }, auth);
      setRxDrugs((prev) => prev.map((r, i) => (i === index ? { ...r, risk: res.data } : r)));
    } catch (e) {
      const msg = noGrant(e) ? NO_GRANT_TEXT : errorMessage(e, 'The interaction check could not run. Verify manually.');
      setRxDrugs((prev) => prev.map((r, i) => (i === index
        ? { ...r, risk: { severity: 'UNKNOWN', warning: msg, explanation: null } }
        : r)));
    }
  };

  const initials = (s) => (s || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
  const waiting = queue.filter(q => q.status === 'WAITING');
  // the server's counts for this doctor; worked out from the list only if they are missing
  const counts = queueCounts || { total: queue.length, done: queue.length - waiting.length, pending: waiting.length };
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const dateStrShort = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

  const allergyList = Array.isArray(summary?.allergies) ? summary.allergies : [];
  const conditionList = Array.isArray(summary?.chronicConditions) ? summary.chronicConditions
    : (Array.isArray(summary?.conditions) ? summary.conditions : []);
  const hasValue = (v) => v !== null && v !== undefined && String(v).trim() !== '';
  const genderText = hasValue(summary?.gender) ? String(summary.gender).charAt(0).toUpperCase() + String(summary.gender).slice(1).toLowerCase() : '';
  const ageText = hasValue(summary?.ageYears) ? `${summary.ageYears} years` : '';

  const navItems = [
    { key: 'queue', label: 'Queue' },
    { key: 'patient', label: 'Patient' },
    { key: 'rx', label: 'Write Rx' },
    { key: 'extend', label: 'Extend' },
    { key: 'profile', label: 'Profile' },
  ];

const goTab = (t) => {
  setTab(t);
  if (t === 'extend') { setExtendMsg(''); setExtendOpen(null); loadExtend(); }
  if (t === 'profile') { fetchMyLeave(); fetchOtherDoctors(); fetchProfile(); }
};

  return (
    <div className="mt-shell">
      <div className="mt-sidebar">
        <div className="mt-sidebar-brand">{sessionStorage.getItem('sector') || 'Doctor'}</div>
        <div className="mt-sidebar-sub">
          Dr. {displayName}{user?.specialization ? ` · ${user.specialization}` : ''}
        </div>

        <div className="mt-sidebar-nav">
          {navItems.map(item => (
            <button
              key={item.key}
              className={`mt-sidebar-item ${tab === item.key ? 'active' : ''}`}
              onClick={() => goTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="mt-sidebar-footer">
          <div className="avatar" style={{ background: 'rgba(201, 123, 46, 0.25)', color: '#F5D9B8', width: 30, height: 30 }}>
            {initials(stripDr(user?.username))}
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="mt-sidebar-time">{timeStr}</div>
            <div className="mt-sidebar-date">{dateStrShort}</div>
          </div>
        </div>
      </div>

      <div className="mt-main">
        <div className="mt-page">
          {tab === 'queue' && (
            <>
              <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                    Welcome, Dr. {displayName}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                    {now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}
                  </div>
                </div>
                <button className="mt-btn mt-btn-outline" style={{ fontSize: 11, padding: '4px 10px' }} onClick={refreshQueue} disabled={refreshingQueue}>
                  {refreshingQueue ? 'Refreshing…' : '↻ Refresh'}
                </button>
              </div>

              <div className="mt-card">
                <div className="mt-card-title">Open a patient with their share code</div>
                <form onSubmit={redeemShareCode} style={{ display: 'flex', gap: 8 }}>
                  <input
                    className="mt-input"
                    style={{ flex: 1 }}
                    inputMode="numeric"
                    maxLength={6}
                    placeholder="6-digit code from the patient"
                    value={shareCode}
                    onChange={(e) => setShareCode(e.target.value)}
                  />
                  <button className="mt-btn mt-btn-purple" type="submit" disabled={accessBusy}>
                    {accessBusy ? 'Checking…' : 'Get access'}
                  </button>
                </form>
                {accessMsg && <div className="alert-box alert-green" style={{ marginTop: 8 }}>{accessMsg}</div>}
                {accessErr && <div className="alert-box alert-red" style={{ marginTop: 8 }}>{accessErr}</div>}
              </div>

              <div className="mt-card">
                <div className="mt-card-title">Today's queue</div>
                <div className="stat-grid">
                  <div className="stat-box"><div className="stat-num" style={{ color: '#3C3489' }}>{counts.total}</div><div className="stat-label">Total</div></div>
                  <div className="stat-box"><div className="stat-num" style={{ color: '#27500A' }}>{counts.done}</div><div className="stat-label">Done</div></div>
                  <div className="stat-box"><div className="stat-num" style={{ color: '#854F0B' }}>{counts.pending}</div><div className="stat-label">Pending</div></div>
                </div>
                {queueErr && <div className="alert-box alert-red" role="alert">{queueErr}</div>}
                {queue.length === 0 ? (
                  <EmptyState icon="☕" title="No patients in the queue" text="New check-ins from reception show up here automatically." />
                ) : queue.map(v => {
                  const isDone = v.status !== 'WAITING';
                  return (
                    <div key={v.id} className="prow" style={{ cursor: 'pointer', opacity: isDone ? 0.7 : 1 }}>
                      <div className="avatar" style={{ background: '#E6F1FB', color: '#0C447C' }}>{initials(v.patientName || v.name || v.careId)}</div>
                      <div style={{ flex: 1 }} onClick={() => openPatient(v.careId)}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{v.patientName || v.name || v.careId}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                          {(v.patientName || v.name) ? `${v.careId} · ` : ''}Checked in {v.checkedInAt ? new Date(v.checkedInAt).toLocaleTimeString() : ''}
                        </div>
                      </div>
                      <StatusBadge status={isDone ? 'DISPENSED' : 'WAITING'}>{isDone ? 'Done' : 'Waiting'}</StatusBadge>
                      {!isDone && (
                        <button className="mt-btn mt-btn-green" style={{ padding: '4px 10px', fontSize: 11 }}
                          onClick={() => markDone(v.id)} disabled={doneBusy === v.id}>
                          {doneBusy === v.id ? 'Saving…' : 'Mark done'}
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            </>
          )}

          {tab === 'patient' && (
            !selectedCareId ? (
              <div className="mt-card"><EmptyState icon="👤" title="No patient selected" text="Pick a patient from the Queue tab, or open one with a share code." /></div>
            ) : (() => {
              const stats = buildHistoryStats(history);

              // Group past antibiotics by drug name, most-recent first — used for both
              // the compact preview and the "view all" drill-down.
              const nowDate = new Date();
              const byDrug = {};
              history.forEach(p => {
                if (!byDrug[p.drug]) byDrug[p.drug] = [];
                byDrug[p.drug].push(p);
              });
              const pastAntibiotics = Object.entries(byDrug).map(([drugName, entries]) => {
                const mostRecent = entries.reduce((a, b) => new Date(a.issuedAt) > new Date(b.issuedAt) ? a : b);
                const daysAgo = mostRecent.issuedAt ? Math.floor((nowDate - new Date(mostRecent.issuedAt)) / 86400000) : null;
                return { drugName, count: entries.length, daysAgo, lastIssuedAt: mostRecent.issuedAt };
              }).sort((a, b) => (a.daysAgo ?? Infinity) - (b.daysAgo ?? Infinity));

              const activeCount = meds.length;
              const pastCount = pastAntibiotics.length;

              // ---- Drill-down: Active prescriptions ----
              if (patientView === 'activeDetail') {
                return (
                  <>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 2px 10px' }}>
                      <button className="mt-btn mt-btn-outline" style={{ padding: '4px 10px', fontSize: 11 }} onClick={() => setPatientView('main')}>
                        ← Back
                      </button>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--c-navy)' }}>Active prescriptions</span>
                    </div>
                    {meds.map((c, i) => (
                      <div key={c.prescriptionId || i} className="mt-card" style={{ margin: '0 0 8px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                          <span>{c.drug} {c.dose ? `(${c.dose})` : ''}</span>
                          <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>{c.endDate ? `until ${c.endDate}` : 'no end date'}</span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 3 }}>
                          {[c.frequency, (c.times || []).join(', ')].filter(Boolean).join(' · ')}
                        </div>
                      </div>
                    ))}
                  </>
                );
              }

              // ---- Drill-down: Past antibiotics ----
              if (patientView === 'pastDetail') {
                return (
                  <>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 2px 10px' }}>
                      <button className="mt-btn mt-btn-outline" style={{ padding: '4px 10px', fontSize: 11 }} onClick={() => setPatientView('main')}>
                        ← Back
                      </button>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--c-navy)' }}>Past prescriptions</span>
                    </div>
                    {pastAntibiotics.map((p) => (
                      <div key={p.drugName} className="mt-card" style={{ margin: '0 0 8px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                          <span>{p.drugName}</span>
                          <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>
                            {p.lastIssuedAt ? new Date(p.lastIssuedAt).toLocaleDateString() : ''}
                          </span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 3 }}>
                          {p.count}× · last {p.daysAgo}d ago
                        </div>
                      </div>
                    ))}
                  </>
                );
              }

              // ---- Main patient detail view ----
              return (
                <>
                  {grantErr && <div className="alert-box alert-orange">{grantErr}</div>}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '4px 2px 12px' }}>
                    <div style={{
                      width: 48, height: 48, borderRadius: '50%',
                      background: 'var(--c-navy)', color: '#F5D9B8',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: 16, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif",
                      flexShrink: 0,
                    }}>
                      {initials(selectedCareId)}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {summary?.name || (grantErr ? 'Name hidden' : (summary ? 'Not recorded' : 'Loading…'))}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>CareID: {selectedCareId}</div>
                    </div>
                  </div>

                  {/* Stats row */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 10 }}>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {stats.antibiotics90Days}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
                        Prescriptions (90d)
                      </div>
                    </div>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {stats.daysSinceLast === null ? '—' : stats.daysSinceLast === 0 ? 'Today' : stats.daysSinceLast}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
                        Since last Rx
                      </div>
                    </div>
                    <div className="mt-card" style={{
                      textAlign: 'center', margin: 0,
                      background: activeCount > 0 ? 'var(--c-red-bg)' : undefined,
                      border: activeCount > 0 ? '1px solid #F09595' : undefined,
                    }}>
                      <div style={{ fontSize: 20, fontWeight: 700, color: activeCount > 0 ? 'var(--c-red-dark)' : 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {activeCount}
                      </div>
                      <div style={{ fontSize: 11, color: activeCount > 0 ? 'var(--c-red-dark)' : 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
                        Active now
                      </div>
                    </div>
                  </div>

                  {/* Patient details: shown only when the record could be read (an access grant exists) */}
                  {summary && (
                    <div className="mt-card">
                      <div className="mt-card-title">Patient details</div>
                      <div className="detail-grid">
                        <div>
                          <span className="mt-label">Age</span>
                          <div className="detail-value">{ageText || <span className="detail-missing">Not recorded</span>}</div>
                        </div>
                        <div>
                          <span className="mt-label">Gender</span>
                          <div className="detail-value">{genderText || <span className="detail-missing">Not recorded</span>}</div>
                        </div>
                        <div>
                          <span className="mt-label">Blood group</span>
                          <div className="detail-value">{hasValue(summary.bloodGroup) ? summary.bloodGroup : <span className="detail-missing">Not recorded</span>}</div>
                        </div>
                      </div>
                      <span className="mt-label">Allergies</span>
                      <div style={{ marginBottom: 8 }}>
                        {allergyList.length
                          ? allergyList.map((a) => <span key={a} className="chip chip-red">{a}</span>)
                          : <span className="detail-missing">Not recorded</span>}
                      </div>
                      <span className="mt-label">Any other disease</span>
                      <div>
                        {conditionList.length
                          ? conditionList.map((c) => <span key={c} className="chip">{c}</span>)
                          : <span className="detail-missing">Not recorded</span>}
                      </div>
                    </div>
                  )}

                  {/* Current medicines */}
                  <div className="mt-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <div className="mt-card-title" style={{ marginBottom: 0 }}>
                        Current medicines <span className="badge badge-gray" style={{ marginLeft: 6 }}>{activeCount}</span>
                      </div>
                      {activeCount > 3 && (
                        <button className="mt-btn mt-btn-outline" style={{ padding: '3px 8px', fontSize: 10 }} onClick={() => setPatientView('activeDetail')}>
                          View all {activeCount} →
                        </button>
                      )}
                    </div>
                    {activeCount === 0 ? (
                      <EmptyState icon="💊" title="No current medicines" />
                    ) : meds.slice(0, 3).map((c, i) => (
                      <div key={c.prescriptionId || i} style={{ padding: '6px 0', borderTop: i > 0 ? '1px solid var(--border)' : 'none' }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{c.drug} {c.dose ? `(${c.dose})` : ''}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                          {[c.frequency, c.endDate ? `until ${c.endDate}` : null].filter(Boolean).join(' · ')}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* History */}
                  <div className="mt-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <div className="mt-card-title" style={{ marginBottom: 0 }}>
                        Past prescriptions <span className="badge badge-gray" style={{ marginLeft: 6 }}>{pastCount}</span>
                      </div>
                      {pastCount > 4 && (
                        <button className="mt-btn mt-btn-outline" style={{ padding: '3px 8px', fontSize: 10 }} onClick={() => setPatientView('pastDetail')}>
                          View all {pastCount} →
                        </button>
                      )}
                    </div>
                    {pastCount === 0 ? (
                      <EmptyState icon="🗂" title="No previous prescriptions" />
                    ) : pastAntibiotics.slice(0, 4).map((p, i) => (
                      <div key={p.drugName} style={{ padding: '6px 0', borderTop: i > 0 ? '1px solid var(--border)' : 'none' }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{p.drugName}</div>
                        <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{p.count}× · last {p.daysAgo}d ago</div>
                      </div>
                    ))}
                  </div>

                  {/* Sticky write-prescription button */}
                  <div style={{ position: 'sticky', bottom: 0, background: 'var(--surface-0, #fff)', paddingTop: 8, marginTop: 4 }}>
                    <button className="mt-btn mt-btn-purple" style={{ width: '100%' }} onClick={() => setTab('rx')}>
                      Write Prescription
                    </button>
                  </div>
                </>
              );
            })()
          )}

          {tab === 'rx' && (
            !selectedCareId ? (
              <div className="mt-card"><EmptyState icon="👤" title="No patient selected" text="Pick a patient from the Queue tab, or open one with a share code." /></div>
            ) : (
              <div className="mt-card">
                <div className="mt-card-title">New prescription — {selectedCareId}</div>
                <form onSubmit={issueRx} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  {rxDrugs.map((row, index) => (
                    <div key={index} style={{
                      border: '1px solid var(--border)',
                      borderRadius: 10,
                      padding: 12,
                      background: 'var(--surface-1)',
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-secondary)' }}>Drug {index + 1}</span>
                        {rxDrugs.length > 1 && (
                          <button type="button" className="mt-btn mt-btn-outline" style={{ padding: '2px 8px', fontSize: 10 }} onClick={() => removeDrugRow(index)}>
                            ✕ Remove
                          </button>
                        )}
                      </div>

                      <label className="mt-label">Drug (from verified list)</label>
                      <select
                        className="mt-select"
                        style={{ marginBottom: 8 }}
                        value={row.drug}
                        onChange={(e) => {
                          updateDrugRow(index, 'drug', e.target.value);
                          checkRiskForRow(index, e.target.value, row.strength, row.duration);
                        }}
                      >
                        <option value="">Select a drug...</option>
                        {drugs.map(d => (
                          <option key={d.id} value={d.name}>{d.name} ({d.drugClass})</option>
                        ))}
                      </select>

                      {row.risk && (
                        <SeverityCard severity={row.risk.severity} warning={row.risk.warning} explanation={row.risk.explanation} />
                      )}

<label className="mt-label">Strength</label>
<input
  className="mt-input"
  style={{ marginBottom: 8 }}
  placeholder="e.g. 500mg"
  value={row.strength}
  onChange={(e) => updateDrugRow(index, 'strength', e.target.value)}
/>

<label className="mt-label">How many times per day?</label>
<select
  className="mt-select"
  style={{ marginBottom: 8 }}
  value={row.frequency}
  onChange={(e) => updateDrugRow(index, 'frequency', e.target.value)}
>
  <option value="">Select frequency...</option>
  <option value="Once daily">Once daily (OD)</option>
  <option value="Twice daily">Twice daily (BD)</option>
  <option value="Three times daily">Three times daily (TDS)</option>
  <option value="Four times daily">Four times daily (QID)</option>
  <option value="As needed">As needed (SOS)</option>
</select>
                      <label className="mt-label">Duration</label>
                      <input
                        className="mt-input"
                        placeholder="e.g. 7 days"
                        value={row.duration}
                        onChange={(e) => updateDrugRow(index, 'duration', e.target.value)}
                        onBlur={() => checkRiskForRow(index, row.drug, row.strength, row.duration)}
                      />
                    </div>
                  ))}

                  <button type="button" className="mt-btn mt-btn-outline" onClick={addDrugRow}>
                    + Add another drug
                  </button>

                  {rxMsg && <div className="alert-box alert-green">{rxMsg}</div>}

                  {rxWarnings.length > 0 && (
                    <div>
                      <div style={{ fontSize: 11, fontWeight: 600, marginBottom: 4 }}>Interaction check on what was issued</div>
                      {rxWarnings.map((w, i) => (
                        <SeverityCard key={i} drug={w.drug} severity={w.severity} warning={w.warning} explanation={w.explanation} />
                      ))}
                    </div>
                  )}

                  {issuedQr && (
                    <div className="alert-box alert-blue" style={{ textAlign: 'center' }}>
                      <RxQrCode code={issuedQr.code} />
                      {issuedQr.code && <div style={{ fontSize: 11, marginTop: 6 }}>The patient shows this at the pharmacy.</div>}
                    </div>
                  )}

                  {rxErr && <div className="alert-box alert-orange">{String(rxErr)}</div>}

                  <button
                    className="mt-btn mt-btn-purple"
                    type="submit"
                  >
                    Issue Prescription
                  </button>
                </form>
              </div>
            )
          )}

          {tab === 'extend' && (
            <div className="mt-card">
              <div className="mt-card-title">Extend prescription</div>
              {!selectedCareId ? (
                <EmptyState icon="📄" title="Open a patient with their share code first" />
              ) : (
                <>
                  <p style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 12 }}>
                    Current prescriptions for {selectedCareId}. Continue the same medicine without writing a new prescription.
                  </p>
                  {extendMsg && <div className="alert-box alert-green" role="status">{extendMsg}</div>}
                  {extendLoadErr && <div className="alert-box alert-orange" role="alert">{extendLoadErr}</div>}
                  {extendLoading && !extendList ? (
                    <Loading text="Loading prescriptions…" />
                  ) : extendList && extendList.length === 0 && !extendLoadErr ? (
                    <EmptyState icon="📄" title="No active prescriptions to extend" />
                  ) : (extendList || []).map((rx) => {
                    const open = extendOpen === rx.id;
                    const hasEnd = !!rx.endDate;
                    const mode = hasEnd ? undefined : 'date';
                    const minDate = addDaysIso(rx.endDate && rx.endDate > todayIso() ? rx.endDate : todayIso(), 1);
                    return (
                      <div key={rx.id} style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 12, marginBottom: 10 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
                          <div>
                            <strong style={{ fontSize: 13 }}>{rx.drug}</strong>
                            <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                              {[rx.strength, FREQ_LABEL[rx.frequency] || rx.frequency].filter(Boolean).join(' · ')}
                            </div>
                            <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
                              Issued {rx.issuedAt ? new Date(rx.issuedAt).toLocaleDateString() : '—'}
                              {' · '}Ends {hasEnd ? fmtDay(rx.endDate) : '—'}
                              {' · '}<strong>{daysLeftText(rx.daysLeft)}</strong>
                            </div>
                          </div>
                          {rx.canExtend ? (
                            !open && (
                              <button type="button" className="mt-btn mt-btn-purple" onClick={() => openExtend(rx)}>Extend</button>
                            )
                          ) : (
                            <StatusBadge status={rx.status} />
                          )}
                        </div>
                        {!rx.canExtend && (
                          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
                            Already dispensed in full, so it cannot be extended. Issue a new prescription instead.
                          </div>
                        )}

                        {open && (
                          <div style={{ marginTop: 10, paddingTop: 10, borderTop: '1px solid var(--border)' }}>
                            {hasEnd && (
                              <>
                                <span className="mt-label">Extend by</span>
                                <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                                  {[7, 14, 30].map((n) => (
                                    <button
                                      key={n}
                                      type="button"
                                      className={`mt-btn ${extendDays === n && !extendDate ? 'mt-btn-purple' : 'mt-btn-outline'}`}
                                      style={{ flex: 1 }}
                                      onClick={() => { setExtendDays(n); setExtendDate(''); setExtendFormErr(''); }}
                                    >
                                      {n} days
                                    </button>
                                  ))}
                                </div>
                              </>
                            )}
                            <label className="mt-label" htmlFor={`extend-date-${rx.id}`}>
                              {hasEnd ? 'Or pick a new end date' : 'New end date'}
                            </label>
                            <input
                              id={`extend-date-${rx.id}`}
                              className="mt-input"
                              type="date"
                              min={minDate}
                              value={extendDate}
                              onChange={(e) => { setExtendDate(e.target.value); setExtendFormErr(''); }}
                              style={{ marginBottom: 8 }}
                            />
                            {extendFormErr && <div className="alert-box alert-red" role="alert">{extendFormErr}</div>}
                            <div style={{ display: 'flex', gap: 8 }}>
                              <button
                                type="button"
                                className="mt-btn mt-btn-purple"
                                style={{ flex: 1 }}
                                disabled={extendBusy}
                                onClick={() => confirmExtend(rx, mode || (extendDate ? 'date' : 'days'))}
                              >
                                {extendBusy ? 'Saving…' : 'Confirm'}
                              </button>
                              <button type="button" className="mt-btn mt-btn-outline" disabled={extendBusy} onClick={() => setExtendOpen(null)}>
                                Cancel
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </>
              )}
            </div>
          )}

          {tab === 'profile' && (
            <>
              <div className="mt-card" style={{
                background: 'linear-gradient(135deg, var(--c-navy) 0%, var(--c-navy-light) 100%)',
                color: '#fff',
                border: 'none',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
                  <div style={{
                    width: 56, height: 56, borderRadius: '50%',
                    background: 'rgba(201, 123, 46, 0.25)', color: '#F5D9B8',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: 18, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif",
                    flexShrink: 0,
                  }}>
                    {initials(stripDr(user?.username))}
                  </div>
                  <div>
                    <div style={{ fontSize: 16, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif" }}>
                      Dr. {displayName}
                    </div>
                    <div style={{ fontSize: 11, opacity: 0.75, marginTop: 2 }}>
                      {user?.specialization || 'Specialization not set'} · {user?.facilityName}
                    </div>
                    <div style={{ fontSize: 11, opacity: 0.6, marginTop: 2 }}>{user?.username}</div>
                  </div>
                </div>
              </div>

{profile?.licenceNumber && (
  <div className="mt-card">
    <div className="mt-card-title">🪪 Licence details</div>
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
      <div><div className="mt-label">Licence number</div><div style={{ fontSize: 12 }}>{profile.licenceNumber}</div></div>
      <div><div className="mt-label">State council</div><div style={{ fontSize: 12 }}>{profile.stateCouncil}</div></div>
    </div>
    <div style={{ marginTop: 10 }}>
      <StatusBadge status={profile.verificationStatus || 'PENDING'}>
        {profile.verificationStatus === 'VERIFIED' ? '✓ Licence verified' : 'Licence pending review'}
      </StatusBadge>
    </div>
  </div>
)}
              <div className="mt-card">
                <div className="mt-card-title">My leave</div>

                {leaveMsg && <div className="alert-box alert-green">{leaveMsg}</div>}
                {leaveErr && <div className="alert-box alert-red">{String(leaveErr)}</div>}

                {myLeave ? (
                  <div style={{
                    background: 'var(--c-amber-bg)',
                    border: '1px solid #E0A868',
                    borderRadius: 9,
                    padding: 12,
                  }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--c-amber-dark)', marginBottom: 4 }}>
                      You are currently on leave
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--c-amber-dark)' }}>
                      {myLeave.leaveFrom} to {myLeave.leaveUntil} {myLeave.reason ? `· ${myLeave.reason}` : ''}
                    </div>
                    <button className="mt-btn mt-btn-outline" style={{ marginTop: 8 }} onClick={endMyLeave}>
                      End leave now
                    </button>
                  </div>
                ) : (
                  <>
                    <p style={{ fontSize: 11, color: 'var(--text-secondary)', marginBottom: 12 }}>
                      Going on leave? Pick your dates and assign a substitute — Reception will automatically route your patients to them.
                    </p>

                    <label className="mt-label">Leave from</label>
                    <input type="date" className="mt-input" style={{ marginBottom: 8 }}
                      value={leaveFrom} onChange={(e) => setLeaveFrom(e.target.value)} />

                    <label className="mt-label">Leave until</label>
                    <input type="date" className="mt-input" style={{ marginBottom: 8 }}
                      value={leaveUntil} onChange={(e) => setLeaveUntil(e.target.value)} />

                    <label className="mt-label">Reason</label>
                    <select className="mt-select" style={{ marginBottom: 8 }}
                      value={leaveReason} onChange={(e) => setLeaveReason(e.target.value)}>
                      <option value="">Select reason...</option>
                      <option value="Medical leave">Medical leave</option>
                      <option value="Personal leave">Personal leave</option>
                      <option value="Conference">Conference</option>
                      <option value="Other">Other</option>
                    </select>

                    <label className="mt-label">Substitute doctor</label>
                    <select className="mt-select" style={{ marginBottom: 8 }}
                      value={substituteDoctor} onChange={(e) => setSubstituteDoctor(e.target.value)}>
                      <option value="">Select substitute...</option>
                      {otherDoctors.map(d => (
                        <option key={d.id} value={d.id}>{d.name}</option>
                      ))}
                    </select>

                    <button className="mt-btn mt-btn-purple" style={{ width: '100%' }} onClick={markMyLeave}>
                      Mark leave &amp; assign substitute
                    </button>
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default DoctorPortal;