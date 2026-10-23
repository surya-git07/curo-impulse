// mocks/data.js
// Fake patient data for building the screens without a backend.
// Shapes follow the Curo plan. When docs/API_CONTRACT.md lands, adjust the
// field names here to match it, and the screens keep working.

// The signed-in patient. CareID is CID- plus 6 characters (per the plan).
export const MOCK_PATIENT = {
  care_id: 'CID-7F3K9A',
  name: 'Anjali Rao',
  phone: '9876543210',
  allergies: 'Penicillin',
};

// "My Medicines" — one row per active medicine.
export const MOCK_MEDICINES = [
  {
    id: 'm1',
    drug_name: 'Amoxicillin',
    dosage: '500 mg',
    frequency: 'Twice a day',
    duration_days: 5,
    doctor_name: 'Dr. Mehta',
    status: 'active',
    allergy_conflict: true, // patient is allergic to penicillin
  },
  {
    id: 'm2',
    drug_name: 'Metformin',
    dosage: '850 mg',
    frequency: 'Once a day',
    duration_days: 30,
    doctor_name: 'Dr. Mehta',
    status: 'active',
    allergy_conflict: false,
  },
  {
    id: 'm3',
    drug_name: 'Vitamin D3',
    dosage: '1000 IU',
    frequency: 'Once a day',
    duration_days: 60,
    doctor_name: 'Dr. Mehta',
    status: 'active',
    allergy_conflict: false,
  },
];

// "Today's schedule" — one row per dose due today.
// taken starts false; the "Mark taken" button flips it locally.
export const MOCK_TODAY_SCHEDULE = [
  { id: 'd1', medicine_id: 'm2', drug_name: 'Metformin', dosage: '850 mg', time: '08:00', taken: false },
  { id: 'd2', medicine_id: 'm3', drug_name: 'Vitamin D3', dosage: '1000 IU', time: '08:00', taken: false },
  { id: 'd3', medicine_id: 'm1', drug_name: 'Amoxicillin', dosage: '500 mg', time: '14:00', taken: false },
  { id: 'd4', medicine_id: 'm1', drug_name: 'Amoxicillin', dosage: '500 mg', time: '21:00', taken: false },
];