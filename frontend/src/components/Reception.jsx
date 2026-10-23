import { API } from '../config';
import { useState } from 'react';
import axios from 'axios';
import './Shared.css';

function Reception() {
  const [careId, setCareId] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCheckIn = async (e) => {
    e.preventDefault();
    setMessage('');
    setError('');
    setLoading(true);

    try {
      const hospitalId = 1; // TODO: replace with logged-in staff's actual hospital_id
const token = localStorage.getItem('token');
await axios.post(`${API}/reception/checkin`, {
  careId,
  hospitalId,
}, {
  headers: { Authorization: `Bearer ${token}` }
});
      setMessage(`Patient ${careId} checked in successfully.`);
      setCareId('');
    } catch (err) {
      setError(err.response?.data || 'Check-in failed. Confirm the CareID is correct.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Reception Check-In</h1>
          <p>Kaveri Hospital</p>
        </div>
      </div>

      <div className="card" style={{ maxWidth: 420 }}>
        <form onSubmit={handleCheckIn} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#3d5c64', marginBottom: '0.4rem' }}>
              Patient CareID
            </label>
            <input
              className="plain"
              style={{ width: '100%', boxSizing: 'border-box' }}
              type="text"
              placeholder="e.g. CID-7K3MQ9"
              value={careId}
              onChange={(e) => setCareId(e.target.value)}
              required
            />
          </div>
          {message && <p style={{ color: '#0e7c86', fontSize: '0.85rem' }}>{message}</p>}
          {error && <p style={{ color: '#c0392b', fontSize: '0.85rem' }}>{String(error)}</p>}
          <button className="btn" type="submit" disabled={loading}>
            {loading ? 'Checking in...' : 'Check In Patient'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default Reception;