// profileSave.js
// Saves the profile form. The backend stores allergies only (PUT /patient/me), so
// that is the one field sent; everything else on the form stays local.
// Never throws: if the call fails for any reason the edit is saved locally and the
// error is returned so the caller can show a toast. The user never loses an edit.

import * as api from './api';

// patient: current patient object; data: the form's output (incl. allergies array);
// live: true when this session is logged in to the real backend.
// -> { patient, synced, error }
export async function saveProfile({ patient, data, live }) {
  const local = { ...patient, ...data };
  if (!live) return { patient: local, synced: false, error: null };
  try {
    const updated = await api.updateAllergies(data.allergies || []);
    // The server trims, drops blanks and repeats: show what it actually stored.
    return { patient: { ...local, allergies: updated.allergies }, synced: true, error: null };
  } catch (e) {
    return { patient: local, synced: false, error: e };
  }
}
