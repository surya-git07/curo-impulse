import { useState, useEffect } from 'react';
import axios from 'axios';
import TopBar from './TopBar';
import { decodeToken } from '../utils/jwt';
import { API } from '../config';

function HospitalConfig() {
  const [doctors, setDoctors] = useState([]);
  const [pendingHospitals, setPendingHospitals] = useState([]);
  const [pendingPharmacies, setPendingPharmacies] = useState([]);
  const [verifyMsg, setVerifyMsg] = useState('');
  const [verifyErr, setVerifyErr] = useState('');
  const [now, setNow] = useState(new Date());
  const [refreshing, setRefreshing] = useState(false);

  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();
  const hospitalId = user?.facilityId;
  const adminUsername = user?.username;
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

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
    if (isSuperAdmin) fetchPendingFacilities();
    else fetchDoctors();
  }, []);

  const refresh = async () => {
    setRefreshing(true);
    if (isSuperAdmin) await fetchPendingFacilities();
    else await fetchDoctors();
    setTimeout(() => setRefreshing(false), 400);
  };

  const verifyHospital = async (id) => {
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/hospital/${id}/verify?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Hospital verified.');
      fetchPendingFacilities();
    } catch (e) { setVerifyErr(e.response?.data || 'Failed to verify hospital.'); }
  };

  const rejectHospital = async (id) => {
    if (!window.confirm('Reject this hospital? This should only be done if the registration details are invalid.')) return;
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/hospital/${id}/reject?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Hospital rejected.');
      fetchPendingFacilities();
    } catch (e) { setVerifyErr(e.response?.data || 'Failed to reject hospital.'); }
  };

  const verifyPharmacy = async (id) => {
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/pharmacy-facility/${id}/verify?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Pharmacy verified.');
      fetchPendingFacilities();
    } catch (e) { setVerifyErr(e.response?.data || 'Failed to verify pharmacy.'); }
  };

  const rejectPharmacy = async (id) => {
    if (!window.confirm('Reject this pharmacy? This should only be done if the registration details are invalid.')) return;
    setVerifyMsg(''); setVerifyErr('');
    try {
      await axios.post(`${API}/pharmacy-facility/${id}/reject?adminUsername=${encodeURIComponent(adminUsername)}`, {}, auth);
      setVerifyMsg('Pharmacy rejected.');
      fetchPendingFacilities();
    } catch (e) { setVerifyErr(e.response?.data || 'Failed to reject pharmacy.'); }
  };

  const dateStr = now.toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  return (
    <div>
      <TopBar />
      <div className="mt-page">
        <div className="mt-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10 }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--c-navy)', fontFamily: "'Space Grotesk', sans-serif" }}>
              Welcome, {isSuperAdmin ? 'Super Admin' : 'Admin'}
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>{dateStr}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 18, fontWeight: 700, fontFamily: "'Space Grotesk', sans-serif", color: 'var(--c-amber-dark)' }}>
              {timeStr}
            </div>
            <button className="mt-btn mt-btn-outline" style={{ fontSize: 10, padding: '4px 10px', marginTop: 4 }} onClick={refresh} disabled={refreshing}>
              {refreshing ? 'Refreshing…' : '↻ Refresh'}
            </button>
          </div>
        </div>

        {(verifyMsg || verifyErr) && (
          <div className={`alert-box ${verifyErr ? 'alert-red' : 'alert-green'}`}>
            {verifyErr ? String(verifyErr) : verifyMsg}
          </div>
        )}

        {isSuperAdmin && (
          <div className="mt-card">
            <div className="mt-card-title">🛡 Pending facility verification</div>
            <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: -6, marginBottom: 10 }}>
              Check each registration number against real state/government records before verifying.
            </p>

            {pendingHospitals.length === 0 && pendingPharmacies.length === 0 ? (
              <p style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', padding: '1.5rem 0' }}>
                No facilities awaiting verification.
              </p>
            ) : (
              <>
                {pendingHospitals.map(h => (
                  <div key={`h-${h.id}`} className="prow">
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 12, fontWeight: 600 }}>
                        {h.name} <span className="badge badge-gray" style={{ marginLeft: 6 }}>Hospital</span>
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                        Reg #: {h.registrationNumber || '—'} {h.gstin ? `· GSTIN: ${h.gstin}` : ''}
                      </div>
                    </div>
                    <button className="mt-btn mt-btn-green" style={{ padding: '4px 12px', fontSize: 11, marginRight: 6 }} onClick={() => verifyHospital(h.id)}>
                      Verify
                    </button>
                    <button className="mt-btn mt-btn-red" style={{ padding: '4px 12px', fontSize: 11 }} onClick={() => rejectHospital(h.id)}>
                      Reject
                    </button>
                  </div>
                ))}

                {pendingPharmacies.map(p => (
                  <div key={`p-${p.id}`} className="prow">
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 12, fontWeight: 600 }}>
                        {p.name} <span className="badge badge-gray" style={{ marginLeft: 6 }}>Pharmacy</span>
                      </div>
                      <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                        Drug License #: {p.drugLicenseNumber || '—'} {p.gstin ? `· GSTIN: ${p.gstin}` : ''}
                      </div>
                    </div>
                    <button className="mt-btn mt-btn-green" style={{ padding: '4px 12px', fontSize: 11, marginRight: 6 }} onClick={() => verifyPharmacy(p.id)}>
                      Verify
                    </button>
                    <button className="mt-btn mt-btn-red" style={{ padding: '4px 12px', fontSize: 11 }} onClick={() => rejectPharmacy(p.id)}>
                      Reject
                    </button>
                  </div>
                ))}
              </>
            )}
          </div>
        )}

        {!isSuperAdmin && (
          <div className="mt-card">
            <div className="mt-card-title">👨‍⚕️ Doctor availability</div>
            {doctors.length === 0 ? (
              <p style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'center', padding: '1.5rem 0' }}>
                No doctors found for this hospital.
              </p>
            ) : doctors.map(doc => (
              <div key={doc.id} className="prow">
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 12, fontWeight: 600 }}>{doc.name}</div>
                  {!doc.available && (
                    <div style={{ fontSize: 10, color: 'var(--c-red)' }}>
                      On leave until {doc.leaveUntil} {doc.reason ? `· ${doc.reason}` : ''}
                      {doc.substituteName ? ` · Substitute: Dr. ${doc.substituteName}` : ''}
                    </div>
                  )}
                </div>
                <span className={`badge ${doc.available ? 'badge-green' : 'badge-red'}`}>
                  {doc.available ? 'Available' : 'On leave'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default HospitalConfig;