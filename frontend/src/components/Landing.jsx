import { useNavigate } from 'react-router-dom';
import './Landing.css';

function Landing() {
  const navigate = useNavigate();

  const options = [
    {
      label: 'Hospital',
      sub: 'Reception · Doctor · Pharmacy',
      path: '/category/hospital',
      color: '#185FA5',
      bg: '#E6F1FB',
      svg: (
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M19 21V5a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v16" />
          <path d="M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
          <path d="M12 7v4M10 9h4" />
        </svg>
      ),
    },
    {
      label: 'Retail Pharmacy',
      sub: 'Standalone dispensing',
      path: '/entry/pharmacy',
      color: '#854F0B',
      bg: '#FAEEDA',
      svg: (
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M10.5 20.5 3.5 13.5a3 3 0 1 1 4.24-4.24l7 7a3 3 0 1 1-4.24 4.24Z" />
          <path d="m8.5 8.5 7 7" />
        </svg>
      ),
    },
    {
      label: 'Retail Clinic',
      sub: 'Independent practice',
      path: '/entry/clinic',
      color: '#085041',
      bg: '#E1F5EE',
      svg: (
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M8 15V9a4 4 0 0 1 8 0v5" />
          <path d="M8 15a4 4 0 0 0 8 0" />
          <circle cx="18" cy="15" r="2" />
        </svg>
      ),
    },
  ];

  const pathway = ['Reception', 'Doctor', 'Pharmacy', 'Patient'];

  return (
    <div className="mt-landing">
      <div className="mt-landing-hero">
        <span className="mt-landing-eyebrow">Antimicrobial Resistance · Prescription Traceability</span>
        <h1 className="mt-landing-title">
          Every prescription,<br />traced end to end.
        </h1>
        <p className="mt-landing-subtitle">
          MedTrace connects reception, doctors, and pharmacies on one verified record —
          so no antibiotic gets prescribed twice without anyone knowing.
        </p>

        <div className="mt-landing-pathway" aria-hidden="true">
          {pathway.map((step, i) => (
            <div className="mt-landing-pathway-step" key={step}>
              <div className="mt-landing-pathway-dot">{i + 1}</div>
              <span>{step}</span>
              {i < pathway.length - 1 && <div className="mt-landing-pathway-line" />}
            </div>
          ))}
        </div>
      </div>

      <div className="mt-landing-section-label">Sign in as</div>

      <div className="mt-landing-grid">
        {options.map((opt) => (
          <div
            key={opt.label}
            className="mt-landing-card"
            onClick={() => navigate(opt.path)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter') navigate(opt.path); }}
          >
            <div
              className="mt-landing-icon"
              style={{ background: opt.bg, color: opt.color }}
            >
              {opt.svg}
            </div>
            <strong className="mt-landing-card-title">{opt.label}</strong>
            <span className="mt-landing-card-sub">{opt.sub}</span>
          </div>
        ))}
      </div>

      <p className="mt-landing-footer">MedTrace · Built for AMR-PVMS</p>
    </div>
  );
}

export default Landing;