import { API } from '../config';
import { useState, useEffect } from 'react';
import axios from 'axios';
import './Shared.css';

function Pharmacy() {
  const pharmacyId = 1; // TODO: replace with logged-in pharmacy staff's actual pharmacy_id
  const [stock, setStock] = useState([]);
  const [prescriptionId, setPrescriptionId] = useState('');
  const [prescription, setPrescription] = useState(null);
  const [quantity, setQuantity] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [recentPrescriptions, setRecentPrescriptions] = useState([]);

  const token = localStorage.getItem('token');
  const authHeader = { headers: { Authorization: `Bearer ${token}` } };

  const fetchStock = async () => {
    try {
      const res = await axios.get(`${API}/pharmacy/stock/${pharmacyId}`, authHeader);
      setStock(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchRecent = async () => {
    try {
      const res = await axios.get(`${API}/prescription/recent`, authHeader);
      setRecentPrescriptions(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchStock();
    fetchRecent();
  }, []);

  const lookupPrescription = async () => {
    setError('');
    setMessage('');
    setPrescription(null);
    try {
      const res = await axios.get(`${API}/pharmacy/prescription/${prescriptionId}`, authHeader);
      setPrescription(res.data);
    } catch (err) {
      setError('Prescription not found.');
    }
  };

  const handleDispense = async () => {
    setError('');
    setMessage('');
    setLoading(true);
    try {
      const res = await axios.post(`${API}/pharmacy/dispense`, {
        prescriptionId: prescription.id,
        pharmacyId,
        drug: prescription.drug,
        quantity: parseInt(quantity, 10),
      }, authHeader);

      setMessage(`Dispensed ${quantity} unit(s) of ${prescription.drug}.`);
      setPrescription(null);
      setPrescriptionId('');
      setQuantity('');
      fetchStock();
      fetchRecent();
    } catch (err) {
      setError(err.response?.data || 'Failed to dispense.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Pharmacy Portal</h1>
          <p>Thulasi · Dispensing</p>
        </div>
        <button className="btn btn-secondary" onClick={() => { fetchStock(); fetchRecent(); }}>
          Refresh
        </button>
      </div>

      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
        <div className="card" style={{ flex: 1, minWidth: 320 }}>
          <h3 style={{ marginTop: 0, color: '#123c47' }}>Lookup Prescription</h3>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1rem' }}>
            <input
              className="plain"
              style={{ flex: 1 }}
              type="number"
              placeholder="Prescription ID"
              value={prescriptionId}
              onChange={(e) => setPrescriptionId(e.target.value)}
            />
            <button className="btn" onClick={lookupPrescription}>Lookup</button>
          </div>

          {error && <p style={{ color: '#c0392b', fontSize: '0.85rem' }}>{String(error)}</p>}
          {message && <p style={{ color: '#0e7c86', fontSize: '0.85rem' }}>{message}</p>}

          {prescription && (
            <div style={{ background: '#f8fbfb', padding: '1rem', borderRadius: '8px' }}>
              <p><strong>Patient:</strong> {prescription.careId}</p>
              <p><strong>Drug:</strong> {prescription.drug}</p>
              <p><strong>Dose:</strong> {prescription.dose}</p>
              <p><strong>Duration:</strong> {prescription.duration}</p>
              <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
                <input
                  className="plain"
                  style={{ flex: 1 }}
                  type="number"
                  placeholder="Quantity to dispense"
                  value={quantity}
                  onChange={(e) => setQuantity(e.target.value)}
                />
                <button className="btn" onClick={handleDispense} disabled={loading || !quantity}>
                  {loading ? 'Dispensing...' : 'Dispense'}
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="card" style={{ flex: 1, minWidth: 320 }}>
          <h3 style={{ marginTop: 0, color: '#123c47' }}>Recent Prescriptions</h3>
          {recentPrescriptions.length === 0 ? (
            <p style={{ color: '#9db3b8' }}>No recent prescriptions.</p>
          ) : (
            recentPrescriptions.map((p) => (
              <div
                key={p.id}
                className="queue-item"
                style={{ cursor: 'pointer' }}
                onClick={() => setPrescriptionId(String(p.id))}
              >
                <div>
                  <strong>#{p.id} — {p.drug}</strong>
                  <p style={{ margin: '0.2rem 0 0', fontSize: '0.8rem', color: '#6b8a92' }}>
                    {p.careId}
                  </p>
                </div>
              </div>
            ))
          )}
        </div>

        <div className="card" style={{ flex: 1, minWidth: 320 }}>
          <h3 style={{ marginTop: 0, color: '#123c47' }}>Current Stock</h3>
          {stock.length === 0 ? (
            <p style={{ color: '#9db3b8' }}>No stock data.</p>
          ) : (
            stock.map((item) => (
              <div key={item.id} className="queue-item">
                <span>{item.drug}</span>
                <strong>{item.quantity} units</strong>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

export default Pharmacy;