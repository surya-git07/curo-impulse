import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import {
  RELATIONS, GENDERS, ID_TYPES, BLANK_MEMBER,
  toForm, fromForm, validateMember, addMember, updateMember, removeMember,
} from './familyLogic';
import { colors, type, card as cardStyle, shadow } from './theme';

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
      <TextInput style={styles.input} placeholderTextColor={colors.placeholder} value={form[key]} onChangeText={(t) => set(key, t)} {...extra} />
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
                <View style={styles.avatar}><Text style={styles.avatarText}>{(m.name || '?').charAt(0).toUpperCase()}</Text></View>
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
  heading: { ...type.sectionLabel, marginTop: 20, marginBottom: 10 },
  button: { backgroundColor: colors.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center', ...shadow },
  buttonText: { color: colors.white, fontWeight: '700', fontSize: 16 },
  link: { color: colors.primary, textAlign: 'center', marginTop: 16, fontWeight: '600', fontSize: 13 },

  card: { ...cardStyle, padding: 16, marginBottom: 12 },
  cardTop: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  avatar: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  avatarText: { fontSize: 16, fontWeight: '800', color: colors.primary },
  name: { flex: 1, fontSize: 16, fontWeight: '700', color: colors.text, marginRight: 8 },
  relationTag: { backgroundColor: colors.primarySoft, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10 },
  relationText: { fontSize: 11, fontWeight: '700', color: colors.primary },
  sub: { fontSize: 13, color: colors.textMuted, marginTop: 3 },

  actions: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginTop: 12, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 12 },
  confirmText: { fontSize: 13, color: colors.danger, fontWeight: '600', marginRight: 10 },
  editBtn: { backgroundColor: colors.primarySoft, borderRadius: 10, paddingHorizontal: 16, paddingVertical: 9, marginRight: 8 },
  editText: { color: colors.primary, fontWeight: '700', fontSize: 13 },
  deleteBtn: { backgroundColor: colors.dangerSoft, borderRadius: 10, paddingHorizontal: 16, paddingVertical: 9, marginRight: 8, borderWidth: 1, borderColor: '#FECACA' },
  deleteText: { color: colors.danger, fontWeight: '700', fontSize: 13 },

  formCard: { ...cardStyle, padding: 20 },
  formTitle: { fontSize: 20, fontWeight: '800', color: colors.text, marginBottom: 16 },
  field: { marginBottom: 16 },
  label: { ...type.sectionLabel, fontSize: 11, marginBottom: 6 },
  input: { backgroundColor: colors.bg, borderRadius: 12, padding: 13, borderWidth: 1, borderColor: colors.border, fontSize: 16, color: colors.text },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 10, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.primary, fontWeight: '600', fontSize: 13 },
  chipTextOn: { color: colors.white },
  error: { color: colors.danger, fontWeight: '600', fontSize: 13, backgroundColor: colors.dangerSoft, borderRadius: 10, padding: 10, marginBottom: 12, overflow: 'hidden' },

  empty: { alignItems: 'center', paddingVertical: 36, paddingHorizontal: 24 },
  emptyIcon: { fontSize: 40, marginBottom: 12, opacity: 0.6 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: colors.textMuted },
  emptySub: { ...type.secondary, marginTop: 4, textAlign: 'center' },
});
