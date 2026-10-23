import { useState, useEffect } from 'react';
import { decodeToken } from '../utils/jwt';

function TopBar() {
  const user = decodeToken();
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(clock);
  }, []);

  const roleLabel = {
    DOCTOR: 'Doctor',
    PHARMACY: 'Pharmacy',
    ADMIN: 'Front desk',
    SUPER_ADMIN: 'Super admin',
  }[user?.role] || '';

  const facilityName = user?.facilityName || 'Unassigned';
  const alreadyMentionsRole = facilityName.toLowerCase().includes(roleLabel.toLowerCase());
  const initials = (user?.username || '?').slice(0, 2).toUpperCase();

  const dateStr = now.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
  const timeStr = now.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

  return (
    <header className="topbar">
      <div>
        <div className="topbar-title">
          {facilityName}{!alreadyMentionsRole ? ` ${roleLabel}` : ''}
        </div>
        <div className="topbar-sub">
          {user?.username}{user?.specialization ? ` · ${user.specialization}` : ''}
        </div>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <div style={{ textAlign: 'right' }}>
          <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.85)', fontWeight: 600 }}>{timeStr}</div>
          <div style={{ fontSize: 11, color: 'rgba(255,255,255,0.75)' }}>{dateStr}</div>
        </div>
        <div
          className="avatar"
          style={{ background: 'rgba(201, 123, 46, 0.25)', color: '#F5D9B8' }}
        >
          {initials}
        </div>
      </div>
    </header>
  );
}

export default TopBar;