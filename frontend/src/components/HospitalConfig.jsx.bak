import { useState, useEffect } from 'react';
import axios from 'axios';
import { decodeToken } from '../utils/jwt';
import { API } from '../config';
import './Shared.css';

function HospitalConfig() {
  const [doctors, setDoctors] = useState([]);
  const [pendingHospitals, setPendingHospitals] = useState([]);
  const [pendingPharmacies, setPendingPharmacies] = useState([]);
  const [verifyMsg, setVerifyMsg] = useState('');
  const [verifyErr, setVerifyErr] = useState('');

  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();
  const hospitalId = user?.facilityId;
  const adminUsername = user?.username;

  const fetchDoctors = async () => {
    try {
      const r = await axios.get(`${API}/staff/doctors/${hospitalId}`, auth);
      setDoctors(r.data);
    } catch (e) { console.error(e); }
  };

  const fetchPendingFacilities = async () => {
    try {
      const hRes = await axios.get(`${API}/hospital/list`);
      setPendingHospitals(hRes.data.filter(h => h.verificationStatus === 'PENDING'));
    } catch (e) { console.error(e); }
    try {
      const pRes = await axios.get(`${API}/pharmacy-facility/list`);
      setPendingPharmacies(pRes.data.filter(p => p.verificationStatus === 'PENDING'));
    } catch (e) { console.error(e); }
  };

  useEffect(() => {
    fetchDoctors();
    fetchPendingFacilities();
  }, []);

  const verifyHospital = async (id) => {
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/hospital/${id}/verify?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Hospital verified.');
      fetchPendingFacilities();
    } catch (e) {
      setVerifyErr(e.response?.data || 'Failed to verify hospital.');
    }
  };

  const rejectHospital = async (id) => {
    if (!window.confirm('Reject this hospital? This should only be done if the registration details are invalid.')) return;
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/hospital/${id}/reject?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Hospital rejected.');
      fetchPendingFacilities();
    } catch (e) {
      setVerifyErr(e.response?.data || 'Failed to reject hospital.');
    }
  };

  const verifyPharmacy = async (id) => {
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/pharmacy-facility/${id}/verify?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Pharmacy verified.');
      fetchPendingFacilities();
    } catch (e) {
      setVerifyErr(e.response?.data || 'Failed to verify pharmacy.');
    }
  };

  const rejectPharmacy = async (id) => {
    if (!window.confirm('Reject this pharmacy? This should only be done if the registration details are invalid.')) return;
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/pharmacy-facility/${id}/reject?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Pharmacy rejected.');
      fetchPendingFacilities();
    } catch (e) {
      setVerifyErr(e.response?.data || 'Failed to reject pharmacy.');
    }
  };

  return (
    <div className="page" style={{ minHeight: '100vh', padding: '2rem' }}>
      <div className="page-header">
        <div>
          <h1>Hospital Configuration</h1>
          <p>{user?.facilityName || 'Hospital'} · Admin oversight</p>
        </div>
      </div>

      {verifyMsg && <p style={{ color: '#1D9E75', fontSize: '0.85rem' }}>{verifyMsg}</p>}
      {verifyErr && <p style={{ color: '#A32D2D', fontSize: '0.85rem' }}>{String(verifyErr)}</p>}

      {/* Pending facility verification */}
      <div className="card" style={{ maxWidth: 640, marginBottom: '1.5rem' }}>
        <h3 style={{ marginTop: 0, fontSize: '1rem' }}>Pending facility verification</h3>
        <p style={{ fontSize: '0.8rem', color: '#9db3b8', marginTop: '-0.5rem' }}>
          Check each registration number against real state/government records before verifying.
        </p>

        {pendingHospitals.length === 0 && pendingPharmacies.length === 0 ? (
          <p style={{ fontSize: '0.85rem', color: '#9db3b8' }}>No facilities awaiting verification.</p>
        ) : (
          <>
            {pendingHospitals.map(h => (
              <div key={`h-${h.id}`} style={{
                border: '1px solid #eee', borderRadius: 8, padding: '0.75rem',
                marginBottom: '0.6rem',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <strong>{h.name}</strong> <span style={{ fontSize: '0.7rem', color: '#9db3b8' }}>(Hospital)</span>
                    <div style={{ fontSize: '0.75rem', color: '#5B6B79', marginTop: 2 }}>
                      Reg #: {h.registrationNumber || '—'} {h.gstin ? `· GSTIN: ${h.gstin}` : ''}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button className="btn" style={{ padding: '0.35rem 0.8rem', fontSize: '0.8rem' }} onClick={() => verifyHospital(h.id)}>
                      Verify
                    </button>
                    <button className="btn" style={{ padding: '0.35rem 0.8rem', fontSize: '0.8rem', background: '#A32D2D' }} onClick={() => rejectHospital(h.id)}>
                      Reject
                    </button>
                  </div>
                </div>
              </div>
            ))}

            {pendingPharmacies.map(p => (
              <div key={`p-${p.id}`} style={{
                border: '1px solid #eee', borderRadius: 8, padding: '0.75rem',
                marginBottom: '0.6rem',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <strong>{p.name}</strong> <span style={{ fontSize: '0.7rem', color: '#9db3b8' }}>(Pharmacy)</span>
                    <div style={{ fontSize: '0.75rem', color: '#5B6B79', marginTop: 2 }}>
                      Drug License #: {p.drugLicenseNumber || '—'} {p.gstin ? `· GSTIN: ${p.gstin}` : ''}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button className="btn" style={{ padding: '0.35rem 0.8rem', fontSize: '0.8rem' }} onClick={() => verifyPharmacy(p.id)}>
                      Verify
                    </button>
                    <button className="btn" style={{ padding: '0.35rem 0.8rem', fontSize: '0.8rem', background: '#A32D2D' }} onClick={() => rejectPharmacy(p.id)}>
                      Reject
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </>
        )}
      </div>

      <div className="card" style={{ maxWidth: 640 }}>
        <h3 style={{ marginTop: 0, fontSize: '1rem' }}>Doctor availability</h3>
        <p style={{ fontSize: '0.8rem', color: '#9db3b8', marginTop: '-0.5rem' }}>
          Doctors manage their own leave — this is a read-only view.
        </p>
        {doctors.length === 0 ? (
          <p style={{ fontSize: '0.85rem', color: '#9db3b8' }}>No doctors found for this hospital.</p>
        ) : doctors.map(doc => (
          <div key={doc.id} style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '0.6rem 0', borderBottom: '1px solid #eee',
          }}>
            <div>
              <strong>{doc.fullName}</strong>
              {!doc.available && (
                <div style={{ fontSize: '0.75rem', color: '#A32D2D' }}>
                  On leave until {doc.leaveUntil} {doc.reason ? `· ${doc.reason}` : ''}
                  {doc.substituteName ? ` — Substitute: Dr. ${doc.substituteName}` : ''}
                </div>
              )}
            </div>
            <span style={{
              fontSize: '0.75rem', fontWeight: 600,
              color: doc.available ? '#1D9E75' : '#A32D2D',
            }}>
              {doc.available ? '● Available' : '● On leave'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

export default HospitalConfig;