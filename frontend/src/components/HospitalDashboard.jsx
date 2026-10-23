import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API } from '../config';
import { errorMessage } from '../api';
import { StatusBadge } from './ui';

// Placeholder: a hospital account is a facility login only. It shows who it is and whether the licence is verified.
// It has no patient data, prescribing or dispensing.
function HospitalDashboard() {
  const navigate = useNavigate();
  const [me, setMe] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    const token = sessionStorage.getItem('token');
    axios.get(`${API}/auth/me`, { headers: { Authorization: `Bearer ${token}` } })
      .then((res) => setMe(res.data))
      .catch((e) => setErr(errorMessage(e, 'Could not load your account.')));
  }, []);

  const signOut = () => {
    sessionStorage.clear();
    navigate('/');
  };

  return (
    <div className="mt-shell">
      <div className="mt-main">
        <div className="mt-page">
          <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
            <div>
              <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--c-navy)' }}>Hospital dashboard</div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>Placeholder. More tools for hospitals will appear here.</div>
            </div>
            <button className="mt-btn mt-btn-outline" onClick={signOut}>Sign out</button>
          </div>

          {err && <div className="alert-box alert-red">{err}</div>}

          {me && (
            <div className="mt-card">
              <div className="mt-card-title">Your hospital</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div><div className="mt-label">Hospital name</div><div style={{ fontSize: 13 }}>{me.facilityName || me.name}</div></div>
                <div><div className="mt-label">Facility ID</div><div style={{ fontSize: 13, fontFamily: 'monospace', wordBreak: 'break-all' }}>{me.facilityId || 'Not assigned'}</div></div>
                <div>
                  <div className="mt-label">Verification</div>
                  <StatusBadge status={me.verificationStatus || 'PENDING'} />
                </div>
              </div>
              {me.verificationStatus !== 'VERIFIED' && (
                <div className="alert-box alert-orange" style={{ marginTop: 10 }}>
                  {me.verificationReason || 'A reviewer will check your licence.'}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default HospitalDashboard;
