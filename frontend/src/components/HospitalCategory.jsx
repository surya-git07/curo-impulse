import { useNavigate } from 'react-router-dom';
import './Shared.css';

function HospitalCategory() {
  const navigate = useNavigate();

  const options = [
    { label: 'Private Hospital', type: 'private', icon: '🏥' },
    { label: 'Government Hospital', type: 'government', icon: '🏛️' },
  ];

  return (
    <div className="page" style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      padding: '1.5rem',
    }}>
      <div className="page-brand">MedTrace</div>

      <div style={{ textAlign: 'center', marginBottom: '2.5rem' }}>
        <h1 style={{ fontSize: '1.7rem', marginBottom: '0.5rem' }}>Hospital</h1>
        <p>Is this a private or government hospital?</p>
      </div>

      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(2, 1fr)',
        gap: '1.25rem',
        maxWidth: 480,
        width: '100%',
      }}>
        {options.map((opt) => (
          <div
            key={opt.type}
            className="card"
            style={{ cursor: 'pointer', textAlign: 'center', padding: '2rem 1rem' }}
            onClick={() => navigate(`/entry/${opt.type}`)}
          >
            <div style={{ fontSize: '2.2rem', marginBottom: '0.75rem' }}>{opt.icon}</div>
            <strong style={{ fontSize: '1rem' }}>{opt.label}</strong>
          </div>
        ))}
      </div>

      <button
        className="btn btn-secondary"
        style={{ marginTop: '2rem' }}
        onClick={() => navigate('/')}
      >
        ← Back
      </button>
    </div>
  );
}

export default HospitalCategory;