import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { API } from '../config';
import { login, signup, errorMessage } from '../api';
import './Login.css';

const typeLabels = {
  private: 'Private Hospital',
  government: 'Government Hospital',
  pharmacy: 'Retail Pharmacy',
  clinic: 'Retail Clinic',
};

const typeIcons = {
  private: 'ti ti-building-hospital',
  government: 'ti ti-building-bank',
  pharmacy: 'ti ti-prescription',
  clinic: 'ti ti-stethoscope',
};

const typeDescriptions = {
  private: 'Sign in to manage your hospital\'s doctors, reception, and pharmacy operations.',
  government: 'Access your government facility\'s staff portal for patient care and prescriptions.',
  pharmacy: 'Sign in to manage your pharmacy\'s inventory and fulfill prescriptions.',
  clinic: 'Access your clinic\'s consultation and prescription tools.',
};

const pathwaySteps = ['Reception', 'Doctor', 'Pharmacy', 'Patient'];

// Hospital-type facilities use /hospital/*, pharmacy/clinic use /pharmacy-facility/*
const isHospitalType = (type) => type === 'private' || type === 'government';

const emptyStaff = (hospitalType) => ({
  name: '', phone: '', password: '', role: hospitalType ? 'DOCTOR' : 'PHARMACY', facilityId: '',
  licenceNumber: '', stateCouncil: '',
  drugLicenceNumber: '', gstin: '', state: '', address: '',
});

