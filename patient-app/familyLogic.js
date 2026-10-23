// familyLogic.js
// Pure helpers for the Family tab. Local state only: there is no backend family
// endpoint yet. Members keep the same shape as MOCK_FAMILY in mocks/data.js.

export const RELATIONS = ['Mother', 'Father', 'Spouse', 'Son', 'Daughter', 'Sibling', 'Other'];
export const GENDERS = ['Female', 'Male', 'Other'];
export const ID_TYPES = ['CareID', 'Phone'];

export const BLANK_MEMBER = {
  relation: '', name: '', idType: 'CareID', idValue: '',
  age: '', gender: '', bloodGroup: '', allergiesText: '', conditions: '',
};

// "Penicillin, Sulfa" -> ['Penicillin', 'Sulfa']; "None" is not an allergy.
export const parseAllergies = (text) =>
  String(text || '').split(',').map((a) => a.trim()).filter((a) => a && a.toLowerCase() !== 'none');

// Form values for an existing member (allergies array -> comma text).
export const toForm = (m) => ({
  relation: m.relation || '', name: m.name || '', idType: m.idType || 'CareID', idValue: m.idValue || '',
  age: m.age || '', gender: m.gender || '', bloodGroup: m.bloodGroup || '',
  allergiesText: (m.allergies || []).join(', '), conditions: m.conditions === 'None' ? '' : (m.conditions || ''),
});

// Returns an error message, or '' when the form is valid.
export function validateMember(f) {
  if (!f.relation) return 'Choose how this person is related to you.';
  if (!f.name.trim()) return 'Enter their name.';
  const id = f.idValue.trim();
  if (!id) return f.idType === 'CareID' ? 'Enter their CareID.' : 'Enter their phone number.';
  if (f.idType === 'CareID' && !/^CID-[A-Z0-9]{6}$/i.test(id)) return 'A CareID looks like CID-AB12CD.';
  if (f.idType === 'Phone' && !/^\d{10}$/.test(id)) return 'A phone number is 10 digits.';
  if (f.age.trim() && !(/^\d{1,3}$/.test(f.age.trim()) && Number(f.age) <= 120)) return 'Age must be a number up to 120.';
  return '';
}

// Form -> member fields (trimmed, CareID upper-cased, allergies as an array).
export function fromForm(f) {
  return {
    relation: f.relation,
    name: f.name.trim(),
    idType: f.idType,
    idValue: f.idType === 'CareID' ? f.idValue.trim().toUpperCase() : f.idValue.trim(),
    age: f.age.trim(),
    gender: f.gender,
    bloodGroup: f.bloodGroup.trim(),
    allergies: parseAllergies(f.allergiesText),
    conditions: f.conditions.trim() || 'None',
  };
}

export const addMember = (list, fields) => [
  ...list,
  { ...fields, id: `f${Date.now().toString(36)}${list.length}`, prescriptions: [] },
];

export const updateMember = (list, id, fields) =>
  list.map((m) => (m.id === id ? { ...m, ...fields } : m)); // keeps id and prescriptions

export const removeMember = (list, id) => list.filter((m) => m.id !== id);
