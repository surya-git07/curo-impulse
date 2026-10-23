// mocks/data.js
// Fake data for the Curo patient app — no backend.
// CareID replaces ABHA everywhere. When docs/API_CONTRACT.md lands, match the
// field names here to it and the screens keep working.

// -------- Patient ----------------------------------------------------------
export const MOCK_PATIENT = {
  careId: 'CID-K4R9X2',
  name: 'Anjali Rao',
  phone: '9876543210',
  allergies: ['Penicillin'],
  profileCompleted: true,
};

// -------- Prescriptions ----------------------------------------------------
// One row per prescribed drug. Grouped by visitId on the Home screen.
// Timestamps are naive (no timezone); the app appends 'Z' before parsing.
// status: 'issued' (not yet dispensed) | 'dispensed'
export const MOCK_PRESCRIPTIONS = [
  // Today's visit — shows the allergy flag and the Warfarin+Aspirin interaction
  {
    id: 'p1', visitId: 'v1',
    drugName: 'Amoxicillin', dosage: '500 mg', durationDays: 5,
    status: 'issued', doctorName: 'Dr. Mehta', hospitalName: 'Kaveri Hospital',
    createdAt: '2026-10-23T08:30:00', dispensedAt: null,
    awareCategory: 'Access', isAntibiotic: true, allergyConflict: true,
  },
  {
    id: 'p2', visitId: 'v1',
    drugName: 'Warfarin', dosage: '5 mg', durationDays: 30,
    status: 'issued', doctorName: 'Dr. Mehta', hospitalName: 'Kaveri Hospital',
    createdAt: '2026-10-23T08:30:00', dispensedAt: null,
    awareCategory: null, isAntibiotic: false, allergyConflict: false,
  },
  {
    id: 'p3', visitId: 'v1',
    drugName: 'Aspirin', dosage: '75 mg', durationDays: 30,
    status: 'issued', doctorName: 'Dr. Mehta', hospitalName: 'Kaveri Hospital',
    createdAt: '2026-10-23T08:30:00', dispensedAt: null,
    awareCategory: null, isAntibiotic: false, allergyConflict: false,
  },
  // Last week — dispensed, still within course (Active)
  {
    id: 'p4', visitId: 'v2',
    drugName: 'Metformin', dosage: '850 mg', durationDays: 30,
    status: 'dispensed', doctorName: 'Dr. Rao', hospitalName: 'City Clinic',
    createdAt: '2026-10-16T10:00:00', dispensedAt: '2026-10-16T12:00:00',
    awareCategory: null, isAntibiotic: false, allergyConflict: false,
  },
  {
    id: 'p5', visitId: 'v2',
    drugName: 'Vitamin D3', dosage: '1000 IU', durationDays: 60,
    status: 'dispensed', doctorName: 'Dr. Rao', hospitalName: 'City Clinic',
    createdAt: '2026-10-16T10:00:00', dispensedAt: '2026-10-16T12:00:00',
    awareCategory: null, isAntibiotic: false, allergyConflict: false,
  },
  // Older — dispensed, course finished
  {
    id: 'p6', visitId: 'v3',
    drugName: 'Azithromycin', dosage: '500 mg', durationDays: 3,
    status: 'dispensed', doctorName: 'Dr. Mehta', hospitalName: 'Kaveri Hospital',
    createdAt: '2026-09-20T09:00:00', dispensedAt: '2026-09-20T11:00:00',
    awareCategory: 'Watch', isAntibiotic: true, allergyConflict: false,
  },
];

// -------- Drug-drug interactions (active for this patient) ------------------
// Backend returns these from drug-rules.json; hard-coded here for the demo.
export const MOCK_INTERACTIONS = [
  {
    id: 'i1', drugA: 'Warfarin', drugB: 'Aspirin', severity: 'major',
    warning: 'Increased bleeding risk',
    explanation:
      'Warfarin and aspirin both reduce clotting. Taken together they sharply raise the risk of serious bleeding.',
  },
];

export const SEVERITY_COLORS = { major: '#c0392b', moderate: '#e08e0b', minor: '#6b7a8a' };

// -------- Today's dose schedule --------------------------------------------
// taken starts false; the "Mark taken" button flips it locally.
export const MOCK_TODAY_SCHEDULE = [
  { id: 'd1', drugName: 'Warfarin', dosage: '5 mg', time: '08:00', taken: false },
  { id: 'd2', drugName: 'Aspirin', dosage: '75 mg', time: '08:00', taken: false },
  { id: 'd3', drugName: 'Metformin', dosage: '850 mg', time: '08:00', taken: false },
  { id: 'd4', drugName: 'Amoxicillin', dosage: '500 mg', time: '14:00', taken: false },
  { id: 'd5', drugName: 'Amoxicillin', dosage: '500 mg', time: '21:00', taken: false },
];

// -------- Family members ---------------------------------------------------
// Each member has their own identifier (CareID or phone) and prescription list.
export const MOCK_FAMILY = [
  {
    id: 'f1', relation: 'Mother', name: 'Lakshmi Rao',
    idType: 'CareID', idValue: 'CID-M7T3Q8',
    age: '58', gender: 'Female', bloodGroup: 'B+',
    allergies: [], conditions: 'Hypertension',
    prescriptions: [
      { id: 'fp1', drugName: 'Amlodipine', dosage: '5 mg', durationDays: 30, doctorName: 'Dr. Rao', createdAt: '2026-10-10T09:00:00' },
    ],
  },
  {
    id: 'f2', relation: 'Son', name: 'Arjun Rao',
    idType: 'Phone', idValue: '9876500011',
    age: '9', gender: 'Male', bloodGroup: 'O+',
    allergies: [], conditions: 'None',
    prescriptions: [],
  },
];

// -------- Access log (who viewed the record) -------------------------------
// One of the brief's must-haves. Shown on a simple access-log screen.
export const MOCK_ACCESS_LOG = [
  { id: 'a1', who: 'Dr. Mehta', role: 'Doctor', org: 'Kaveri Hospital', action: 'Viewed record', at: '2026-10-23T08:29:00' },
  { id: 'a4', who: 'Guest (shared link)', role: 'Guest', org: 'Shared link', action: 'Viewed medicines & allergies (code K7Q2MX)', at: '2026-10-22T18:15:00' },
  { id: 'a2', who: 'Kaveri Pharmacy', role: 'Pharmacy', org: 'Kaveri Hospital', action: 'Dispensed Metformin', at: '2026-10-16T12:00:00' },
  { id: 'a3', who: 'Dr. Rao', role: 'Doctor', org: 'City Clinic', action: 'Viewed record', at: '2026-10-16T10:00:00' },
];

// Blank profile shape for the Complete-profile form.
export const BLANK_PROFILE = {
  age: '', gender: '', bloodGroup: '', heightCm: '', weightKg: '',
  allergies: [], conditions: '', pregnancy: 'Not applicable',
  surgeries: '', currentMedications: '', traditionalMedicine: '',
};