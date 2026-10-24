import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API } from '../config';
import { CARE_ID_RE, errorMessage } from '../api';
import { Spinner } from './ui';

const checkInMessage = (e) => {
  const code = e.response?.data?.error;
  if (code === 'PATIENT_NOT_FOUND') return 'No patient found with that CareID. Check it and try again.';
  if (code === 'ALREADY_IN_QUEUE') return 'This patient is already waiting for this doctor today.';
  if (code === 'INVALID_DOCTOR') return 'That doctor cannot take check-ins. Choose a verified doctor from the list.';
  return errorMessage(e, 'Check-in failed. Please try again.');
};

const drName = (name) => `Dr. ${(name || '').replace(/^dr\.?\s*/i, '')}`.trim();

// A reception account is a front-desk login only: it checks patients in and sees no patient data.
function ReceptionDashboard() {
  const navigate = useNavigate();
  const [careId, setCareId] = useState('');
  const [doctors, setDoctors] = useState([]);
  const [doctorId, setDoctorId] = useState('');
  const [doctorsLoading, setDoctorsLoading] = useState(true);
  const [doctorsErr, setDoctorsErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');

  const authHeader = () => ({ headers: { Authorization: `Bearer ${sessionStorage.getItem('token')}` } });

  const loadDoctors = async () => {
    setDoctorsLoading(true);
    setDoctorsErr('');
    try {
      const res = await axios.get(`${API}/reception/doctors`, authHeader());
      setDoctors(Array.isArray(res.data) ? res.data : []);
    } catch (e) {
      setDoctorsErr(errorMessage(e, 'Could not load the doctor list.'));
    } finally {
      setDoctorsLoading(false);
    }
  };

  useEffect(() => { loadDoctors(); }, []);

  const checkIn = async (e) => {
    e.preventDefault();
    setMsg(''); setErr('');
    if (!doctorId) {
      setErr('Select a doctor.');
      return;
    }
    const id = careId.trim().toUpperCase();
    if (!CARE_ID_RE.test(id)) {
      setErr('A CareID looks like CID-7K3MQ9.');
      return;
    }
    setBusy(true);
    try {
      const res = await axios.post(`${API}/reception/check-in`, { careId: id, doctorId }, authHeader());
      const patient = res.data?.patientName || res.data?.name || id;
      const chosen = doctors.find((d) => d.id === doctorId);
      setMsg(`${patient} is checked in for ${drName(res.data?.doctorName || chosen?.name)}.`);
      setCareId('');
    } catch (e2) {
      setErr(checkInMessage(e2));
      if (e2.response?.data?.error === 'INVALID_DOCTOR') {
        setDoctorId('');
        loadDoctors();
      }
    } finally {
      setBusy(false);
    }
  };

  const signOut = () => {
    sessionStorage.clear();
    navigate('/');
  };

  return (
    <div className="mt-shell">
      <div className="mt-main">
        <div className="mt-page">
          <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
            <h1 style={{ fontSize: 18, margin: 0, color: 'var(--c-navy)' }}>Reception</h1>
            <button className="mt-btn mt-btn-outline" onClick={signOut}>Sign out</button>
          </div>

          <div className="mt-card">
            <form onSubmit={checkIn}>
              <label className="mt-label" htmlFor="reception-doctor">Doctor</label>
              <select
                id="reception-doctor"
                className="mt-select"
                style={{ marginBottom: 12 }}
                value={doctorId}
                onChange={(e) => setDoctorId(e.target.value)}
                disabled={doctorsLoading}
                required
              >
                <option value="">{doctorsLoading ? 'Loading doctors…' : 'Select a doctor'}</option>
                {doctors.map((d) => <option key={d.id} value={d.id}>{drName(d.name)}</option>)}
              </select>
              {doctorsErr && (
                <div className="alert-box alert-red" role="alert">
                  {doctorsErr}{' '}
                  <button type="button" className="mt-btn mt-btn-outline" style={{ padding: '2px 8px', fontSize: 11 }} onClick={loadDoctors}>Try again</button>
                </div>
              )}
              {!doctorsLoading && !doctorsErr && doctors.length === 0 && (
                <div className="alert-box alert-orange">No verified doctors are available yet.</div>
              )}

              <label className="mt-label" htmlFor="reception-careid">Patient CareID</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <input
                  id="reception-careid"
                  className="mt-input"
                  style={{ flex: 1 }}
                  placeholder="e.g. CID-7K3MQ9"
                  value={careId}
                  onChange={(e) => setCareId(e.target.value)}
                />
                <button className="mt-btn mt-btn-orange" type="submit" disabled={busy || !careId.trim() || !doctorId}>
                  {busy ? <><Spinner /> Checking in…</> : 'Check in'}
                </button>
              </div>
            </form>
            {msg && <div className="alert-box alert-green" role="status" style={{ marginTop: 12 }}>{msg}</div>}
            {err && <div className="alert-box alert-red" role="alert" style={{ marginTop: 12 }}>{err}</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

export default ReceptionDashboard;
