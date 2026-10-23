import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet,
  ScrollView, SafeAreaView, StatusBar,
} from 'react-native';
import { MOCK_PATIENT, MOCK_MEDICINES, MOCK_TODAY_SCHEDULE } from './mocks/data';

// ---------------------------------------------------------------------------
// Curo patient app — mock-data build.
// No backend yet — runs entirely on mock data. Three tabs: My Medicines, Today, Profile.
// When the API contract lands, replace the mock state below with fetch calls
// that use API_URL from config.js, and the screens stay the same.
// ---------------------------------------------------------------------------

export default function App() {
  // 'login' | 'signup' | 'app'
  const [screen, setScreen] = useState('login');

  // Signup form + the CareID we show after signing up.
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [newCareId, setNewCareId] = useState('');

  // The signed-in patient and their data (mock).
  const [patient, setPatient] = useState(null);
  const [medicines, setMedicines] = useState([]);
  const [schedule, setSchedule] = useState([]);

  const [tab, setTab] = useState('meds'); // 'meds' | 'today' | 'profile'

  // Generate a CareID: CID- followed by 6 characters.
  const makeCareId = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += chars[Math.floor(Math.random() * chars.length)];
    }
    return `CID-${code}`;
  };

  const handleSignup = () => {
    // Pretend the backend created the account and returned a CareID.
    setNewCareId(makeCareId());
  };

  const handleLogin = () => {
    // Load the mock patient and their data, then enter the app.
    setPatient(MOCK_PATIENT);
    setMedicines(MOCK_MEDICINES);
    setSchedule(MOCK_TODAY_SCHEDULE);
    setScreen('app');
    setTab('meds');
  };

  // Flip a dose between taken and not taken.
  const toggleTaken = (doseId) => {
    setSchedule((prev) =>
      prev.map((d) => (d.id === doseId ? { ...d, taken: !d.taken } : d))
    );
  };

  // ----- SIGNUP -----------------------------------------------------------
  if (screen === 'signup') {
    // After signup we show the CareID and a button to continue to login.
    if (newCareId) {
      return (
        <SafeAreaView style={styles.screen}>
          <StatusBar barStyle="dark-content" />
          <View style={styles.authBody}>
            <Text style={styles.brand}>Curo</Text>
            <View style={styles.careIdCard}>
              <Text style={styles.careIdLabel}>Your CareID</Text>
              <Text style={styles.careIdValue}>{newCareId}</Text>
              <Text style={styles.careIdNote}>
                This is your health ID. Save it — doctors and pharmacies use it
                to find your shared medicine record.
              </Text>
            </View>
            <TouchableOpacity
              style={styles.button}
              onPress={() => {
                setNewCareId('');
                setPassword('');
                setScreen('login');
              }}
            >
              <Text style={styles.buttonText}>Continue to Login</Text>
            </TouchableOpacity>
          </View>
        </SafeAreaView>
      );
    }

    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar barStyle="dark-content" />
        <ScrollView contentContainerStyle={styles.authBody} keyboardShouldPersistTaps="handled">
          <Text style={styles.brand}>Curo</Text>
          <Text style={styles.subtitle}>Create your account</Text>

          <TextInput
            style={styles.input}
            placeholder="Full name"
            value={name}
            onChangeText={setName}
          />
          <TextInput
            style={styles.input}
            placeholder="Phone number"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
          />
          <TextInput
            style={styles.input}
            placeholder="Set a password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
          />

          <TouchableOpacity
            style={[styles.button, (!name || !phone || !password) && styles.buttonDisabled]}
            onPress={handleSignup}
            disabled={!name || !phone || !password}
          >
            <Text style={styles.buttonText}>Sign Up</Text>
          </TouchableOpacity>

          <TouchableOpacity onPress={() => setScreen('login')}>
            <Text style={styles.link}>Already have an account? Log in</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ----- LOGIN ------------------------------------------------------------
  if (screen === 'login') {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar barStyle="dark-content" />
        <View style={styles.authBody}>
          <Text style={styles.brand}>Curo</Text>
          <Text style={styles.subtitle}>Log in to your medicine record</Text>

          <TextInput
            style={styles.input}
            placeholder="Phone number"
            value={phone}
            onChangeText={setPhone}
            keyboardType="phone-pad"
          />
          <TextInput
            style={styles.input}
            placeholder="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
          />

          <TouchableOpacity style={styles.button} onPress={handleLogin}>
            <Text style={styles.buttonText}>Log In</Text>
          </TouchableOpacity>

          <TouchableOpacity onPress={() => setScreen('signup')}>
            <Text style={styles.link}>New here? Sign up</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // ----- MAIN APP ---------------------------------------------------------
  const takenCount = schedule.filter((d) => d.taken).length;

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="dark-content" />

      <View style={styles.header}>
        <Text style={styles.headerTitle}>
          {tab === 'meds' ? 'My Medicines' : tab === 'today' ? "Today's Schedule" : 'Profile'}
        </Text>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* MY MEDICINES */}
        {tab === 'meds' && (
          <>
            {medicines.map((m) => (
              <View key={m.id} style={styles.card}>
                <View style={styles.cardTop}>
                  <Text style={styles.drugName}>{m.drug_name}</Text>
                  <Text style={styles.statusPill}>{m.status}</Text>
                </View>
                <Text style={styles.detail}>{m.dosage} · {m.frequency}</Text>
                <Text style={styles.detailFaint}>
                  {m.duration_days} days · {m.doctor_name}
                </Text>
                {m.allergy_conflict && (
                  <View style={styles.warnBox}>
                    <Text style={styles.warnText}>
                      ⚠️ Conflicts with your recorded allergy. Check with your doctor.
                    </Text>
                  </View>
                )}
              </View>
            ))}
          </>
        )}

        {/* TODAY'S SCHEDULE */}
        {tab === 'today' && (
          <>
            <Text style={styles.progress}>
              {takenCount} of {schedule.length} doses taken
            </Text>
            {schedule.map((d) => (
              <View key={d.id} style={[styles.card, d.taken && styles.cardDone]}>
                <View style={styles.doseRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.drugName, d.taken && styles.doneText]}>
                      {d.drug_name}
                    </Text>
                    <Text style={styles.detail}>{d.dosage} · {d.time}</Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.markButton, d.taken && styles.markButtonDone]}
                    onPress={() => toggleTaken(d.id)}
                  >
                    <Text style={[styles.markButtonText, d.taken && styles.markButtonTextDone]}>
                      {d.taken ? '✓ Taken' : 'Mark taken'}
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </>
        )}

        {/* PROFILE */}
        {tab === 'profile' && patient && (
          <>
            <View style={styles.card}>
              <Text style={styles.cardLabel}>CareID</Text>
              <Text style={styles.careIdInline}>{patient.care_id}</Text>
              <View style={styles.divider} />
              <Text style={styles.cardLabel}>Name</Text>
              <Text style={styles.detail}>{patient.name}</Text>
              <View style={styles.divider} />
              <Text style={styles.cardLabel}>Phone</Text>
              <Text style={styles.detail}>{patient.phone}</Text>
              <View style={styles.divider} />
              <Text style={styles.cardLabel}>Allergies</Text>
              <Text style={styles.warnText}>{patient.allergies || 'None'}</Text>
            </View>

            <TouchableOpacity
              style={styles.logoutButton}
              onPress={() => {
                setPatient(null);
                setPassword('');
                setScreen('login');
              }}
            >
              <Text style={styles.logoutText}>Log Out</Text>
            </TouchableOpacity>
          </>
        )}
      </ScrollView>

      {/* BOTTOM TABS */}
      <View style={styles.tabBar}>
        <TouchableOpacity style={styles.tabItem} onPress={() => setTab('meds')}>
          <Text style={[styles.tabText, tab === 'meds' && styles.tabTextActive]}>💊{'\n'}Medicines</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.tabItem} onPress={() => setTab('today')}>
          <Text style={[styles.tabText, tab === 'today' && styles.tabTextActive]}>📅{'\n'}Today</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.tabItem} onPress={() => setTab('profile')}>
          <Text style={[styles.tabText, tab === 'profile' && styles.tabTextActive]}>👤{'\n'}Profile</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const TEAL = '#0d7377';

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f0f7f7' },

  // Auth
  authBody: { flexGrow: 1, justifyContent: 'center', padding: 24 },
  brand: { fontSize: 32, fontWeight: 'bold', color: TEAL, textAlign: 'center', marginBottom: 4 },
  subtitle: { fontSize: 15, color: '#555', textAlign: 'center', marginBottom: 24 },
  input: { backgroundColor: '#fff', borderRadius: 10, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#cde' },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 4 },
  buttonDisabled: { backgroundColor: '#9bc4c5' },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  link: { color: TEAL, textAlign: 'center', marginTop: 20 },

  // CareID reveal after signup
  careIdCard: { backgroundColor: '#fff', borderRadius: 16, padding: 24, alignItems: 'center', marginBottom: 24, borderWidth: 2, borderColor: TEAL },
  careIdLabel: { fontSize: 12, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 1 },
  careIdValue: { fontSize: 30, fontWeight: 'bold', color: TEAL, marginVertical: 8, letterSpacing: 2 },
  careIdNote: { fontSize: 13, color: '#667', textAlign: 'center', lineHeight: 19 },

  // Header
  header: { padding: 20, paddingBottom: 12 },
  headerTitle: { fontSize: 24, fontWeight: 'bold', color: TEAL },

  // Content + cards
  content: { padding: 16, paddingBottom: 24 },
  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, elevation: 2 },
  cardDone: { backgroundColor: '#eef7f0' },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  drugName: { fontSize: 17, fontWeight: '700', color: '#1a3c3d' },
  statusPill: { fontSize: 11, color: TEAL, backgroundColor: '#e3f1f1', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, overflow: 'hidden', textTransform: 'capitalize' },
  detail: { fontSize: 14, color: '#445', marginTop: 4 },
  detailFaint: { fontSize: 13, color: '#8a9a9a', marginTop: 2 },
  cardLabel: { fontSize: 11, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 0.5 },
  careIdInline: { fontSize: 18, fontWeight: '700', color: TEAL, marginTop: 2, letterSpacing: 1 },
  divider: { height: 1, backgroundColor: '#eef2f2', marginVertical: 10 },

  // Allergy warning
  warnBox: { backgroundColor: '#fff3f3', borderRadius: 8, padding: 10, marginTop: 10, borderWidth: 1, borderColor: '#f0c0c0' },
  warnText: { color: '#c0392b', fontSize: 13, fontWeight: '600' },

  // Today
  progress: { fontSize: 14, fontWeight: '600', color: '#5a6a6a', marginBottom: 12 },
  doseRow: { flexDirection: 'row', alignItems: 'center' },
  doneText: { textDecorationLine: 'line-through', color: '#8a9a9a' },
  markButton: { backgroundColor: TEAL, borderRadius: 8, paddingHorizontal: 14, paddingVertical: 10 },
  markButtonDone: { backgroundColor: '#cfe8d6' },
  markButtonText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  markButtonTextDone: { color: '#2e7d4f' },

  // Profile
  logoutButton: { backgroundColor: '#fff3f3', borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 8, borderWidth: 1, borderColor: '#f0c0c0' },
  logoutText: { color: '#c0392b', fontWeight: '700', fontSize: 15 },

  // Tab bar
  tabBar: { flexDirection: 'row', backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#e0eaea', paddingVertical: 8 },
  tabItem: { flex: 1, alignItems: 'center' },
  tabText: { fontSize: 11, color: '#8a9a9a', textAlign: 'center', fontWeight: '600' },
  tabTextActive: { color: TEAL },
});