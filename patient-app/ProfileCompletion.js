import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView, SafeAreaView, Alert } from 'react-native';
import { BLANK_PROFILE } from './mocks/data';

import { colors, type, card as cardStyle, shadow } from './theme';

// Medical profile form, shown after first signup and from Settings > Edit.
// onDone receives the filled profile (App saves allergies to the backend, the rest stays local).
// initialAllergies pre-fills the allergies box even before the profile is marked completed;
// without it a first-time save would send [] and wipe allergies already on the server.
export default function ProfileCompletion({ existingProfile, initialAllergies, saving, onDone, onCancel }) {
  const [form, setForm] = useState({ ...BLANK_PROFILE, ...(existingProfile || {}) });
  // Allergies are an array in the data; edit them as comma-separated text.
  const [allergiesText, setAllergiesText] = useState((initialAllergies || (existingProfile && existingProfile.allergies) || []).join(', '));

  const set = (key, val) => setForm((f) => ({ ...f, [key]: val }));

  const field = (key, label, extra = {}) => (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={[styles.input, extra.multiline && styles.inputMulti]} placeholderTextColor={colors.placeholder} value={form[key]} onChangeText={(t) => set(key, t)} {...extra} />
    </View>
  );

  const picker = (key, label, options) => (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.chipRow}>
        {options.map((o) => (
          <TouchableOpacity
            key={o}
            style={[styles.chip, form[key] === o && styles.chipActive]}
            onPress={() => set(key, o)}
          >
            <Text style={[styles.chipText, form[key] === o && styles.chipTextActive]}>{o}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );

  const submit = () => {
    if (saving) return;
    if (!form.age || !form.gender) return Alert.alert('Please fill at least age and gender');
    const allergies = allergiesText.split(',').map((a) => a.trim()).filter((a) => a && a.toLowerCase() !== 'none');
    onDone({ ...form, allergies, profileCompleted: true });
  };

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Complete your profile</Text>
        <Text style={styles.subtitle}>This helps doctors prescribe safely.</Text>

        <Text style={styles.sectionLabel}>The basics</Text>
        <View style={styles.section}>
          {field('age', 'Age', { keyboardType: 'number-pad' })}
          {picker('gender', 'Gender', ['Female', 'Male', 'Other'])}
          {picker('bloodGroup', 'Blood group', ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-'])}
          {field('heightCm', 'Height (cm)', { keyboardType: 'number-pad' })}
          {field('weightKg', 'Weight (kg)', { keyboardType: 'number-pad' })}
        </View>

        <Text style={styles.sectionLabel}>Allergies</Text>
        <View style={[styles.section, styles.sectionAlert]}>
          <View style={styles.field}>
            <Text style={styles.label}>Known allergies</Text>
            <TextInput style={styles.input} placeholderTextColor={colors.placeholder} value={allergiesText} onChangeText={setAllergiesText} placeholder="e.g. Penicillin, Sulfa (comma separated, empty if none)" />
            <Text style={styles.hint}>⚠️  We use this to warn you and your doctors about conflicts.</Text>
          </View>
        </View>

        <Text style={styles.sectionLabel}>Medical history</Text>
        <View style={styles.section}>
          {field('conditions', 'Ongoing conditions', { placeholder: 'e.g. Diabetes' })}
          {picker('pregnancy', 'Pregnancy', ['Not applicable', 'Not pregnant', 'Pregnant'])}
          {field('surgeries', 'Past surgeries', { multiline: true })}
          {field('currentMedications', 'Current medications', { multiline: true })}
          {field('traditionalMedicine', 'Traditional medicine', { multiline: true, placeholder: 'e.g. Ayurveda, none' })}
        </View>

        <TouchableOpacity style={[styles.button, saving && styles.buttonBusy]} onPress={submit} disabled={saving} activeOpacity={0.85}>
          <Text style={styles.buttonText}>{saving ? 'Saving…' : 'Save profile'}</Text>
        </TouchableOpacity>
        {onCancel && (
          <TouchableOpacity onPress={onCancel}>
            <Text style={styles.link}>Cancel</Text>
          </TouchableOpacity>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  content: { padding: 20, paddingBottom: 40 },
  title: { ...type.screenTitle, color: colors.primary, marginBottom: 4 },
  subtitle: { ...type.secondary, fontSize: 16, marginBottom: 8 },
  sectionLabel: { ...type.sectionLabel, marginTop: 20, marginBottom: 10 },
  section: { ...cardStyle, padding: 16, paddingBottom: 0, marginBottom: 4 },
  sectionAlert: { borderLeftWidth: 5, borderLeftColor: colors.accent },
  field: { marginBottom: 16 },
  label: { ...type.sectionLabel, fontSize: 11, marginBottom: 6 },
  input: { backgroundColor: colors.bg, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 13, borderWidth: 1, borderColor: colors.border, fontSize: 16, color: colors.text },
  inputMulti: { minHeight: 76, textAlignVertical: 'top' },
  hint: { ...type.secondary, marginTop: 8 },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 10, borderRadius: 10, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.card },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.primary, fontWeight: '600', fontSize: 13 },
  chipTextActive: { color: colors.white },
  button: { backgroundColor: colors.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center', marginTop: 24, ...shadow },
  buttonBusy: { opacity: 0.6 },
  buttonText: { color: colors.white, fontWeight: '700', fontSize: 16 },
  link: { color: colors.primary, textAlign: 'center', marginTop: 16, fontWeight: '600', fontSize: 13 },
});
