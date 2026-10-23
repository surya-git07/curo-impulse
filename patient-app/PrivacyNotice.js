import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, SafeAreaView } from 'react-native';

const TEAL = '#0d7377';

// Static DPDP-style privacy notice. Shown from Settings and the signup consent link.
export default function PrivacyNotice({ onBack }) {
  const Section = ({ title, children }) => (
    <View style={styles.section}>
      <Text style={styles.h2}>{title}</Text>
      <Text style={styles.body}>{children}</Text>
    </View>
  );

  return (
    <SafeAreaView style={styles.screen}>
      <View style={styles.header}>
        <TouchableOpacity onPress={onBack}>
          <Text style={styles.back}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Privacy Notice</Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <Section title="What we collect">
          Your name, phone number, CareID, and the health details you add — allergies,
          conditions, prescriptions and dose history.
        </Section>
        <Section title="Why we collect it">
          To show your medicines in one place, check for drug interactions and allergy
          conflicts, remind you about doses, and let your doctor and pharmacy coordinate care.
        </Section>
        <Section title="Who can see it">
          You, and the doctors, caregivers and pharmacies you share your CareID with. Every
          access is recorded in your access log.
        </Section>
        <Section title="Your rights">
          You can view, edit and delete your data at any time, and withdraw consent by
          deleting your account.
        </Section>
        <Section title="Retention">
          We keep your records while your account is active. Deleting your account removes
          your personal data.
        </Section>
        <Section title="Security">
          Data is sent over encrypted connections and protected with access controls. We log
          access for accountability.
        </Section>
        <Section title="Contact">
          For any privacy question, reach the Curo team at privacy@curo.example.
        </Section>

        <TouchableOpacity style={styles.button} onPress={onBack}>
          <Text style={styles.buttonText}>Back</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f0f7f7' },
  header: { padding: 20, paddingBottom: 10 },
  back: { color: TEAL, fontSize: 15, fontWeight: '600', marginBottom: 6 },
  title: { fontSize: 24, fontWeight: 'bold', color: TEAL },
  content: { padding: 20, paddingTop: 4 },
  section: { marginBottom: 18 },
  h2: { fontSize: 15, fontWeight: '700', color: '#1a3c3d', marginBottom: 4 },
  body: { fontSize: 14, color: '#445', lineHeight: 20 },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 8 },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
});