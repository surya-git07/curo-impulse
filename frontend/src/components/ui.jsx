// Small shared pieces for the demo screens. Colours and sizes come from theme.css variables.

export function Spinner({ large = false }) {
  return <span className={`spinner${large ? ' spinner-lg' : ''}`} role="status" aria-label="Loading" />;
}

export function Loading({ text = 'Loading…' }) {
  return <div className="loading-row"><Spinner large /> <span>{text}</span></div>;
}

export function EmptyState({ icon = '○', title, text }) {
  return (
    <div className="empty-state">
      <div className="empty-icon" aria-hidden="true">{icon}</div>
      <div className="empty-title">{title}</div>
      {text && <div className="empty-text">{text}</div>}
    </div>
  );
}

// One colour per status, defined in theme.css (.badge-status-*).
export function StatusBadge({ status, children }) {
  if (!status) return null;
  const key = String(status).toLowerCase();
  const label = children || String(status).charAt(0) + String(status).slice(1).toLowerCase();
  return <span className={`badge badge-status-${key}`}>{label}</span>;
}

const SEVERITY = {
  HIGH: { cls: 'sev-high', icon: '!', label: 'High risk' },
  MODERATE: { cls: 'sev-moderate', icon: '!', label: 'Moderate risk' },
  LOW: { cls: 'sev-low', icon: 'i', label: 'Low risk' },
  NONE: { cls: 'sev-none', icon: '✓', label: 'No conflict' },
  UNKNOWN: { cls: 'sev-unknown', icon: '?', label: 'Not checked' },
};

// Interaction check: advice only, never blocks issuing.
export function SeverityCard({ severity, warning, explanation, drug }) {
  const st = SEVERITY[severity] || SEVERITY.UNKNOWN;
  return (
    <div className={`sev-card ${st.cls}`} role="alert">
      <div className="sev-icon" aria-hidden="true">{st.icon}</div>
      <div className="sev-body">
        <div className="sev-kicker">
          <span>{drug ? `${drug} · ` : ''}Interaction check</span>
          <span>{st.label}</span>
        </div>
        <div className="sev-headline">{warning || (severity === 'NONE' ? 'No conflict found' : 'Could not be checked')}</div>
        {explanation && <div className="sev-text">{explanation}</div>}
        <div className="sev-foot">Advice only. You can still issue this prescription.</div>
      </div>
    </div>
  );
}

export function AllergyCard({ allergies }) {
  if (!allergies || allergies.length === 0) return null;
  return (
    <div className="sev-card sev-allergy" role="alert">
      <div className="sev-icon" aria-hidden="true">!</div>
      <div className="sev-body">
        <div className="sev-kicker"><span>Allergy conflict</span></div>
        <div className="sev-headline">Allergy on file: {allergies.join(', ')}</div>
        <div className="sev-text">Check every drug below against these allergies before you issue.</div>
      </div>
    </div>
  );
}
