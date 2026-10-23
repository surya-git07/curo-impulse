import { useState, useEffect } from 'react';
import axios from 'axios';
import { decodeToken } from '../utils/jwt';

import { API } from '../config';
import { errorMessage } from '../api';

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

function DoctorPortal() {
  const [tab, setTab] = useState('queue');
  const [queue, setQueue] = useState([]);
  const [selectedCareId, setSelectedCareId] = useState(null);
  const [summary, setSummary] = useState(null);
  const [history, setHistory] = useState([]);
  const [conflicts, setConflicts] = useState([]);
  const [grantErr, setGrantErr] = useState('');
  const [drugs, setDrugs] = useState([]);
  const [issued, setIssued] = useState([]);
  const [extendTarget, setExtendTarget] = useState(null);
  const [extendNote, setExtendNote] = useState('');
  const [extendMsg, setExtendMsg] = useState('');
  const [profile, setProfile] = useState(null);
  const [patientView, setPatientView] = useState('main'); // 'main' | 'activeDetail' | 'pastDetail'

const [rxDrugs, setRxDrugs] = useState([{ drug: '', strength: '', frequency: '', duration: '', risk: null }]);
  const [rxMsg, setRxMsg] = useState('');
  const [rxErr, setRxErr] = useState('');
  const [rxBlocked, setRxBlocked] = useState(false);
  const [issuedQr, setIssuedQr] = useState(null);
  const [refreshingQueue, setRefreshingQueue] = useState(false);
  const [refreshingIssued, setRefreshingIssued] = useState(false);
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
  const firstName = (user?.username || '').split('.')[0];
  const displayName = firstName ? firstName.charAt(0).toUpperCase() + firstName.slice(1) : 'Doctor';
  const hospitalId = user?.facilityId;

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  const fetchQueue = async () => {
    try {
      const doctorId = user?.id;
      const url = doctorId
        ? `${API}/reception/queue/${hospitalId}?doctorId=${doctorId}`
        : `${API}/reception/queue/${hospitalId}`;
      const res = await axios.get(url, auth);
      setQueue(res.data);
    } catch (e) { console.error(e); }
  };

  const fetchDrugs = async () => {
    try {
      const res = await axios.get(`${API}/drugs/list`, auth);
      setDrugs(res.data);
    } catch (e) { console.error(e); }
  };

  const fetchIssued = async () => {
    try {
      const res = await axios.get(`${API}/prescription/recent`, auth);
      setIssued(res.data);
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
    const i = setInterval(fetchQueue, 5000);
    return () => clearInterval(i);
  }, []);

  const refreshQueue = async () => {
    setRefreshingQueue(true);
    await fetchQueue();
    setTimeout(() => setRefreshingQueue(false), 400);
  };

  const refreshIssued = async () => {
    setRefreshingIssued(true);
    await fetchIssued();
    setTimeout(() => setRefreshingIssued(false), 400);
  };

  const openPatient = async (careId) => {
    setSelectedCareId(careId);
    setSummary(null);
    setHistory([]);
    setConflicts([]);
    setGrantErr('');
    setPatientView('main');
    setTab('patient');
    try {
      const s = await axios.get(`${API}/patient/${encodeURIComponent(careId)}/summary`, auth);
      setSummary(s.data);
    } catch (e) {
      if (e.response?.data?.error === 'NO_ACTIVE_GRANT') {
        setGrantErr('No active access grant for this CareID. Ask the patient for a share code or request access first.');
      } else console.error(e);
    }
    try {
      const h = await axios.get(`${API}/prescription/patient/${encodeURIComponent(careId)}`, auth);
      setHistory(h.data);
    } catch (e) { console.error(e); }
    try {
      const c = await axios.get(`${API}/prescription/active-conflicts/${encodeURIComponent(careId)}`, auth);
      setConflicts(c.data);
    } catch (e) { console.error(e); }
  };

  const removeVisit = async (id) => {
    try {
      await axios.delete(`${API}/reception/visit/${id}`, auth);
      fetchQueue();
    } catch (e) { console.error(e); }
  };

  const removeIssued = async (id) => {
    if (!window.confirm('Remove this prescription record?')) return;
    try {
      await axios.delete(`${API}/prescription/${id}`, auth);
      fetchIssued();
    } catch (e) { console.error(e); }
  };

  const recallGroup = async (groupId) => {
    if (!window.confirm('Recall this prescription? This will invalidate it immediately.')) return;
    try {
      await axios.post(`${API}/prescription/group/${groupId}/recall`, {}, auth);
      fetchIssued();
    } catch (e) {
      alert(e.response?.data || 'Failed to recall.');
    }
  };

  const minutesLeftToRecall = (issuedAt) => {
    if (!issuedAt) return 0;
    const elapsed = (new Date() - new Date(issuedAt)) / 60000;
    return Math.max(0, Math.ceil(30 - elapsed));
  };

  const issueRx = async (e) => {
    e.preventDefault();
    setRxMsg(''); setRxErr(''); setRxBlocked(false); setIssuedQr(null);

const validDrugs = rxDrugs.filter(d => d.drug && d.strength && d.frequency && d.duration);
if (validDrugs.length === 0) {
  setRxErr('Add at least one drug with strength, frequency, and duration.');
  return;
}

try {
 const res = await axios.post(`${API}/prescription/issue`, {
   careId: selectedCareId,
   drugs: validDrugs.map(d => ({ drug: d.drug, dose: `${d.strength} ${d.frequency.toLowerCase()}`, duration: d.duration })),
  }, auth);
      setRxMsg(`Issued — Prescription Group ID: ${res.data.groupId} (${res.data.drugs.length} drug${res.data.drugs.length > 1 ? 's' : ''})`);
      setIssuedQr(res.data.qrCode);
      setRxDrugs([{ drug: '', strength: '', frequency: '', duration: '', risk: null }]);
      fetchQueue();
      fetchIssued();
    } catch (err) {
      if (err.response?.status === 409) {
        setRxBlocked(true);
        setRxErr(errorMessage(err, 'Prescription conflict.'));
      } else {
        setRxErr(errorMessage(err, 'Failed to issue prescription.'));
      }
    }
  };

  const extendGroup = async (groupId, days) => {
    setExtendMsg('');
    try {
      await axios.post(`${API}/prescription/group/${groupId}/extend`, {
        extendByDays: days,
        note: extendNote,
      }, auth);
      setExtendMsg(`Extended by ${days} days.`);
      setExtendNote('');
      setExtendTarget(null);
      fetchIssued();
    } catch (e) {
      alert(e.response?.data || 'Failed to extend.');
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
      alert(e.response?.data || 'Failed to end leave.');
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

  const checkRiskForRow = async (index, drugName, duration) => {
    if (!drugName) return;
    try {
      const res = await axios.post(`${API}/ai/risk-score`, {
        drug: drugName,
        careId: selectedCareId,
        duration: duration,
        allergies: Array.isArray(summary?.allergies) ? summary.allergies.join(', ') : (summary?.allergies || 'None'),
      }, auth);
      const updated = [...rxDrugs];
      updated[index].risk = res.data;
      setRxDrugs(updated);
    } catch (e) { console.error(e); }
  };

  const initials = (s) => (s || '?').replace(/[^A-Za-z0-9]/g, '').slice(0, 2).toUpperCase();
  const waiting = queue.filter(q => q.status === 'WAITING');
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const dateStrShort = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

  const hasRealAllergies = summary && !summary.offline && (
    Array.isArray(summary.allergies)
      ? summary.allergies.length > 0
      : (summary.allergies && summary.allergies !== 'None' && summary.allergies !== 'Unknown')
  );

  const hasTraditionalMedicine = summary && !summary.offline &&
    summary.traditional_medicine && summary.traditional_medicine !== 'None';

  const navItems = [
    { key: 'queue', label: 'Queue' },
    { key: 'patient', label: 'Patient' },
    { key: 'rx', label: 'Write Rx' },
    { key: 'issued', label: 'Issued' },
    { key: 'extend', label: 'Extend' },
    { key: 'profile', label: 'Profile' },
  ];

const goTab = (t) => {
  setTab(t);
  if (t === 'issued' || t === 'extend') fetchIssued();
  if (t === 'profile') { fetchMyLeave(); fetchOtherDoctors(); fetchProfile(); }
};

  return (
    <div className="mt-shell">
      <div className="mt-sidebar">
        <div className="mt-sidebar-brand">{user?.facilityName || 'Unassigned'} Doctor</div>
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
          {tab === 'queue' && (
            <>
              <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
                <div>
                  <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                    Welcome, Dr. {displayName}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                    {now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}
                  </div>
                </div>
                <button className="mt-btn mt-btn-outline" style={{ fontSize: 10, padding: '4px 10px' }} onClick={refreshQueue} disabled={refreshingQueue}>
                  {refreshingQueue ? 'Refreshing…' : '↻ Refresh'}
                </button>
              </div>

              <div className="mt-card">
                <div className="mt-card-title">Today's queue</div>
                <div className="stat-grid">
                  <div className="stat-box"><div className="stat-num" style={{ color: '#3C3489' }}>{queue.length}</div><div className="stat-label">Total</div></div>
                  <div className="stat-box"><div className="stat-num" style={{ color: '#27500A' }}>{queue.length - waiting.length}</div><div className="stat-label">Done</div></div>
                  <div className="stat-box"><div className="stat-num" style={{ color: '#854F0B' }}>{waiting.length}</div><div className="stat-label">Pending</div></div>
                </div>
                {waiting.length === 0 ? (
                  <p style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', padding: '1.5rem 0' }}>No patients waiting.</p>
                ) : waiting.map(v => (
                  <div key={v.id} className="prow" style={{ cursor: 'pointer' }}>
                    <div className="avatar" style={{ background: '#E6F1FB', color: '#0C447C' }}>{initials(v.careId)}</div>
                    <div style={{ flex: 1 }} onClick={() => openPatient(v.careId)}>
                      <div style={{ fontSize: 12, fontWeight: 600 }}>{v.careId}</div>
                      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                        Checked in {new Date(v.checkedInAt).toLocaleTimeString()}
                      </div>
                    </div>
                    <span className="badge badge-gray">Waiting</span>
                    <button className="mt-btn mt-btn-outline" style={{ padding: '4px 10px', fontSize: 10 }} onClick={() => removeVisit(v.id)}>✕</button>
                  </div>
                ))}
              </div>
            </>
          )}

          {tab === 'patient' && (
            !selectedCareId ? (
              <div className="mt-card"><p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Select a patient from the Queue tab first.</p></div>
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

              const activeCount = conflicts.length;
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
                    {conflicts.map((c, i) => (
                      <div key={i} className="mt-card" style={{ margin: '0 0 8px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                          <span>{c.drug} {c.dose ? `(${c.dose})` : ''}</span>
                          <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>
                            {c.issuedAt ? new Date(c.issuedAt).toLocaleDateString() : ''}
                          </span>
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 3 }}>
                          {[c.drugClass && `${c.drugClass}-class`, c.frequency, c.duration].filter(Boolean).join(' · ')}
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
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--c-navy)' }}>Past antibiotics</span>
                    </div>
                    {pastAntibiotics.map((p) => (
                      <div key={p.drugName} className="mt-card" style={{ margin: '0 0 8px' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 600 }}>
                          <span>{p.drugName}</span>
                          <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>
                            {p.lastIssuedAt ? new Date(p.lastIssuedAt).toLocaleDateString() : ''}
                          </span>
                        </div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 3 }}>
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
                        {summary?.name || 'Loading...'}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>CareID: {selectedCareId}</div>
                    </div>
                  </div>

                  {summary?.offline && (
                    <div className="alert-box alert-orange">
                      ⚠ Patient service unreachable — allergy, vitals, and cross-facility history unavailable right now.
                    </div>
                  )}

                  {/* Basic info row */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10 }}>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--c-navy)' }}>{summary?.blood_group || '—'}</div>
                      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 3 }}>Blood group</div>
                    </div>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--c-navy)' }}>{summary?.age || '—'}</div>
                      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 3 }}>Age</div>
                    </div>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--c-navy)' }}>{summary?.gender || '—'}</div>
                      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 3 }}>Gender</div>
                    </div>
                  </div>

                  {/* Stats row */}
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginTop: 10 }}>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {stats.antibiotics90Days}
                      </div>
                      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
                        Antibiotics (90d)
                      </div>
                    </div>
                    <div className="mt-card" style={{ textAlign: 'center', margin: 0 }}>
                      <div style={{ fontSize: 20, fontWeight: 700, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
                        {stats.daysSinceLast === null ? '—' : stats.daysSinceLast === 0 ? 'Today' : stats.daysSinceLast}
                      </div>
                      <div style={{ fontSize: 9, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
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
                      <div style={{ fontSize: 9, color: activeCount > 0 ? 'var(--c-red-dark)' : 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: 0.4, marginTop: 4 }}>
                        Active now
                      </div>
                    </div>
                  </div>

                  {hasRealAllergies && (
                    <div className="mt-card" style={{ background: 'var(--c-red-bg)', border: '1px solid #F09595' }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--c-red-dark)' }}>
                        🛡 Allergy on file: {Array.isArray(summary.allergies) ? summary.allergies.join(', ') : summary.allergies}
                      </div>
                      <div style={{ fontSize: 10, color: '#791F1F', marginTop: 2 }}>
                        Do not prescribe {Array.isArray(summary.allergies) ? summary.allergies.join(' or ') : summary.allergies}-class drugs.
                      </div>
                    </div>
                  )}

                  {hasTraditionalMedicine && (
                    <div className="mt-card" style={{ background: 'var(--c-amber-bg)', border: '1px solid #E0A868' }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--c-amber-dark)' }}>
                        Traditional medicine in use — check herb-drug interactions before prescribing.
                      </div>
                    </div>
                  )}

                  {/* Comorbidities */}
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', padding: '10px 4px' }}>
                    <span style={{ fontWeight: 600, color: 'var(--text-body, #333)' }}>Any other disease: </span>
                    {summary && summary.conditions && summary.conditions !== 'None' && summary.conditions !== 'Unknown'
                      ? summary.conditions
                      : 'None recorded'}
                  </div>

                  {/* Active prescriptions */}
                  <div className="mt-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <div className="mt-card-title" style={{ marginBottom: 0 }}>
                        Active prescriptions <span className="badge badge-gray" style={{ marginLeft: 6 }}>{activeCount}</span>
                      </div>
                      {activeCount > 3 && (
                        <button className="mt-btn mt-btn-outline" style={{ padding: '3px 8px', fontSize: 10 }} onClick={() => setPatientView('activeDetail')}>
                          View all {activeCount} →
                        </button>
                      )}
                    </div>
                    {activeCount === 0 ? (
                      <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>None active right now.</p>
                    ) : conflicts.slice(0, 3).map((c, i) => (
                      <div key={i} style={{ padding: '6px 0', borderTop: i > 0 ? '1px solid var(--border)' : 'none' }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{c.drug} {c.dose ? `(${c.dose})` : ''}</div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                          {c.drugClass ? `${c.drugClass} · ` : ''}since {c.issuedAt ? new Date(c.issuedAt).toLocaleDateString() : ''}
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Past antibiotics */}
                  <div className="mt-card">
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                      <div className="mt-card-title" style={{ marginBottom: 0 }}>
                        Past antibiotics <span className="badge badge-gray" style={{ marginLeft: 6 }}>{pastCount}</span>
                      </div>
                      {pastCount > 4 && (
                        <button className="mt-btn mt-btn-outline" style={{ padding: '3px 8px', fontSize: 10 }} onClick={() => setPatientView('pastDetail')}>
                          View all {pastCount} →
                        </button>
                      )}
                    </div>
                    {pastCount === 0 ? (
                      <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>No previous prescriptions.</p>
                    ) : pastAntibiotics.slice(0, 4).map((p, i) => (
                      <div key={p.drugName} style={{ padding: '6px 0', borderTop: i > 0 ? '1px solid var(--border)' : 'none' }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>{p.drugName}</div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{p.count}× · last {p.daysAgo}d ago</div>
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
              <div className="mt-card"><p style={{ fontSize: 12, color: 'var(--text-muted)' }}>Select a patient from the Queue tab first.</p></div>
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
                        <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)' }}>Drug {index + 1}</span>
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
                          checkRiskForRow(index, e.target.value, row.duration);
                        }}
                      >
                        <option value="">Select a drug...</option>
                        {drugs.map(d => (
                          <option key={d.id} value={d.name}>{d.name} ({d.drugClass})</option>
                        ))}
                      </select>

                      {row.risk && (
                        <div style={{ marginBottom: 8 }}>
                          {row.risk.blocked ? (
                            <div className="alert-box alert-red">
                              <strong>🚫 Blocked:</strong> {row.risk.explanation}
                            </div>
                          ) : (
                            <>
                              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11 }}>
                                <span>AMR Risk {row.risk.awareCategory ? `· WHO: ${row.risk.awareCategory}` : ''}</span>
                                <strong style={{ color: row.risk.riskLevel === 'HIGH' ? '#A32D2D' : row.risk.riskLevel === 'MEDIUM' ? '#854F0B' : '#27500A' }}>
                                  {row.risk.riskLevel} ({(row.risk.riskScore * 100).toFixed(0)}%)
                                </strong>
                              </div>
                              <div className="risk-track">
                                <div className="risk-fill" style={{
                                  width: `${row.risk.riskScore * 100}%`,
                                  background: row.risk.riskLevel === 'HIGH' ? '#A32D2D' : row.risk.riskLevel === 'MEDIUM' ? '#854F0B' : '#1D9E75',
                                }} />
                              </div>
                              <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{row.risk.explanation}</div>
                            </>
                          )}
                        </div>
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
                        onBlur={() => checkRiskForRow(index, row.drug, row.duration)}
                      />
                    </div>
                  ))}

                  <button type="button" className="mt-btn mt-btn-outline" onClick={addDrugRow}>
                    + Add another drug
                  </button>

                  {rxMsg && <div className="alert-box alert-green">{rxMsg}</div>}

                  {issuedQr && (
                    <div className="alert-box alert-blue" style={{ textAlign: 'center' }}>
                      Prescription sent to the patient's app — they'll show their QR at the pharmacy.
                    </div>
                  )}

                  {rxErr && <div className={`alert-box ${rxBlocked ? 'alert-red' : 'alert-orange'}`}>{rxBlocked ? '⚠ ' : ''}{String(rxErr)}</div>}

                  <button
                    className="mt-btn mt-btn-purple"
                    type="submit"
                    disabled={rxDrugs.some(d => d.risk?.blocked)}
                  >
                    Issue Prescription
                  </button>
                </form>
              </div>
            )
          )}

          {tab === 'issued' && (
            <div className="mt-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <div className="mt-card-title" style={{ marginBottom: 0 }}>Recently issued</div>
                <button className="mt-btn mt-btn-outline" style={{ fontSize: 10, padding: '4px 10px' }} onClick={refreshIssued} disabled={refreshingIssued}>
                  {refreshingIssued ? 'Refreshing…' : '↻ Refresh'}
                </button>
              </div>
              {issued.length === 0 ? (
                <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>Nothing issued yet.</p>
              ) : issued.map(g => {
                const minsLeft = minutesLeftToRecall(g.issuedAt);
                return (
                  <div key={g.id} className="prow" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                    <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 11, fontWeight: 600 }}>Group #{g.id} — {g.careId}</div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                          {g.issuedAt ? new Date(g.issuedAt).toLocaleString() : ''}
                          {g.extendedDays > 0 ? ` · extended +${g.extendedDays}d` : ''}
                        </div>
                      </div>
                      <span className={`badge ${g.status === 'ISSUED' ? 'badge-blue' : g.status === 'REVOKED' ? 'badge-red' : 'badge-green'}`}>{g.status}</span>
                      <button className="mt-btn mt-btn-outline" style={{ padding: '4px 10px', fontSize: 10, marginLeft: 8 }} onClick={() => removeIssued(g.id)}>✕</button>
                    </div>
                    {g.status === 'ISSUED' && minsLeft > 0 && (
                      <div style={{ marginTop: 6, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 10, color: 'var(--c-red)' }}>⏱ Recall window — {minsLeft} min remaining</span>
                        <button className="mt-btn mt-btn-red" style={{ fontSize: 10, padding: '4px 10px' }} onClick={() => recallGroup(g.id)}>
                          Recall prescription
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {tab === 'extend' && (
            <div className="mt-card">
              <div className="mt-card-title">Extend prescription</div>
              <p style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 12 }}>
                Patient returns, continue the same medicine — no new prescription needed.
              </p>

              {extendMsg && <div className="alert-box alert-green">{extendMsg}</div>}

              {issued.filter(g => g.status === 'ISSUED' || g.status === 'DISPENSED').length === 0 ? (
                <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>No active prescriptions to extend.</p>
              ) : issued.filter(g => g.status === 'ISSUED' || g.status === 'DISPENSED').map(g => (
<div key={g.id} style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 12, marginBottom: 10 }}>
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
    <div>
      <strong style={{ fontSize: 12 }}>Group #{g.id} — {g.careId}</strong>
      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
        Issued {g.issuedAt ? new Date(g.issuedAt).toLocaleDateString() : ''}
        {g.extendedDays > 0 ? ` · already extended +${g.extendedDays}d` : ''}
      </div>
    </div>
    <span className={`badge ${g.status === 'ISSUED' ? 'badge-blue' : 'badge-green'}`}>{g.status}</span>
  </div>

  {g.drugs && g.drugs.length > 0 && (
    <div style={{
      background: 'var(--surface-1)',
      borderRadius: 8,
      padding: '8px 10px',
      marginBottom: 8,
    }}>
      {g.drugs.map(drug => (
        <div key={drug.id} style={{ fontSize: 11, marginBottom: 4 }}>
          <strong>{drug.drug}</strong> — {drug.dose} · {drug.duration}
        </div>
      ))}
    </div>
  )}

                  {extendTarget === g.id ? (
                    <div style={{ marginTop: 10 }}>
                      <label className="mt-label">Doctor note</label>
                      <input
                        className="mt-input"
                        style={{ marginBottom: 8 }}
                        placeholder="e.g. Patient improving, continue course. No dosage change."
                        value={extendNote}
                        onChange={(e) => setExtendNote(e.target.value)}
                      />
                      <div style={{ display: 'flex', gap: 8 }}>
                        <button className="mt-btn mt-btn-outline" style={{ flex: 1 }} onClick={() => extendGroup(g.id, 3)}>+3 days</button>
                        <button className="mt-btn mt-btn-outline" style={{ flex: 1 }} onClick={() => extendGroup(g.id, 5)}>+5 days</button>
                        <button className="mt-btn mt-btn-outline" style={{ flex: 1 }} onClick={() => extendGroup(g.id, 7)}>+7 days</button>
                      </div>
                      <button className="mt-btn mt-btn-outline" style={{ width: '100%', marginTop: 8 }} onClick={() => setExtendTarget(null)}>
                        Cancel
                      </button>
                    </div>
                  ) : (
                    <button className="mt-btn mt-btn-purple" style={{ width: '100%', marginTop: 8 }} onClick={() => setExtendTarget(g.id)}>
                      Extend this prescription
                    </button>
                  )}
                </div>
              ))}
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
                    {initials(user?.username)}
                  </div>
                  <div>
                    <div style={{ fontSize: 16, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif" }}>
                      Dr. {displayName}
                    </div>
                    <div style={{ fontSize: 11, opacity: 0.75, marginTop: 2 }}>
                      {user?.specialization || 'Specialization not set'} · {user?.facilityName}
                    </div>
                    <div style={{ fontSize: 10, opacity: 0.6, marginTop: 2 }}>{user?.username}</div>
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
      <span className={`badge ${profile.verificationStatus === 'VERIFIED' ? 'badge-green' : 'badge-gray'}`}>
        {profile.verificationStatus === 'VERIFIED' ? '✓ Licence verified' : 'Licence pending review'}
      </span>
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
                    <p style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 12 }}>
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