// mocks/consent.js
// Pending access requests from doctors/caregivers, for patient approval.
// Mock only. camelCase per contract v0.3+.

export const MOCK_CONSENT_REQUESTS = [
  {
    id: 'cr1',
    requesterName: 'Dr. Mehta',
    requesterRole: 'Doctor',
    org: 'Kaveri Hospital',
    reason: 'Reviewing current medications before today\'s visit',
    requestedAt: '2026-10-23T15:00:00',
    status: 'pending', // pending | approved | denied
  },
];