import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import {
  RELATIONS, GENDERS, ID_TYPES, BLANK_MEMBER,
  toForm, fromForm, validateMember, addMember, updateMember, removeMember,
} from './familyLogic';

const TEAL = '#0d7377';

// Family members tab (local state, lifted into App so it survives tab switches).
// members / onChange come from App; there is no backend family endpoint yet.
export default function FamilyTab({ members, onChange }) {
  const [form, setForm] = useState(null);          // null = closed; otherwise the open add/edit form
  const [editingId, setEditingId] = useState(null); // null = adding
  const [error, setError] = useState('');
  const [confirmId, setConfirmId] = useState(null); // member awaiting delete confirmation

  const set = (key, val) => setForm((f) => ({ ...f, [key]: val }));

  const openAdd = () => { setEditingId(null); setForm({ ...BLANK_MEMBER }); setError(''); setConfirmId(null); };
  const openEdit = (m) => { setEditingId(m.id); setForm(toForm(m)); setError(''); setConfirmId(null); };
  const close = () => { setForm(null); setEditingId(null); setError(''); };

  const save = () => {
    const problem = validateMember(form);
    if (problem) return setError(problem);
    const fields = fromForm(form);
    onChange(editingId ? updateMember(members, editingId, fields) : addMember(members, fields));
    close();
  };

  const remove = (id) => { onChange(removeMember(members, id)); setConfirmId(null); if (editingId === id) close(); };

  const chips = (key, options) => (
    <View style={styles.chipRow}>
      {options.map((o) => (
        <TouchableOpacity key={o} style={[styles.chip, form[key] === o && styles.chipOn]} onPress={() => set(key, o)}>
          <Text style={[styles.chipText, form[key] === o && styles.chipTextOn]}>{o}</Text>
        </TouchableOpacity>
      ))}
    </View>
  );

  const input = (key, label, extra = {}) => (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={styles.input} value={form[key]} onChangeText={(t) => set(key, t)} {...extra} />
    </View>
  );

  return (
    <ScrollView contentContainerStyle={styles.wrap} keyboardShouldPersistTaps="handled">
      {form ? (
        <View style={styles.formCard}>
          <Text style={styles.formTitle}>{editingId ? 'Edit family member' : 'Add family member'}</Text>

          <View style={styles.field}>
            <Text style={styles.label}>Relation</Text>
            {chips('relation', RELATIONS)}
          </View>
          {input('name', 'Full name', { placeholder: 'e.g. Lakshmi Rao' })}
          <View style={styles.field}>
            <Text style={styles.label}>Identify them by</Text>
            {chips('idType', ID_TYPES)}
          </View>
          {input('idValue', form.idType === 'CareID' ? 'CareID' : 'Phone number', {
            placeholder: form.idType === 'CareID' ? 'CID-AB12CD' : '10-digit number',
            autoCapitalize: form.idType === 'CareID' ? 'characters' : 'none',
            keyboardType: form.idType === 'Phone' ? 'number-pad' : 'default',
            autoCorrect: false,
          })}
          {input('age', 'Age (optional)', { keyboardType: 'number-pad' })}
          <View style={styles.field}>
            <Text style={styles.label}>Gender (optional)</Text>
            {chips('gender', GENDERS)}
          </View>
          {input('bloodGroup', 'Blood group (optional)', { placeholder: 'e.g. B+', autoCapitalize: 'characters' })}
          {input('allergiesText', 'Allergies (optional)', { placeholder: 'Comma separated, empty if none' })}
          {input('conditions', 'Ongoing conditions (optional)', { placeholder: 'e.g. Hypertension' })}

          {error ? <Text style={styles.error}>{error}</Text> : null}

          <TouchableOpacity style={styles.button} onPress={save}>
            <Text style={styles.buttonText}>{editingId ? 'Save changes' : 'Add member'}</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={close}><Text style={styles.link}>Cancel</Text></TouchableOpacity>
        </View>
      ) : (
        <>
          <TouchableOpacity style={styles.button} onPress={openAdd}>
            <Text style={styles.buttonText}>＋  Add family member</Text>
          </TouchableOpacity>

          <Text style={styles.heading}>Family members ({members.length})</Text>
          {members.length === 0 ? (
            <View style={styles.empty}>
              <Text style={styles.emptyIcon}>👪</Text>
              <Text style={styles.emptyTitle}>No family members yet</Text>
              <Text style={styles.emptySub}>Add someone to keep their medicines in one place.</Text>
            </View>
          ) : members.map((m) => (
            <View key={m.id} style={styles.card}>
              <View style={styles.cardTop}>
                <Text style={styles.name}>{m.name}</Text>
                <View style={styles.relationTag}><Text style={styles.relationText}>{m.relation}</Text></View>
              </View>
              <Text style={styles.sub}>{m.idType}: {m.idValue}</Text>
              <Text style={styles.sub}>
                {[m.age ? `${m.age} yrs` : null, m.gender || null, m.bloodGroup || null].filter(Boolean).join(' · ') || 'No details added'}
              </Text>
              <Text style={styles.sub}>Allergies: {m.allergies && m.allergies.length > 0 ? m.allergies.join(', ') : 'None'}</Text>
              <Text style={styles.sub}>Conditions: {m.conditions || 'None'}</Text>
              {m.prescriptions && m.prescriptions.length > 0 ? (
                <Text style={styles.sub}>Medicines: {m.prescriptions.map((p) => `${p.drugName} ${p.dosage}`).join(', ')}</Text>
              ) : null}

              {confirmId === m.id ? (
                <View style={styles.actions}>
                  <Text style={styles.confirmText}>Remove {m.name}?</Text>
                  <TouchableOpacity style={styles.deleteBtn} onPress={() => remove(m.id)}><Text style={styles.deleteText}>Yes, remove</Text></TouchableOpacity>
                  <TouchableOpacity style={styles.editBtn} onPress={() => setConfirmId(null)}><Text style={styles.editText}>Keep</Text></TouchableOpacity>
                </View>
              ) : (
                <View style={styles.actions}>
                  <TouchableOpacity style={styles.editBtn} onPress={() => openEdit(m)}><Text style={styles.editText}>Edit</Text></TouchableOpacity>
                  <TouchableOpacity style={styles.deleteBtn} onPress={() => setConfirmId(m.id)}><Text style={styles.deleteText}>Delete</Text></TouchableOpacity>
                </View>
              )}
            </View>
          ))}
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingVertical: 8 },
  heading: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginVertical: 12, textTransform: 'uppercase', letterSpacing: 0.5 },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  link: { color: TEAL, textAlign: 'center', marginTop: 16, fontWeight: '600' },

  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, elevation: 2 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 },
  name: { fontSize: 16, fontWeight: '700', color: '#1a3c3d', flexShrink: 1, marginRight: 8 },
  relationTag: { backgroundColor: '#e3f5f5', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10 },
  relationText: { fontSize: 11, fontWeight: '700', color: TEAL },
  sub: { fontSize: 13, color: '#667', marginTop: 3 },

  actions: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginTop: 12, borderTopWidth: 1, borderTopColor: '#eef2f2', paddingTop: 10 },
  confirmText: { fontSize: 13, color: '#7d1128', fontWeight: '600', marginRight: 10 },
  editBtn: { backgroundColor: '#eef6f6', borderRadius: 8, paddingHorizontal: 16, paddingVertical: 9, marginRight: 8 },
  editText: { color: TEAL, fontWeight: '700', fontSize: 13 },
  deleteBtn: { backgroundColor: '#fff3f3', borderRadius: 8, paddingHorizontal: 16, paddingVertical: 9, marginRight: 8, borderWidth: 1, borderColor: '#f0c0c0' },
  deleteText: { color: '#c0392b', fontWeight: '700', fontSize: 13 },

  formCard: { backgroundColor: '#fff', borderRadius: 14, padding: 16, elevation: 2 },
  formTitle: { fontSize: 18, fontWeight: '700', color: TEAL, marginBottom: 14 },
  field: { marginBottom: 14 },
  label: { fontSize: 13, fontWeight: '600', color: '#5a6a6a', marginBottom: 6 },
  input: { backgroundColor: '#f7fbfb', borderRadius: 10, padding: 12, borderWidth: 1, borderColor: '#cde' },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 10, borderWidth: 1, borderColor: '#cde', backgroundColor: '#fff' },
  chipOn: { backgroundColor: TEAL, borderColor: TEAL },
  chipText: { color: TEAL, fontWeight: '600' },
  chipTextOn: { color: '#fff' },
  error: { color: '#c0392b', fontWeight: '600', marginBottom: 10 },

  empty: { alignItems: 'center', padding: 30 },
  emptyIcon: { fontSize: 40, marginBottom: 10 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: '#333' },
  emptySub: { fontSize: 13, color: '#8a9a9a', marginTop: 4, textAlign: 'center' },
});
