// mocks/data.js
// Fake data for the Curo patient app — no backend.
// CareID replaces ABHA everywhere. When docs/API_CONTRACT.md lands, match the
// field names here to it and the screens keep working.

// -------- Patient ----------------------------------------------------------
export const MOCK_PATIENT = {
  care_id: 'CARE-4821-9937',
  name: 'Anjali Rao',
  phone: '9876543210',
  allergies: 'Penicillin',
  profile_completed: true,
};

// -------- Prescriptions ----------------------------------------------------
// One row per prescribed drug. Grouped by visit_id on the Home screen.
// Timestamps are naive (no timezone); the app appends 'Z' before parsing.
// status: 'issued' (not yet dispensed) | 'dispensed'
export const MOCK_PRESCRIPTIONS = [
  // Today's visit — shows the allergy flag and the Warfarin+Aspirin interaction
  {
    id: 'p1', visit_id: 'v1',
    drug_name: 'Amoxicillin', dosage: '500 mg', duration_days: 5,
    status: 'issued', doctor_name: 'Dr. Mehta', hospital_name: 'Kaveri Hospital',
    created_at: '2026-10-23T08:30:00', dispensed_at: null,
    aware_category: 'Access', is_antibiotic: true, allergy_conflict: true,
  },
  {
    id: 'p2', visit_id: 'v1',
    drug_name: 'Warfarin', dosage: '5 mg', duration_days: 30,
    status: 'issued', doctor_name: 'Dr. Mehta', hospital_name: 'Kaveri Hospital',
    created_at: '2026-10-23T08:30:00', dispensed_at: null,
    aware_category: null, is_antibiotic: false, allergy_conflict: false,
  },
  {
    id: 'p3', visit_id: 'v1',
    drug_name: 'Aspirin', dosage: '75 mg', duration_days: 30,
    status: 'issued', doctor_name: 'Dr. Mehta', hospital_name: 'Kaveri Hospital',
    created_at: '2026-10-23T08:30:00', dispensed_at: null,
    aware_category: null, is_antibiotic: false, allergy_conflict: false,
  },
  // Last week — dispensed, still within course (Active)
  {
    id: 'p4', visit_id: 'v2',
    drug_name: 'Metformin', dosage: '850 mg', duration_days: 30,
    status: 'dispensed', doctor_name: 'Dr. Rao', hospital_name: 'City Clinic',
    created_at: '2026-10-16T10:00:00', dispensed_at: '2026-10-16T12:00:00',
    aware_category: null, is_antibiotic: false, allergy_conflict: false,
  },
  {
    id: 'p5', visit_id: 'v2',
    drug_name: 'Vitamin D3', dosage: '1000 IU', duration_days: 60,
    status: 'dispensed', doctor_name: 'Dr. Rao', hospital_name: 'City Clinic',
    created_at: '2026-10-16T10:00:00', dispensed_at: '2026-10-16T12:00:00',
    aware_category: null, is_antibiotic: false, allergy_conflict: false,
  },
  // Older — dispensed, course finished
  {
    id: 'p6', visit_id: 'v3',
    drug_name: 'Azithromycin', dosage: '500 mg', duration_days: 3,
    status: 'dispensed', doctor_name: 'Dr. Mehta', hospital_name: 'Kaveri Hospital',
    created_at: '2026-09-20T09:00:00', dispensed_at: '2026-09-20T11:00:00',
    aware_category: 'Watch', is_antibiotic: true, allergy_conflict: false,
  },
];

// -------- Drug-drug interactions (active for this patient) ------------------
// Backend returns these from drug-rules.json; hard-coded here for the demo.
export const MOCK_INTERACTIONS = [
  {
    id: 'i1', drug_a: 'Warfarin', drug_b: 'Aspirin', severity: 'major',
    warning: 'Increased bleeding risk',
    explanation:
      'Warfarin and aspirin both reduce clotting. Taken together they sharply raise the risk of serious bleeding.',
  },
];

export const SEVERITY_COLORS = { major: '#c0392b', moderate: '#e08e0b', minor: '#6b7a8a' };

// -------- Today's dose schedule --------------------------------------------
// taken starts false; the "Mark taken" button flips it locally.
export const MOCK_TODAY_SCHEDULE = [
  { id: 'd1', drug_name: 'Warfarin', dosage: '5 mg', time: '08:00', taken: false },
  { id: 'd2', drug_name: 'Aspirin', dosage: '75 mg', time: '08:00', taken: false },
  { id: 'd3', drug_name: 'Metformin', dosage: '850 mg', time: '08:00', taken: false },
  { id: 'd4', drug_name: 'Amoxicillin', dosage: '500 mg', time: '14:00', taken: false },
  { id: 'd5', drug_name: 'Amoxicillin', dosage: '500 mg', time: '21:00', taken: false },
];

// -------- Family members ---------------------------------------------------
// Each member has their own identifier (CareID or phone) and prescription list.
export const MOCK_FAMILY = [
  {
    id: 'f1', relation: 'Mother', name: 'Lakshmi Rao',
    id_type: 'CareID', id_value: 'CARE-1130-5567',
    age: '58', gender: 'Female', blood_group: 'B+',
    allergies: 'None', conditions: 'Hypertension',
    prescriptions: [
      { id: 'fp1', drug_name: 'Amlodipine', dosage: '5 mg', duration_days: 30, doctor_name: 'Dr. Rao', created_at: '2026-10-10T09:00:00' },
    ],
  },
  {
    id: 'f2', relation: 'Son', name: 'Arjun Rao',
    id_type: 'Phone', id_value: '9876500011',
    age: '9', gender: 'Male', blood_group: 'O+',
    allergies: 'None', conditions: 'None',
    prescriptions: [],
  },
];

// -------- Access log (who viewed the record) -------------------------------
// One of the brief's must-haves. Shown on a simple access-log screen.
export const MOCK_ACCESS_LOG = [
  { id: 'a1', who: 'Dr. Mehta', role: 'Doctor', org: 'Kaveri Hospital', action: 'Viewed record', at: '2026-10-23T08:29:00' },
  { id: 'a2', who: 'Kaveri Pharmacy', role: 'Pharmacy', org: 'Kaveri Hospital', action: 'Dispensed Metformin', at: '2026-10-16T12:00:00' },
  { id: 'a3', who: 'Dr. Rao', role: 'Doctor', org: 'City Clinic', action: 'Viewed record', at: '2026-10-16T10:00:00' },
];

// Blank profile shape for the Complete-profile form.
export const BLANK_PROFILE = {
  age: '', gender: '', blood_group: '', height_cm: '', weight_kg: '',
  allergies: '', conditions: '', pregnancy: 'Not applicable',
  surgeries: '', current_medications: '', traditional_medicine: '',
};