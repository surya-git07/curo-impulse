import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

// The prescription QR as an image, with the same code typed out underneath for when a camera is not available.
function RxQrCode({ code, size = 160 }) {
  const [src, setSrc] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (!code) { setSrc(''); return undefined; }
    QRCode.toDataURL(code, { width: size, margin: 1 })
      .then((url) => { if (!cancelled) setSrc(url); })
      .catch(() => { if (!cancelled) setSrc(''); });
    return () => { cancelled = true; };
  }, [code, size]);

  if (!code) {
    return <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>No QR code for this prescription yet.</div>;
  }

  return (
    <div style={{ textAlign: 'center' }}>
      {src && <img src={src} alt={`Prescription QR code ${code}`} width={size} height={size} />}
      <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 4 }}>Code to type at the pharmacy</div>
      <div style={{ fontSize: 15, fontWeight: 700, letterSpacing: 1, fontFamily: 'monospace', wordBreak: 'break-all' }}>{code}</div>
    </div>
  );
}

export default RxQrCode;