function Entry() {
  const { facilityType } = useParams();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const hospitalType = isHospitalType(facilityType);

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [loading, setLoading] = useState(false);

  const [facilityName, setFacilityName] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [gstin, setGstin] = useState('');
  const [facilityMsg, setFacilityMsg] = useState('');
  const [facilityErr, setFacilityErr] = useState('');

  const [facilities, setFacilities] = useState([]);
  const [staffForm, setStaffForm] = useState({
    ...emptyStaff(hospitalType),
  });
  const [consent, setConsent] = useState(false);
  const [staffMsg, setStaffMsg] = useState('');
  const [staffErr, setStaffErr] = useState('');

  const loadFacilities = async () => {
    if (hospitalType) {
      const res = await axios.get(`${API}/hospital/list`);
      setFacilities(res.data.filter(h => h.type === facilityType));
    } else {
      const res = await axios.get(`${API}/pharmacy-facility/list`);
      setFacilities(res.data.filter(f => f.type === facilityType));
    }
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginError('');
    setLoading(true);
    try {
      const { role } = await login(identifier, password);

      if (role === 'DOCTOR') navigate('/doctor');
      else if (role === 'ADMIN') navigate('/reception');
      else if (role === 'SUPER_ADMIN') navigate('/config');
      else if (role === 'PHARMACY') navigate('/pharmacy');
      else navigate('/');
    } catch (err) {
      setLoginError(errorMessage(err, 'Sign in failed.'));
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterFacility = async (e) => {
    e.preventDefault();
    setFacilityErr('');
    setFacilityMsg('');
    try {
      const url = hospitalType
        ? `${API}/hospital/register`
        : `${API}/pharmacy-facility/register`;

      const payload = hospitalType
        ? { name: facilityName, type: facilityType, registrationNumber, gstin: gstin || null }
        : { name: facilityName, type: facilityType, drugLicenceNumber: registrationNumber, gstin: gstin || null };

      await axios.post(url, payload);
      setFacilityMsg('Facility registered! It is pending admin verification before staff can be added.');
      setFacilityName('');
      setRegistrationNumber('');
      setGstin('');
    } catch (err) {
      setFacilityErr(errorMessage(err, 'Registration failed.'));
    }
  };

  const setStaff = (patch) => setStaffForm({ ...staffForm, ...patch });

  const handleRegisterStaff = async (e) => {
    e.preventDefault();
    setStaffErr('');
    setStaffMsg('');

    if (!consent) {
      setStaffErr('Please accept the privacy policy to continue.');
      return;
    }

    const f = staffForm;
    const fields = { role: f.role, name: f.name.trim(), phone: f.phone.trim(), password: f.password };
    if (f.facilityId) fields.facilityId = f.facilityId;
    if (f.role === 'DOCTOR') {
      fields.licenceNumber = f.licenceNumber.trim();
      fields.stateCouncil = f.stateCouncil.trim();
    } else if (f.role === 'PHARMACY') {
      fields.drugLicenceNumber = f.drugLicenceNumber.trim();
      fields.gstin = f.gstin.trim().toUpperCase();
      fields.state = f.state.trim().toUpperCase();
      if (!f.facilityId && f.address.trim()) fields.address = f.address.trim();
    }

    try {
      const res = await signup(fields, consent);
      const pending = res.verificationStatus === 'PENDING';
      setStaffMsg(
        pending
          ? `Account created (CareID ${res.careId}). ${res.verificationReason} You can sign in once it is approved.`
          : `Staff account created (CareID ${res.careId}). Sign in with your CareID or phone.`
      );
      setStaffForm(emptyStaff(hospitalType));
      setConsent(false);
    } catch (err) {
      setStaffErr(errorMessage(err, 'Registration failed.'));
    }
  };

  return (
    <div className="login-container">
      <div className={`login-shell entry-shell theme-${facilityType}`}>
        <div className="welcome-panel">
          <div className="page-brand" style={{ opacity: 0.85 }}>MedTrace</div>
          <div className="welcome-badge"><i className={typeIcons[facilityType] || 'ti ti-building-hospital'}></i></div>
          <h2>{typeLabels[facilityType] || 'Facility'}</h2>
          <p>{typeDescriptions[facilityType] || 'Sign in to your account, register a new facility, or add staff to an existing one.'}</p>

          <div className="welcome-pathway">
            {pathwaySteps.map((step, i, arr) => (
              <div className="welcome-pathway-step" key={step}>
                <div className="welcome-pathway-dot">{i + 1}</div>
                <span>{step}</span>
                {i < arr.length - 1 && <div className="welcome-pathway-line" />}
              </div>
            ))}
          </div>

          <button
            className="back-btn"
            style={{ marginTop: '1.5rem' }}
            onClick={() => navigate(hospitalType ? '/category/hospital' : '/')}
          >
            ← Back
          </button>
        </div>

        <div className="form-panel">
          <div className="entry-tabs">
            <button
              className={`entry-tab ${mode === 'login' ? 'active' : ''}`}
              onClick={() => setMode('login')}
            >Sign In</button>
            <button
              className={`entry-tab ${mode === 'registerFacility' ? 'active' : ''}`}
              onClick={() => setMode('registerFacility')}
            >New Facility</button>
            <button
              className={`entry-tab ${mode === 'registerStaff' ? 'active' : ''}`}
              onClick={() => { setMode('registerStaff'); loadFacilities(); }}
            >Add Staff</button>
          </div>

          {mode === 'login' && (
            <form onSubmit={handleLogin} className="entry-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input className="plain" placeholder="CareID or phone" value={identifier}
                onChange={(e) => setIdentifier(e.target.value)} required />
              <input className="plain" type="password" placeholder="Password" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
              {loginError && <p className="error">{loginError}</p>}
              <button className="btn" type="submit" disabled={loading}>
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>
          )}

          {mode === 'registerFacility' && (
            <form onSubmit={handleRegisterFacility} className="entry-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input className="plain" placeholder={`Facility name, e.g. ${hospitalType ? 'Kaveri' : 'Thulasi'}`} value={facilityName}
                onChange={(e) => setFacilityName(e.target.value)} required />

              <div>
                <label style={{ fontSize: '0.72rem', fontWeight: 600, color: '#5B6B79', display: 'block', marginBottom: '0.3rem' }}>
                  {hospitalType ? 'Registration Number' : 'Drug License Number'} (required)
                </label>
                <input
                  className="plain"
                  placeholder={hospitalType ? 'e.g. TN/CE/2024/00123' : 'e.g. DL-TN-20B-4567'}
                  value={registrationNumber}
                  onChange={(e) => setRegistrationNumber(e.target.value)}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: '0.72rem', fontWeight: 600, color: '#5B6B79', display: 'block', marginBottom: '0.3rem' }}>
                  GSTIN (optional)
                </label>
                <input
                  className="plain"
                  placeholder="e.g. 33AAAAA0000A1Z5"
                  value={gstin}
                  onChange={(e) => setGstin(e.target.value)}
                />
              </div>

              {facilityErr && <p className="error">{String(facilityErr)}</p>}
              {facilityMsg && <p style={{ color: '#0e7c86', fontSize: '0.85rem' }}>{facilityMsg}</p>}
              <button className="btn" type="submit">Register Facility</button>
            </form>
          )}

          {mode === 'registerStaff' && (
            <form onSubmit={handleRegisterStaff} className="entry-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <select className="plain" value={staffForm.facilityId}
                onChange={(e) => setStaff({ facilityId: e.target.value })}
                required={staffForm.role !== 'PHARMACY'}>
                <option value="">{staffForm.role === 'PHARMACY' ? 'Select facility (or leave blank to create one)' : 'Select facility'}</option>
                {facilities.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
              </select>
              <select className="plain" value={staffForm.role}
                onChange={(e) => setStaff({ role: e.target.value })}>
                {hospitalType ? (
                  <option value="DOCTOR">Doctor</option>
                ) : (
                  <option value="PHARMACY">Pharmacy</option>
                )}
              </select>
              <input className="plain"
                placeholder={staffForm.role === 'PHARMACY' ? 'Licensee name, as on the drug licence' : 'Full name'}
                value={staffForm.name} onChange={(e) => setStaff({ name: e.target.value })} required />
              <input className="plain" type="tel" placeholder="Phone" value={staffForm.phone}
                onChange={(e) => setStaff({ phone: e.target.value })} required />
              <input className="plain" type="password" placeholder="Password (min 8 characters)" minLength={8}
                value={staffForm.password} onChange={(e) => setStaff({ password: e.target.value })} required />

              {staffForm.role === 'DOCTOR' && (
                <>
                  <input className="plain" placeholder="Licence number, e.g. TN-48213" value={staffForm.licenceNumber}
                    onChange={(e) => setStaff({ licenceNumber: e.target.value })} required />
                  <input className="plain" placeholder="State medical council, e.g. Tamil Nadu Medical Council" value={staffForm.stateCouncil}
                    onChange={(e) => setStaff({ stateCouncil: e.target.value })} required />
                </>
              )}

              {staffForm.role === 'PHARMACY' && (
                <>
                  <input className="plain" placeholder="Drug licence number" value={staffForm.drugLicenceNumber}
                    onChange={(e) => setStaff({ drugLicenceNumber: e.target.value })} required />
                  <input className="plain" placeholder="GSTIN, e.g. 33AAAAA0000A1Z5" value={staffForm.gstin}
                    onChange={(e) => setStaff({ gstin: e.target.value })} required />
                  <input className="plain" placeholder="State code, e.g. TN" maxLength={2} value={staffForm.state}
                    onChange={(e) => setStaff({ state: e.target.value })} required />
                  {!staffForm.facilityId && (
                    <input className="plain" placeholder="Pharmacy address" value={staffForm.address}
                      onChange={(e) => setStaff({ address: e.target.value })} required />
                  )}
                </>
              )}

              <label style={{ display: 'flex', gap: '0.5rem', fontSize: '0.78rem', alignItems: 'flex-start' }}>
                <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                <span>I accept the Curo privacy policy.</span>
              </label>

              {staffErr && <p className="error">{String(staffErr)}</p>}
              {staffMsg && <p style={{ color: '#0e7c86', fontSize: '0.85rem' }}>{staffMsg}</p>}
              <button className="btn" type="submit" disabled={!consent}>
                Create Staff Account
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

export default Entry;