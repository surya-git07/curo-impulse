import { API } from '../config';
import { useEffect, useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import './Shared.css';

function DoctorQueue() {
  const [queue, setQueue] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const fetchQueue = async () => {
    try {
      const hospitalId = 1; // TODO: replace with logged-in doctor's actual hospital_id
     const token = localStorage.getItem('token');
     const res = await axios.get(`${API}/reception/queue/${hospitalId}`, {
       headers: { Authorization: `Bearer ${token}` }
     });
      setQueue(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

const removeVisit = async (visitId) => {
  const token = localStorage.getItem('token');
  try {
    await axios.delete(`${API}/reception/visit/${visitId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    fetchQueue();
  } catch (err) {
    console.error(err);
  }
};
  useEffect(() => {
    fetchQueue();
    const interval = setInterval(fetchQueue, 5000); // poll every 5s
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Doctor Queue</h1>
          <p>Kaveri Hospital · Waiting patients</p>
        </div>
        <button className="btn btn-secondary" onClick={fetchQueue}>Refresh</button>
      </div>

      <div className="card">
        {loading ? (
          <p>Loading queue...</p>
        ) : queue.length === 0 ? (
          <div className="empty-state">
            <p>No patients checked in yet.</p>
          </div>
        ) : (
          queue.map((visit) => (
            <div className="queue-item" key={visit.id}>
              <div>
                <strong>{visit.careId}</strong>
                <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: '#6b8a92' }}>
                  Checked in {new Date(visit.checkedInAt).toLocaleTimeString()}
                </p>
              </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <span className="status-badge status-waiting">WAITING</span>
              <button
                className="btn"
                onClick={() => navigate(`/doctor/patient/${encodeURIComponent(visit.careId)}`)}
              >
                View Patient
              </button>
              <button
                className="btn btn-secondary"
                onClick={() => removeVisit(visit.id)}
              >
                Remove
              </button>
            </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

export default DoctorQueue;