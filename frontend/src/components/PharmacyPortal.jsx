import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { Html5Qrcode } from 'html5-qrcode';
import { decodeToken } from '../utils/jwt';

import { API } from '../config';

function PharmacyPortal() {
  const pharmacyId = decodeToken()?.facilityId;
  const [tab, setTab] = useState('scan');
  const [incoming, setIncoming] = useState([]);
  const [log, setLog] = useState({ entries: [], dispensedCount: 0, rejectedCount: 0, pendingCount: 0 });
  const [prescriptionId, setPrescriptionId] = useState('');
  const [prescription, setPrescription] = useState(null);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState('');
  const [scanning, setScanning] = useState(false);
  const [now, setNow] = useState(new Date());
  const [searchInput, setSearchInput] = useState('');
  const [activeGroups, setActiveGroups] = useState([]);

  const scannerRef = useRef(null);
  const token = sessionStorage.getItem('token');
  const auth = { headers: { Authorization: `Bearer ${token}` } };
  const user = decodeToken();

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  const fetchIncoming = async () => {
    try {
      const r = await axios.get(`${API}/prescription/for-pharmacy/${pharmacyId}`, auth);
      setIncoming(r.data.filter(p => p.status === 'ISSUED'));
    } catch (e) { console.error(e); }
  };

  const fetchLog = async () => {
    try {
      const r = await axios.get(`${API}/pharmacy/log/${pharmacyId}`, auth);
      setLog(r.data);
    } catch (e) { console.error(e); }
  };

  useEffect(() => {
    fetchIncoming();
  }, []);

  const lookup = async (idOrQr) => {
    setErr(''); setMsg(''); setPrescription(null); setActiveGroups([]);
    const value = idOrQr || prescriptionId;
    try {
      const r = await axios.get(`${API}/prescription/group/${value}`, auth);
      setPrescription(r.data);
      if (idOrQr) setPrescriptionId(String(idOrQr));
    } catch {
      setErr('Prescription group not found.');
    }
  };

  const searchPrescriptions = async () => {
    setErr(''); setMsg(''); setPrescription(null); setActiveGroups([]);
    const value = searchInput.trim();
    if (!value) return;

    // Numbers-only and short → treat as a Group ID
    if (/^\d+$/.test(value) && value.length <= 5) {
      try {
        const r = await axios.get(`${API}/prescription/group/${value}`, auth);
        setPrescription(r.data);
        return;
      } catch { /* fall through to CareID search */ }
    }

    // Otherwise treat as a CareID — list all active prescriptions for that patient
    try {
      const r = await axios.get(`${API}/prescription/for-patient/${value}/active`, auth);
      if (r.data.length === 0) {
        setErr('No active prescriptions found for this CareID.');
      } else if (r.data.length === 1) {
        setPrescription(r.data[0]);
      } else {
        setActiveGroups(r.data);
      }
    } catch {
      setErr('Nothing found for that ID or CareID.');
    }
  };

  const dispense = async () => {
    setErr(''); setMsg('');
    try {
      await axios.post(`${API}/prescription/group/${prescription.groupId}/dispense-full`, {}, auth);
      setMsg(`Dispensed all ${prescription.drugs.length} drug(s) for ${prescription.careId}. Prescription closed.`);
      setPrescription(null); setPrescriptionId(''); setSearchInput('');
      fetchIncoming();
    } catch (e) { setErr(e.response?.data || 'Failed to dispense.'); }
  };

  const startScan = async () => {
    setErr(''); setMsg(''); setPrescription(null);
    setScanning(true);
    const scanner = new Html5Qrcode('qr-reader');
    scannerRef.current = scanner;
    try {
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: 220 },
        async (decodedText) => {
          await scanner.stop();
          setScanning(false);
          setTab('lookup');

          // Universal per-patient QR (CareID-based) is now the primary lookup path,
          // matching the patient app's one-QR-per-patient design.
          try {
            const r = await axios.get(`${API}/prescription/for-patient/${decodedText}/active`, auth);
            if (r.data.length === 0) {
              setErr('No active prescriptions found for this patient.');
            } else if (r.data.length === 1) {
              setPrescription(r.data[0]);
            } else {
              setActiveGroups(r.data);
            }
            return;
          } catch { /* fall through — might be a legacy single-prescription QR */ }

          // Fallback for any older UUID-based QR still in circulation
          try {
            const r = await axios.get(`${API}/prescription/by-qr/${decodedText}`, auth);
            setPrescription(r.data);
          } catch {
            setErr('Could not find a matching prescription for this QR code.');
          }
        },
        () => {}
      );
    } catch {
      setErr('Camera unavailable. Use manual lookup instead.');
      setScanning(false);
    }
  };

  const stopScan = async () => {
    if (scannerRef.current) {
      try { await scannerRef.current.stop(); } catch { /* ignore */ }
    }
    setScanning(false);
  };

  const initials = (s) => (s || '?').slice(0, 2).toUpperCase();
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
  const dateStrShort = now.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

  const navItems = [
    { key: 'scan', label: 'Scan QR' },
    { key: 'lookup', label: 'Lookup' },
    { key: 'incoming', label: 'Incoming Rx' },
    { key: 'log', label: 'Log' },
  ];

  const goTab = (t) => {
    setTab(t);
    if (t === 'incoming') fetchIncoming();
    if (t === 'log') fetchLog();
  };

  return (
    <div className="mt-shell">
      <div className="mt-sidebar">
        <div className="mt-sidebar-brand">{user?.facilityName || 'Unassigned'}</div>
        <div className="mt-sidebar-sub">{user?.username}</div>

        <div className="mt-sidebar-nav">
          {navItems.map(item => (
            <button
              key={item.key}
              className={`mt-sidebar-item ${tab === item.key ? 'active' : ''}`}
              onClick={() => goTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="mt-sidebar-footer">
          <div className="avatar" style={{ background: 'rgba(201, 123, 46, 0.25)', color: '#F5D9B8', width: 30, height: 30 }}>
            {initials(user?.username)}
          </div>
          <div style={{ textAlign: 'right' }}>
            <div className="mt-sidebar-time">{timeStr}</div>
            <div className="mt-sidebar-date">{dateStrShort}</div>
          </div>
        </div>
      </div>

      <div className="mt-main">
        <div className="mt-page">
          {tab === 'scan' && (
            <div className="mt-card" style={{ textAlign: 'center' }}>
              <div className="mt-card-title" style={{ justifyContent: 'center' }}>Scan patient's QR code</div>
              <div id="qr-reader" style={{ maxWidth: 280, margin: '0 auto 10px' }} />
              {!scanning ? (
                <button className="mt-btn mt-btn-orange" onClick={startScan}>Start camera</button>
              ) : (
                <button className="mt-btn mt-btn-outline" onClick={stopScan}>Stop</button>
              )}
              {err && <div className="alert-box alert-red" style={{ marginTop: 10, textAlign: 'left' }}>{String(err)}</div>}
              <p style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 8 }}>
                No camera? Use the Lookup tab with the prescription ID.
              </p>
            </div>
          )}

          {tab === 'lookup' && (
            <div className="mt-card">
              <div className="mt-card-title">Lookup prescription</div>
              <label className="mt-label">Group ID or Patient CareID</label>
              <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                <input
                  className="mt-input"
                  style={{ flex: 1 }}
                  placeholder="e.g. 49 or 12345671234567"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                />
                <button className="mt-btn mt-btn-orange" onClick={searchPrescriptions}>Search</button>
              </div>
              {err && <div className="alert-box alert-red">{String(err)}</div>}
              {msg && <div className="alert-box alert-green">{msg}</div>}

              {activeGroups.length > 0 && (
                <div style={{ marginBottom: 12 }}>
                  <div className="mt-label" style={{ marginBottom: 6 }}>Multiple active prescriptions found — select one</div>
                  {activeGroups.map(g => (
                    <div key={g.groupId} className="prow" style={{ cursor: 'pointer' }} onClick={() => { setPrescription(g); setActiveGroups([]); }}>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 12, fontWeight: 600 }}>Group #{g.groupId} — {g.drugs.length} drug(s)</div>
                        <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                          {g.drugs.map(d => d.drug).join(', ')} · {g.issuedAt ? new Date(g.issuedAt).toLocaleDateString() : ''}
                        </div>
                      </div>
                      <span className="badge badge-blue">ISSUED</span>
                    </div>
                  ))}
                </div>
              )}

              {prescription && (
                <div className="mt-card" style={{
                  background: 'var(--c-green-bg)',
                  border: '1px solid #5DCAA5',
                  padding: 14,
                }}>
                  <div style={{ textAlign: 'center', marginBottom: 10 }}>
                    <div style={{ fontSize: 26 }}>🛡️</div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--c-green-dark)', marginTop: 4, fontFamily: "'Space Grotesk', sans-serif" }}>
                      Verified — valid prescription
                    </div>
                    <div style={{ fontSize: 10, color: '#3B6D11', marginTop: 2 }}>Status: {prescription.status}</div>
                  </div>

                  <div style={{ height: 1, background: 'rgba(0,0,0,0.06)', margin: '10px 0' }} />

                  <div style={{ marginBottom: 12 }}>
                    <div className="mt-label">Patient</div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{prescription.careId}</div>
                  </div>

                  <div className="mt-label" style={{ marginBottom: 6 }}>Drugs in this prescription</div>
                  {prescription.drugs.map(d => (
                    <div key={d.id} style={{
                      background: '#fff',
                      borderRadius: 8,
                      padding: '8px 10px',
                      marginBottom: 6,
                    }}>
                      <strong style={{ fontSize: 12 }}>{d.drug}</strong>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{d.dose} · {d.duration}</div>
                    </div>
                  ))}

                  <button
                    className="mt-btn mt-btn-green"
                    style={{ width: '100%', marginTop: 8 }}
                    onClick={dispense}
                    disabled={prescription.status !== 'ISSUED'}
                  >
                    Confirm — dispense all drugs
                  </button>
                </div>
              )}
            </div>
          )}

          {tab === 'incoming' && (
            <div className="mt-card">
              <div className="mt-card-title">Prescriptions routed to this pharmacy</div>
              {incoming.length === 0 ? (
                <p style={{ fontSize: 11, color: 'var(--text-muted)' }}>No pending prescriptions.</p>
              ) : incoming.map(p => (
                <div key={p.id} className="prow" style={{ cursor: 'pointer' }} onClick={() => { setTab('lookup'); lookup(p.id); }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 11, fontWeight: 600 }}>#{p.id} — {p.drug}</div>
                    <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{p.careId} · {p.dose}</div>
                  </div>
                  <span className="badge badge-blue">ISSUED</span>
                </div>
              ))}
            </div>
          )}

          {tab === 'log' && (
            <div className="mt-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <div className="mt-card-title" style={{ marginBottom: 0 }}>Dispensing log</div>
                <button className="mt-btn mt-btn-outline" style={{ fontSize: 10, padding: '4px 10px' }} onClick={fetchLog}>
                  ↻ Refresh
                </button>
              </div>

              <div className="stat-grid">
                <div className="stat-box">
                  <div className="stat-num" style={{ color: 'var(--c-green)' }}>{log.dispensedCount}</div>
                  <div className="stat-label">Dispensed</div>
                </div>
                <div className="stat-box">
                  <div className="stat-num" style={{ color: 'var(--c-red)' }}>{log.rejectedCount}</div>
                  <div className="stat-label">Rejected</div>
                </div>
                <div className="stat-box">
                  <div className="stat-num" style={{ color: 'var(--c-amber-dark)' }}>{log.pendingCount}</div>
                  <div className="stat-label">Pending</div>
                </div>
              </div>

              {log.entries?.length === 0 ? (
                <p style={{ fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', padding: '1.5rem 0' }}>No activity yet.</p>
              ) : log.entries?.map(entry => (
                <div key={entry.id} className="prow">
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 11, fontWeight: 600 }}>{entry.drug} {entry.careId ? `— ${entry.careId}` : ''}</div>
                    <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                      {entry.status === 'DISPENSED' ? `Qty: ${entry.qtyDispensed}` : entry.rejectReason} · {entry.dispensedAt ? new Date(entry.dispensedAt).toLocaleString() : ''}
                    </div>
                  </div>
                  <span className={`badge ${entry.status === 'DISPENSED' ? 'badge-green' : 'badge-red'}`}>
                    {entry.status === 'DISPENSED' ? 'Done' : 'Rejected'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default PharmacyPortal;