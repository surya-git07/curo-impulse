// groupMeds.js
// Display helpers that put live medicines into the same cards as Home's Active Prescriptions.
// Pure functions; no data fetching and no state.

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

// 'YYYY-MM-DD' -> '24 October 2026' ('' if it is not a plain date).
export function formatDay(day) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day || '');
  if (!m || Number(m[2]) < 1 || Number(m[2]) > 12) return '';
  return `${Number(m[3])} ${MONTHS[Number(m[2]) - 1]} ${m[1]}`;
}

// Live medicines (from /patient/me/medications) -> the prescription shape Home's Active
// Prescriptions block renders, so live and mock rows go through the same cards.
//   - status 'issued'  -> the "Issued" tag (dispensing is not tracked by the backend yet)
//   - visitId          -> doctor + hospital + issue date, so Home groups them the same way
//   - allergyConflict  -> set from the live allergy check (conflictDrugs), same source as the warning card
//   - no awareCategory / isAntibiotic: the backend does not send them (prescriptions/detailed is not built)
export function toActivePrescriptions(meds, conflictDrugs = []) {
  return (meds || []).map((m) => {
    const day = m.startDate || null;
    const times = Array.isArray(m.times) && m.times.length > 0 ? ` · ${m.times.join(', ')}` : '';
    return {
      id: m.id,
      drugName: m.drug,
      dosage: m.dose,
      detail: `${m.dose}${times}${m.endDate ? ` · until ${m.endDate}` : ''}`,
      status: 'issued',
      doctorName: m.prescriberName || null,
      hospitalName: m.hospitalName || null,
      createdAt: day ? `${day}T00:00:00` : null, // sort key only; the card shows issueDay
      issueDay: day,
      visitId: [m.prescriberName || '', m.hospitalName || '', day || ''].join('|'),
      awareCategory: null,
      isAntibiotic: false,
      allergyConflict: conflictDrugs.includes(m.drug),
    };
  });
}
