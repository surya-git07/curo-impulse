import { API } from '../config';
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Shared.css';

function Prescription() {
  const { careId } = useParams();
  const navigate = useNavigate();

  const [drug, setDrug] = useState('');
  const [dose, setDose] = useState('');
  const [duration, setDuration] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [blocked, setBlocked] = useState(false);
  const [loading, setLoading] = useState(false);
  const [riskScore, setRiskScore] = useState(null);
  const [checkingRisk, setCheckingRisk] = useState(false);

  const checkRisk = async () => {
    if (!drug) return;
    setCheckingRisk(true);
    const token = localStorage.getItem('token');
    try {
      const res = await axios.post(`${API}/ai/risk-score`, {
        drug,
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setRiskScore(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setCheckingRisk(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setMessage('');
    setError('');
    setBlocked(false);
    setLoading(true);

    const token = localStorage.getItem('token');

    try {
      const res = await axios.post(`${API}/prescription/issue`, {
        careId: careId,
        doctorId: 1, // TODO: replace with logged-in doctor's actual doctor_id
        drug,
        dose,
        duration,
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });

      setMessage(`Prescription issued: ${res.data.drug} for ${careId} — Prescription ID: ${res.data.id}`);
      setDrug('');
      setDose('');
      setDuration('');
      setRiskScore(null);
    } catch (err) {
      if (err.response?.status === 409) {
        setBlocked(true);
        setError(err.response.data);
      } else {
        setError('Failed to issue prescription.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Write Prescription</h1>
          <p>{careId}</p>
        </div>
        <button className="btn btn-secondary" onClick={() => navigate(`/doctor/patient/${careId}`)}>
          Back to Patient
        </button>
      </div>

      <div className="card" style={{ maxWidth: 460 }}>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#3d5c64', marginBottom: '0.4rem' }}>
              Drug Name
            </label>
            <input
              className="plain"
              style={{ width: '100%', boxSizing: 'border-box' }}
              type="text"
              placeholder="e.g. Amoxicillin"
              value={drug}
              onChange={(e) => { setDrug(e.target.value); setRiskScore(null); }}
              onBlur={checkRisk}
              required
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#3d5c64', marginBottom: '0.4rem' }}>
              Dose
            </label>
            <input
              className="plain"
              style={{ width: '100%', boxSizing: 'border-box' }}
              type="text"
              placeholder="e.g. 500mg twice daily"
              value={dose}
              onChange={(e) => setDose(e.target.value)}
              required
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, color: '#3d5c64', marginBottom: '0.4rem' }}>
              Duration
            </label>
            <input
              className="plain"
              style={{ width: '100%', boxSizing: 'border-box' }}
              type="text"
              placeholder="e.g. 7 days"
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              required
            />
          </div>

          {checkingRisk && (
            <p style={{ fontSize: '0.8rem', color: '#6b8a92' }}>Checking interactions...</p>
          )}

          {riskScore && (
            <div style={{
              padding: '0.75rem',
              borderRadius: '8px',
              background: riskScore.riskLevel === 'HIGH' ? '#fdecea' : '#eafaf1',
              border: `1px solid ${riskScore.riskLevel === 'HIGH' ? '#f5b7b1' : '#a9dfbf'}`,
            }}>
              <strong style={{ color: riskScore.riskLevel === 'HIGH' ? '#c0392b' : '#1e8449' }}>
                Interaction risk: {riskScore.riskLevel} ({(riskScore.riskScore * 100).toFixed(0)}%)
              </strong>
              <p style={{ margin: '0.3rem 0 0', fontSize: '0.8rem', color: '#555' }}>
                {riskScore.explanation}
              </p>
            </div>
          )}

          {message && (
            <p style={{ color: '#0e7c86', fontSize: '0.85rem' }}>{message}</p>
          )}

          {error && (
            <p style={{
              color: '#c0392b',
              background: blocked ? '#fdecea' : 'transparent',
              padding: blocked ? '0.6rem 0.8rem' : 0,
              borderRadius: '6px',
              fontSize: '0.85rem',
              fontWeight: blocked ? 600 : 400,
            }}>
              {blocked ? '⚠ ' : ''}{String(error)}
            </p>
          )}

          <button className="btn" type="submit" disabled={loading}>
            {loading ? 'Issuing...' : 'Issue Prescription'}
          </button>
        </form>
      </div>
    </div>
  );
}

export default Prescription;