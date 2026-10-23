import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

// Placeholder: a reception account is a front-desk login only. It has no patient data, prescribing or dispensing,
// so the search is not wired up yet.
function ReceptionDashboard() {
  const navigate = useNavigate();
  const [careId, setCareId] = useState('');

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
              <button className="mt-btn mt-btn-orange" disabled>Find patient</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default ReceptionDashboard;
