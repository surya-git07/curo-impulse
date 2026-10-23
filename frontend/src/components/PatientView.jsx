import { API } from '../config';
import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import './Shared.css';

function PatientView() {
  const { careId } = useParams();
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    axios.get(`${API}/patient/${careId}/summary`)
      .then((res) => setSummary(res.data))
      .catch(() => setError('Could not load patient summary.'))
      .finally(() => setLoading(false));
  }, [careId]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Patient Summary</h1>
          <p>{careId}</p>
        </div>
        <button className="btn" onClick={() => navigate(`/doctor/prescribe/${careId}`)}>Write Prescription</button>
        <button className="btn btn-secondary" onClick={() => navigate('/doctor')}>Back to Queue</button>
      </div>

      <div className="card" style={{ maxWidth: 520 }}>
        {loading ? (
          <p>Loading summary...</p>
        ) : error ? (
          <p style={{ color: '#c0392b' }}>{error}</p>
        ) : (
          <>
            <p><strong>Name:</strong> {summary.name}</p>
            <p><strong>CareID:</strong> {summary.careId}</p>
            <p><strong>DOB:</strong> {summary.dob}</p>
            <p><strong>Blood Group:</strong> {summary.bloodGroup}</p>
            <p><strong>Allergies:</strong> {Array.isArray(summary.allergies) ? (summary.allergies.length ? summary.allergies.join(', ') : 'None recorded') : (summary.allergies || 'None recorded')}</p>
            <p><strong>Conditions:</strong> {Array.isArray(summary.conditions) ? (summary.conditions.length ? summary.conditions.join(', ') : 'None recorded') : (summary.conditions || 'None recorded')}</p>
          </>
        )}
      </div>
    </div>
  );
}

export default PatientView;