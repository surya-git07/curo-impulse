import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { login, signup, errorMessage, PHONE_RE, PHONE_PATTERN, PHONE_MESSAGE, phoneDigits, LOGIN_ID_PATTERN, loginIdentifier, loginIdentifierError } from '../api';
import { Spinner, StatusBadge } from './ui';
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

// Pharmacy pages sign up pharmacies; hospital and clinic pages sign up doctors.
const staffRole = (type) => (type === 'pharmacy' ? 'PHARMACY' : 'DOCTOR');

const emptyStaff = () => ({
  name: '', phone: '', password: '',
  licenceNumber: '', stateCouncil: '',
  drugLicenceNumber: '', gstin: '', state: '',
});

function Entry() {
  const { facilityType } = useParams();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const role = staffRole(facilityType);
  const hospitalType = facilityType === 'private' || facilityType === 'government';

  const [identifier, setIdentifier] = useState('');
  const [idTouched, setIdTouched] = useState(false);
  const [phoneTouched, setPhoneTouched] = useState(false);
  const [password, setPassword] = useState('');
  const [loginError, setLoginError] = useState('');
  const [loading, setLoading] = useState(false);

  const [staffForm, setStaffForm] = useState(emptyStaff());
  const [consent, setConsent] = useState(false);
  const [staffErr, setStaffErr] = useState('');
  const [staffBusy, setStaffBusy] = useState(false);
  const [signupResult, setSignupResult] = useState(null);

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoginError('');
    if (loginIdentifierError(identifier)) {
      setIdTouched(true);
      return;
    }
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

  const setStaff = (patch) => setStaffForm({ ...staffForm, ...patch });

  const handleRegisterStaff = async (e) => {
    e.preventDefault();
    setStaffErr('');
    setSignupResult(null);

    if (!PHONE_RE.test(staffForm.phone)) {
      setPhoneTouched(true);
      return;
    }
    if (!consent) {
      setStaffErr('Please accept the privacy policy to continue.');
      return;
    }

    const f = staffForm;
    const fields = { role, name: f.name.trim(), phone: f.phone.trim(), password: f.password };
    if (role === 'DOCTOR') {
      fields.licenceNumber = f.licenceNumber.trim();
      fields.stateCouncil = f.stateCouncil.trim();
    } else {
      fields.drugLicenceNumber = f.drugLicenceNumber.trim();
      fields.gstin = f.gstin.trim().toUpperCase();
      fields.state = f.state.trim().toUpperCase();
    }

    setStaffBusy(true);
    try {
      const res = await signup(fields, consent);
      setSignupResult(res);
      setStaffForm(emptyStaff());
      setPhoneTouched(false);
      setConsent(false);
    } catch (err) {
      setStaffErr(errorMessage(err, 'Sign up failed. Check the details and try again.'));
    } finally {
      setStaffBusy(false);
    }
  };

  return (
    <div className="login-container">
      <div className={`login-shell entry-shell theme-${facilityType}`}>
        <div className="welcome-panel">
          <div className="page-brand" style={{ opacity: 0.85 }}>Curo</div>
          <div className="welcome-badge"><i className={typeIcons[facilityType] || 'ti ti-building-hospital'}></i></div>
          <h2>{typeLabels[facilityType] || 'Facility'}</h2>
          <p>{typeDescriptions[facilityType] || 'Sign in, or sign up with your licence.'}</p>

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
              className={`entry-tab ${mode === 'registerStaff' ? 'active' : ''}`}
              onClick={() => setMode('registerStaff')}
            >Staff Sign Up</button>
          </div>

          {mode === 'login' && (
            <form onSubmit={handleLogin} className="entry-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input className="plain" placeholder="CareID or 10-digit mobile number" value={identifier}
                maxLength={10} pattern={LOGIN_ID_PATTERN} title="A CareID like CID-7K3MQ9 or a 10-digit mobile number"
                aria-invalid={idTouched && !!loginIdentifierError(identifier)}
                onChange={(e) => setIdentifier(loginIdentifier(e.target.value))}
                onBlur={() => setIdTouched(true)} required />
              {idTouched && loginIdentifierError(identifier) && (
                <p className="error" role="alert" style={{ marginTop: '-0.5rem' }}>{loginIdentifierError(identifier)}</p>
              )}
              <input className="plain" type="password" placeholder="Password" value={password}
                onChange={(e) => setPassword(e.target.value)} required />
              {loginError && <p className="error">{loginError}</p>}
              <button className="btn" type="submit" disabled={loading}>
                {loading ? 'Signing in...' : 'Sign In'}
              </button>
            </form>
          )}

          {mode === 'registerStaff' && (
            <form onSubmit={handleRegisterStaff} className="entry-form" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input className="plain"
                placeholder={role === 'PHARMACY' ? 'Licensee name, as on the drug licence' : 'Full name'}
                value={staffForm.name} onChange={(e) => setStaff({ name: e.target.value })} required />
              <input className="plain" type="tel" inputMode="numeric" autoComplete="tel-national"
                placeholder="Mobile number (10 digits)" value={staffForm.phone}
                maxLength={10} pattern={PHONE_PATTERN} title={PHONE_MESSAGE}
                aria-invalid={phoneTouched && !PHONE_RE.test(staffForm.phone)}
                onChange={(e) => setStaff({ phone: phoneDigits(e.target.value) })}
                onBlur={() => setPhoneTouched(true)} required />
              {phoneTouched && !PHONE_RE.test(staffForm.phone) && (
                <p className="error" role="alert" style={{ marginTop: '-0.5rem' }}>{PHONE_MESSAGE}</p>
              )}
              <input className="plain" type="password" placeholder="Password (min 8 characters)" minLength={8}
                value={staffForm.password} onChange={(e) => setStaff({ password: e.target.value })} required />

              {role === 'DOCTOR' && (
                <>
                  <input className="plain" placeholder="Licence number, e.g. TN-48213" value={staffForm.licenceNumber}
                    onChange={(e) => setStaff({ licenceNumber: e.target.value })} required />
                  <input className="plain" placeholder="State medical council, e.g. Tamil Nadu Medical Council" value={staffForm.stateCouncil}
                    onChange={(e) => setStaff({ stateCouncil: e.target.value })} required />
                </>
              )}

              {role === 'PHARMACY' && (
                <>
                  <input className="plain" placeholder="Drug licence number" value={staffForm.drugLicenceNumber}
                    onChange={(e) => setStaff({ drugLicenceNumber: e.target.value })} required />
                  <input className="plain" placeholder="GSTIN, e.g. 33AAAAA0000A1Z5" value={staffForm.gstin}
                    onChange={(e) => setStaff({ gstin: e.target.value })} required />
                  <input className="plain" placeholder="State code, e.g. TN" maxLength={2} value={staffForm.state}
                    onChange={(e) => setStaff({ state: e.target.value })} required />
                </>
              )}

              <label style={{ display: 'flex', gap: '0.5rem', fontSize: '0.78rem', alignItems: 'flex-start' }}>
                <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                <span>I accept the Curo privacy policy.</span>
              </label>

              {staffErr && <p className="error" role="alert">{staffErr}</p>}
              {signupResult && (
                <div className={`alert-box ${signupResult.verificationStatus === 'VERIFIED' ? 'alert-green' : 'alert-orange'}`} role="status">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                    <strong>Licence status</strong>
                    <StatusBadge status={signupResult.verificationStatus || 'PENDING'} />
                  </div>
                  <div>{signupResult.verificationReason
                    || (signupResult.verificationStatus === 'VERIFIED' ? 'Your licence matched the registry.' : 'A reviewer will check your licence.')}</div>
                  <div style={{ marginTop: 6 }}>
                    {signupResult.verificationStatus === 'VERIFIED'
                      ? <>Your CareID is <strong>{signupResult.careId}</strong>. You can sign in now with it or your phone.</>
                      : <>Your CareID is <strong>{signupResult.careId}</strong>. You can sign in once a reviewer approves the account.</>}
                  </div>
                </div>
              )}
              <button className="btn" type="submit" disabled={!consent || staffBusy}>
                {staffBusy ? <><Spinner /> Creating account…</> : 'Create Account'}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

export default Entry;