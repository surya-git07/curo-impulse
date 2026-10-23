import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView } from 'react-native';
import { MOCK_CONSENT_REQUESTS } from './mocks/consent';

const TEAL = '#0d7377';
const GREEN = '#2e9e5b';
const RED = '#c0392b';

// Consent approval: patient approves or denies a pending access request from
// a doctor or caregiver. Approving/denying writes an access-log entry.
// onDecision(request, decision) lets the parent add the access-log row.
export default function ConsentApproval({ onBack, onDecision }) {
  const [requests, setRequests] = useState(MOCK_CONSENT_REQUESTS);

  const decide = (req, decision) => {
    setRequests((prev) => prev.map((r) => (r.id === req.id ? { ...r, status: decision } : r)));
    if (onDecision) onDecision(req, decision);
  };

  const pending = requests.filter((r) => r.status === 'pending');
  const decided = requests.filter((r) => r.status !== 'pending');

  return (
    <ScrollView contentContainerStyle={styles.wrap}>
      <TouchableOpacity onPress={onBack}><Text style={styles.link}>‹ Back to Settings</Text></TouchableOpacity>
      <Text style={styles.heading}>Access Requests</Text>
      <Text style={styles.subtitle}>Approve or deny requests to view your record.</Text>

      {pending.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyIcon}>✅</Text>
          <Text style={styles.emptyTitle}>No pending requests</Text>
        </View>
      ) : (
        pending.map((r) => (
          <View key={r.id} style={styles.card}>
            <View style={styles.cardTop}>
              <Text style={styles.name}>{r.requesterName}</Text>
              <Text style={styles.role}>{r.requesterRole}</Text>
            </View>
            <Text style={styles.org}>{r.org}</Text>
            <Text style={styles.reason}>"{r.reason}"</Text>
            <View style={styles.actions}>
              <TouchableOpacity style={styles.denyButton} onPress={() => decide(r, 'denied')}>
                <Text style={styles.denyText}>Deny</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.approveButton} onPress={() => decide(r, 'approved')}>
                <Text style={styles.approveText}>Approve</Text>
              </TouchableOpacity>
            </View>
          </View>
        ))
      )}

      {decided.length > 0 && (
        <>
          <Text style={styles.sectionTitle}>Decided</Text>
          {decided.map((r) => (
            <View key={r.id} style={styles.card}>
              <View style={styles.cardTop}>
                <Text style={styles.name}>{r.requesterName}</Text>
                <View style={[styles.statusPill, r.status === 'approved' ? styles.statusApproved : styles.statusDenied]}>
                  <Text style={styles.statusPillText}>{r.status}</Text>
                </View>
              </View>
              <Text style={styles.org}>{r.org}</Text>
            </View>
          ))}
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingVertical: 8 },
  link: { color: TEAL, fontWeight: '600', marginBottom: 12 },
  heading: { fontSize: 22, fontWeight: '700', color: TEAL, marginBottom: 4 },
  subtitle: { fontSize: 13, color: '#667', marginBottom: 16, lineHeight: 18 },

  empty: { alignItems: 'center', padding: 30 },
  emptyIcon: { fontSize: 36, marginBottom: 8 },
  emptyTitle: { fontSize: 15, fontWeight: '700', color: '#333' },

  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, elevation: 2 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  name: { fontSize: 16, fontWeight: '700', color: '#1a3c3d' },
  role: { fontSize: 11, color: TEAL, backgroundColor: '#e3f1f1', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10 },
  org: { fontSize: 13, color: '#667', marginTop: 4 },
  reason: { fontSize: 13, color: '#445', marginTop: 8, fontStyle: 'italic' },

  actions: { flexDirection: 'row', gap: 10, marginTop: 14 },
  denyButton: { flex: 1, backgroundColor: '#fff3f3', borderRadius: 8, paddingVertical: 10, alignItems: 'center', borderWidth: 1, borderColor: '#f0c0c0' },
  denyText: { color: RED, fontWeight: '700', fontSize: 13 },
  approveButton: { flex: 1, backgroundColor: GREEN, borderRadius: 8, paddingVertical: 10, alignItems: 'center' },
  approveText: { color: '#fff', fontWeight: '700', fontSize: 13 },

  sectionTitle: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginVertical: 12, textTransform: 'uppercase', letterSpacing: 0.5 },
  statusPill: { paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10 },
  statusApproved: { backgroundColor: '#e3f5ea' },
  statusDenied: { backgroundColor: '#fdecea' },
  statusPillText: { fontSize: 11, fontWeight: '700', color: '#333', textTransform: 'capitalize' },
});