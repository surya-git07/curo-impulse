import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Alert } from 'react-native';

const TEAL = '#0d7377';

// Simple appointment-booking tab (mock). Pick a doctor and a slot, then "book".
const DOCTORS = [
  { id: 'doc1', name: 'Dr. Mehta', dept: 'General Medicine', hospital: 'Kaveri Hospital' },
  { id: 'doc2', name: 'Dr. Rao', dept: 'Diabetology', hospital: 'City Clinic' },
];

const SLOTS = ['Today 5:00 PM', 'Tomorrow 10:30 AM', 'Tomorrow 4:00 PM'];

export default function BookTab() {
  const [doctor, setDoctor] = useState(null);
  const [slot, setSlot] = useState(null);
  const [booked, setBooked] = useState(null);

  const book = () => {
    if (!doctor || !slot) return Alert.alert('Pick a doctor and a time slot first');
    setBooked({ doctor, slot });
  };

  if (booked) {
    return (
      <View style={styles.wrap}>
        <View style={styles.confirmCard}>
          <Text style={styles.confirmTick}>✓</Text>
          <Text style={styles.confirmTitle}>Appointment booked</Text>
          <Text style={styles.confirmText}>
            {booked.doctor.name} · {booked.doctor.dept}{'\n'}{booked.slot}
          </Text>
        </View>
        <TouchableOpacity style={styles.button} onPress={() => { setBooked(null); setDoctor(null); setSlot(null); }}>
          <Text style={styles.buttonText}>Book another</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.wrap}>
      <Text style={styles.label}>Choose a doctor</Text>
      {DOCTORS.map((d) => (
        <TouchableOpacity
          key={d.id}
          style={[styles.option, doctor?.id === d.id && styles.optionActive]}
          onPress={() => setDoctor(d)}
        >
          <Text style={[styles.optionTitle, doctor?.id === d.id && styles.optionTitleActive]}>{d.name}</Text>
          <Text style={styles.optionSub}>{d.dept} · {d.hospital}</Text>
        </TouchableOpacity>
      ))}

      <Text style={[styles.label, { marginTop: 16 }]}>Choose a time</Text>
      {SLOTS.map((s) => (
        <TouchableOpacity
          key={s}
          style={[styles.option, slot === s && styles.optionActive]}
          onPress={() => setSlot(s)}
        >
          <Text style={[styles.optionTitle, slot === s && styles.optionTitleActive]}>{s}</Text>
        </TouchableOpacity>
      ))}

      <TouchableOpacity style={[styles.button, { marginTop: 20 }]} onPress={book}>
        <Text style={styles.buttonText}>Book appointment</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingVertical: 8 },
  label: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginBottom: 8, textTransform: 'uppercase', letterSpacing: 0.5 },
  option: { backgroundColor: '#fff', borderRadius: 12, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: '#e0eaea' },
  optionActive: { borderColor: TEAL, backgroundColor: '#eef6f6' },
  optionTitle: { fontSize: 15, fontWeight: '700', color: '#1a3c3d' },
  optionTitleActive: { color: TEAL },
  optionSub: { fontSize: 13, color: '#667', marginTop: 2 },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  confirmCard: { backgroundColor: '#fff', borderRadius: 16, padding: 24, alignItems: 'center', marginBottom: 16, borderWidth: 2, borderColor: TEAL },
  confirmTick: { fontSize: 36, color: '#2e9e5b' },
  confirmTitle: { fontSize: 18, fontWeight: '700', color: '#1a3c3d', marginVertical: 8 },
  confirmText: { fontSize: 14, color: '#445', textAlign: 'center', lineHeight: 20 },
});