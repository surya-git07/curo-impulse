import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';

const TEAL = '#0d7377';

// Placeholder family data — replace with mocks/family.js or live data if available.
const FAMILY_MEMBERS = [
  { id: 'f1', name: 'Demo Patient One', relation: 'Self', careId: 'CID-DMPAT2' },
];

export default function FamilyTab() {
  return (
    <ScrollView contentContainerStyle={styles.wrap}>
      <Text style={styles.label}>Linked family members</Text>
      {FAMILY_MEMBERS.map((m) => (
        <View key={m.id} style={styles.card}>
          <Text style={styles.name}>{m.name}</Text>
          <Text style={styles.sub}>{m.relation} · {m.careId}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingVertical: 8 },
  label: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginBottom: 8, textTransform: 'uppercase', letterSpacing: 0.5 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: '#e0eaea' },
  name: { fontSize: 15, fontWeight: '700', color: '#1a3c3d' },
  sub: { fontSize: 13, color: '#667', marginTop: 2 },
});