import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView, SafeAreaView, Alert } from 'react-native';
import { BLANK_PROFILE } from './mocks/data';

const TEAL = '#0d7377';

// Medical profile form, shown after first signup and from Settings > Edit.
// All local/mock: onDone receives the filled profile.
export default function ProfileCompletion({ existingProfile, onDone, onCancel }) {
  const [form, setForm] = useState({ ...BLANK_PROFILE, ...(existingProfile || {}) });
  // Allergies are an array in the data; edit them as comma-separated text.
  const [allergiesText, setAllergiesText] = useState(((existingProfile && existingProfile.allergies) || []).join(', '));

  const set = (key, val) => setForm((f) => ({ ...f, [key]: val }));

  const field = (key, label, extra = {}) => (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={styles.input} value={form[key]} onChangeText={(t) => set(key, t)} {...extra} />
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
    if (!form.age || !form.gender) return Alert.alert('Please fill at least age and gender');
    const allergies = allergiesText.split(',').map((a) => a.trim()).filter((a) => a && a.toLowerCase() !== 'none');
    onDone({ ...form, allergies, profileCompleted: true });
  };

  return (
    <SafeAreaView style={styles.screen}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Complete your profile</Text>
        <Text style={styles.subtitle}>This helps doctors prescribe safely.</Text>

        {field('age', 'Age', { keyboardType: 'number-pad' })}
        {picker('gender', 'Gender', ['Female', 'Male', 'Other'])}
        {picker('bloodGroup', 'Blood group', ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-'])}
        {field('heightCm', 'Height (cm)', { keyboardType: 'number-pad' })}
        {field('weightKg', 'Weight (kg)', { keyboardType: 'number-pad' })}
        <View style={styles.field}>
          <Text style={styles.label}>Allergies</Text>
          <TextInput style={styles.input} value={allergiesText} onChangeText={setAllergiesText} placeholder="e.g. Penicillin, Sulfa (comma separated, empty if none)" />
        </View>
        {field('conditions', 'Ongoing conditions', { placeholder: 'e.g. Diabetes' })}
        {picker('pregnancy', 'Pregnancy', ['Not applicable', 'Not pregnant', 'Pregnant'])}
        {field('surgeries', 'Past surgeries', { multiline: true })}
        {field('currentMedications', 'Current medications', { multiline: true })}
        {field('traditionalMedicine', 'Traditional medicine', { multiline: true, placeholder: 'e.g. Ayurveda, none' })}

        <TouchableOpacity style={styles.button} onPress={submit}>
          <Text style={styles.buttonText}>Save profile</Text>
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
  screen: { flex: 1, backgroundColor: '#f0f7f7' },
  content: { padding: 24 },
  title: { fontSize: 24, fontWeight: 'bold', color: TEAL, marginBottom: 4 },
  subtitle: { fontSize: 14, color: '#555', marginBottom: 20 },
  field: { marginBottom: 16 },
  label: { fontSize: 13, fontWeight: '600', color: '#5a6a6a', marginBottom: 6 },
  input: { backgroundColor: '#fff', borderRadius: 10, padding: 14, borderWidth: 1, borderColor: '#cde' },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 10, borderRadius: 10, borderWidth: 1, borderColor: '#cde', backgroundColor: '#fff' },
  chipActive: { backgroundColor: TEAL, borderColor: TEAL },
  chipText: { color: TEAL, fontWeight: '600' },
  chipTextActive: { color: '#fff' },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 8 },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  link: { color: TEAL, textAlign: 'center', marginTop: 16, fontWeight: '600' },
});