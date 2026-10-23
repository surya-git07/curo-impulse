import { useState } from 'react';
import { login, errorMessage, LOGIN_ID_PATTERN, loginIdentifier, loginIdentifierError } from '../api';
import './Login.css';
import { useNavigate } from 'react-router-dom';
function Login() {
    const navigate = useNavigate();
  const [identifier, setIdentifier] = useState('');
  const [idTouched, setIdTouched] = useState(false);
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [loggedInUser, setLoggedInUser] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (loginIdentifierError(identifier)) {
      setIdTouched(true);
      return;
    }
    setLoading(true);

    try {
      const user = await login(identifier, password);
      const role = user.role;
      setLoggedInUser(user);

      // Redirect based on role after a short pause
setTimeout(() => {
  if (role === 'DOCTOR') navigate('/doctor');
  else if (role === 'ADMIN') navigate('/reception');
  else if (role === 'SUPER_ADMIN') navigate('/config');
  else if (role === 'PHARMACY') navigate('/pharmacy');
}, 1200);
    } catch (err) {
      setError(errorMessage(err, 'Sign in failed.'));
    } finally {
      setLoading(false);
    }
  };

  if (loggedInUser) {
    return (
      <div className="login-container">
        <div className="login-shell">
          <div className="welcome-panel">
            <div className="welcome-badge">✓</div>
<h2>You're signed in,<br />{loggedInUser.name}</h2>
<p>{loggedInUser.facilityName} · Your Curo session is active.</p>
<span className="role-badge">{loggedInUser.role}</span>
          </div>
          <div className="form-panel">
            <h1>Session active</h1>
            <p className="subtitle">Welcome to Curo.</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="login-container">
      <div className="login-shell">
        <div className="welcome-panel">
          <div className="welcome-badge">+</div>
          <h2>Welcome to<br />Curo</h2>
          <p>
            A shared platform for medication coordination,
            secure prescriptions, and hospital-pharmacy handover.
          </p>
          <div className="welcome-stats">
            <div>
              <strong>3</strong>
              <span>Facilities</span>
            </div>
            <div>
              <strong>24/7</strong>
              <span>Monitoring</span>
            </div>
            <div>
              <strong>100%</strong>
              <span>Traceable</span>
            </div>
          </div>
        </div>

        <div className="form-panel">
          <h1>Sign in</h1>
          <p className="subtitle">Enter your credentials to access your dashboard</p>

          <form onSubmit={handleSubmit}>
            <div className="field-group">
              <label htmlFor="identifier">CareID or phone</label>
              <input
                id="identifier"
                type="text"
                placeholder="CID-XXXXXX or 10-digit mobile number"
                value={identifier}
                maxLength={10}
                pattern={LOGIN_ID_PATTERN}
                onChange={(e) => setIdentifier(loginIdentifier(e.target.value))}
                onBlur={() => setIdTouched(true)}
                required
              />
              {idTouched && loginIdentifierError(identifier) && (
                <p className="error" role="alert" style={{ marginTop: '0.4rem' }}>{loginIdentifierError(identifier)}</p>
              )}
            </div>
            <div className="field-group">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && <p className="error">{error}</p>}
            <button type="submit" disabled={loading}>
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

export default Login;