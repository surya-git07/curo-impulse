import { useState, useEffect } from 'react';
import axios from 'axios';
import TopBar from './TopBar';
import { decodeToken } from '../utils/jwt';
import { API } from '../config';
import { CARE_ID_RE, errorMessage } from '../api';
import { EmptyState, StatusBadge } from './ui';

function ReceptionPortal() {
  const [careId, setCareId] = useState('');
  const [verified, setVerified] = useState(null);
  const [patientName, setPatientName] = useState('');
  const [age, setAge] = useState('');
  const [gender, setGender] = useState('');
  const [doctors, setDoctors] = useState([]);
  const [selectedDoctor, setSelectedDoctor] = useState('');
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [loading, setLoading] = useState(false);
  const [now, setNow] = useState(new Date());
  const [queue, setQueue] = useState([]);
  const [refreshing, setRefreshing] = useState(false);

  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();
  const firstName = (user?.username || '').split('.')[0];
  const displayName = firstName ? firstName.charAt(0).toUpperCase() + firstName.slice(1) : 'there';
  const hospitalId = user?.facilityId;

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  const fetchQueue = async () => {
    try {
      const r = await axios.get(`${API}/reception/queue/${hospitalId}`, auth);
      setQueue(r.data);
    } catch { setQueue([]); }
  };

  const fetchDoctors = async () => {
    try {
      const r = await axios.get(`${API}/staff/doctors/${hospitalId}`, auth);
      setDoctors(r.data);
    } catch (e) { console.error(e); }
  };

  useEffect(() => {
    fetchQueue();
    fetchDoctors();
  }, []);

  const refresh = async () => {
    setRefreshing(true);
    await Promise.all([fetchQueue(), fetchDoctors()]);
    setTimeout(() => setRefreshing(false), 400);
  };
const drName = (name) => {
  if (!name) return 'Dr. Unknown';
  const stripped = name.replace(/^dr\.?\s*/i, '');
  return `Dr. ${stripped}`;
};

  // The front desk (ADMIN) has no grant, so GET /careid/{careId} is not available here;
  // check the CareID format and take the name, age and gender typed at the desk.
  const verify = () => {
    setErr(''); setMsg(''); setVerified(null);
    const id = careId.trim().toUpperCase();
    if (!CARE_ID_RE.test(id)) {
      setErr('Enter a valid CareID, e.g. CID-7K3MQ9.');
      return;
    }
    setCareId(id);
    setVerified({ careId: id });
  };

  const selectedDoctorInfo = doctors.find(d => String(d.id) === selectedDoctor);
  const effectiveDoctorId = selectedDoctorInfo && !selectedDoctorInfo.available && selectedDoctorInfo.substituteId
    ? selectedDoctorInfo.substituteId
    : (selectedDoctor || null);

  const checkIn = async () => {
    setErr(''); setMsg('');
    if (!selectedDoctor) {
      setErr('Please select which doctor the patient is seeing.');
      return;
    }
    if (!patientName.trim()) {
      setErr('Please enter the patient name.');
      return;
    }
    try {
      await axios.post(`${API}/reception/checkin`, {
        careId,
        hospitalId,
        doctorId: effectiveDoctorId,
        patientName: patientName.trim(),
        age: age ? parseInt(age, 10) : null,
        gender: gender || null,
      }, auth);
      setMsg(`${patientName.trim()} checked in successfully.`);
      setCareId(''); setVerified(null); setSelectedDoctor('');
      setPatientName(''); setAge(''); setGender('');
      fetchQueue();
    } catch (e) { setErr(errorMessage(e, 'Check-in failed.')); }
  };

const doctorNameFor = (doctorId) => {
  const d = doctors.find(x => x.id === doctorId);
  return d ? drName(d.name) : 'Unassigned';
};

  const dateStr = now.toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const waiting = queue.filter(v => v.status === 'WAITING');

  return (
    <div>
      <TopBar />
      <div className="mt-page">
        <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
              Welcome, {displayName}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>{dateStr}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 18, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif", color: 'var(--c-amber-dark)' }}>
              {timeStr}
            </div>
            <button
              className="mt-btn mt-btn-outline"
              style={{ fontSize: 11, padding: '4px 10px', marginTop: 4 }}
              onClick={refresh}
              disabled={refreshing}
            >
              {refreshing ? 'Refreshing…' : '↻ Refresh'}
            </button>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div>
            <div className="mt-card">
              <div className="mt-card-title">Patient check-in</div>
              <label className="mt-label">Patient CareID</label>
              <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                <input className="mt-input" style={{ flex: 1 }} placeholder="e.g. CID-7K3MQ9"
                  value={careId} onChange={(e) => { setCareId(e.target.value); setVerified(null); }} />
                <button className="mt-btn" onClick={verify} disabled={!careId}>
                  Look up
                </button>
              </div>

              {err && <div className="alert-box alert-red" role="alert">{String(err)}</div>}
              {msg && <div className="alert-box alert-green">{msg}</div>}

              {verified && (
                <div className="alert-box alert-blue" style={{ padding: 12 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 8 }}>CareID {verified.careId}</div>

                  <label className="mt-label">Patient name</label>
                  <input className="mt-input" style={{ marginBottom: 8 }} value={patientName}
                    onChange={(e) => setPatientName(e.target.value)} />
                  <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                    <input className="mt-input" style={{ flex: 1 }} type="number" min="0" max="130" placeholder="Age"
                      value={age} onChange={(e) => setAge(e.target.value)} />
                    <select className="mt-select" style={{ flex: 1 }} value={gender}
                      onChange={(e) => setGender(e.target.value)}>
                      <option value="">Gender</option>
                      <option value="FEMALE">Female</option>
                      <option value="MALE">Male</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </div>

                  <label className="mt-label">Which doctor does the patient want to see?</label>
                  <select
                    className="mt-select"
                    style={{ marginBottom: 8 }}
                    value={selectedDoctor}
                    onChange={(e) => setSelectedDoctor(e.target.value)}
                  >
                    <option value="">Select doctor...</option>
{doctors.map(d => (
  <option key={d.id} value={d.id}>
    {drName(d.name)}{d.specialization ? ` — ${d.specialization}` : ''}{!d.available ? ' (On leave)' : ''}
  </option>
))}
                  </select>

                  {selectedDoctorInfo && !selectedDoctorInfo.available && (
                    <div className="alert-box alert-orange" style={{ marginBottom: 8 }}>
                     ⚠ {drName(selectedDoctorInfo.name)} is on leave until {selectedDoctorInfo.leaveUntil}.
                     {selectedDoctorInfo.substituteName ? (
                       <> Patient will be seen by <strong>{drName(selectedDoctorInfo.substituteName)}</strong> instead.</>
                      ) : (
                        <> No substitute assigned — please check with admin.</>
                      )}
                    </div>
                  )}

                  <button className="mt-btn mt-btn-green" style={{ width: '100%' }} onClick={checkIn}
                    disabled={selectedDoctorInfo && !selectedDoctorInfo.available && !selectedDoctorInfo.substituteId}>
                    Check In Patient
                  </button>
                </div>
              )}
            </div>
          </div>

          <div>
            <div className="mt-card">
              <div className="mt-card-title">👨‍⚕️ Doctor availability</div>
              {doctors.length === 0 ? (
                <EmptyState icon="🩺" title="No doctors found" text="Doctors who sign up for this hospital will show here." />
              ) : doctors.map(d => (
                <div key={d.id} className="prow">
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>{drName(d.name)}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                      {d.specialization || 'General'}
                       {!d.available && d.substituteName && ` · Substitute: ${drName(d.substituteName)}`}                    </div>
                  </div>
                  <span className={`badge ${d.available ? 'badge-green' : 'badge-orange'}`}>
                    {d.available ? 'Available' : 'On leave'}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-card">
          <div className="mt-card-title">🧑‍🤝‍🧑 Patients checked in today ({queue.length})</div>
          {queue.length === 0 ? (
            <EmptyState icon="🧑‍🤝‍🧑" title="No patients checked in yet" text="Enter a CareID on the left to check someone in." />
          ) : queue.map(v => (
            <div key={v.id} className="prow">
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 12, fontWeight: 600 }}>{v.careId}</div>
                <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                  Seeing Dr. {doctorNameFor(v.doctorId)} · Checked in {v.checkedInAt ? new Date(v.checkedInAt).toLocaleTimeString() : ''}
                </div>
              </div>
              <StatusBadge status={v.status} />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default ReceptionPortal;