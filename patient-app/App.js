import React, { useState, useRef } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, Alert,
  ScrollView, SafeAreaView, StatusBar, Animated, Platform, KeyboardAvoidingView,
} from 'react-native';
import { SafeAreaProvider, useSafeAreaInsets } from 'react-native-safe-area-context';
import * as Clipboard from 'expo-clipboard';

import ProfileCompletion from './ProfileCompletion';
import BottomNav from './BottomNav';
import FamilyTab from './FamilyTab';
import { saveProfile as persistProfile } from './profileSave';
import BookTab from './BookTab';
import QrTab from './QrTab';
import PrivacyNotice from './PrivacyNotice';
import ShareRecord from './ShareRecord';
import ConsentApproval from './ConsentApproval';
import * as api from './api';
import { MOCK_MODE } from './config';
import { findInteractions, findAllergyConflicts } from './ruleChecks';
import {
  MOCK_PATIENT, MOCK_PRESCRIPTIONS, MOCK_TODAY_SCHEDULE,
  MOCK_INTERACTIONS, SEVERITY_COLORS, MOCK_ACCESS_LOG, MOCK_FAMILY,
} from './mocks/data';

const TEAL = '#0d7377';

// ---- helpers --------------------------------------------------------------
function groupByVisit(prescriptions) {
  const groups = {};
  prescriptions.forEach((p) => {
    const key = p.visitId || p.id;
    if (!groups[key]) {
      groups[key] = { doctorName: p.doctorName, hospitalName: p.hospitalName, createdAt: p.createdAt, drugs: [] };
    }
    groups[key].drugs.push(p);
  });
  return Object.entries(groups).sort((a, b) => new Date(withZ(b[1].createdAt)) - new Date(withZ(a[1].createdAt)));
}

// Timestamps are naive UTC; append Z so they parse as UTC (fixes the ~5.5h IST shift).
function withZ(iso) {
  if (!iso) return iso;
  return /[zZ]|[+-]\d\d:\d\d$/.test(iso) ? iso : `${iso}Z`;
}

function formatVisitDate(iso) {
  if (!iso) return '';
  return new Date(withZ(iso)).toLocaleString('en-IN', {
    day: 'numeric', month: 'long', year: 'numeric', hour: 'numeric', minute: '2-digit',
  });
}

function getCourseTag(p) {
  const dispensed = p.status?.toLowerCase().includes('dispensed');
  if (!dispensed) return { label: 'Issued', style: 'tagIssued' };
  const start = p.dispensedAt || p.createdAt;
  const days = parseInt(p.durationDays, 10) || 0;
  if (start && days > 0) {
    const end = new Date(withZ(start));
    end.setDate(end.getDate() + days);
    if (new Date() > end) return { label: 'Course Finished', style: 'tagFinished' };
  }
  return { label: 'Active', style: 'tagActive' };
}

const ID_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
function makeCareId() {
  let id = '';
  for (let i = 0; i < 6; i++) id += ID_CHARS[Math.floor(Math.random() * ID_CHARS.length)];
  return `CID-${id}`;
}

// ---------------------------------------------------------------------------
function AppInner() {
  const insets = useSafeAreaInsets();

  const [screen, setScreen] = useState('login'); // login | signup | forgot | profile | app
  const [overlay, setOverlay] = useState(null);   // 'privacy' | 'accesslog' | null
  const [tab, setTab] = useState('home');          // home | today | qr | book | family | settings

  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [consentGiven, setConsentGiven] = useState(false);
  const [newCareId, setNewCareId] = useState('');

  const [resetOtp, setResetOtp] = useState('');
  const [sentOtp, setSentOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');

  const [patient, setPatient] = useState(null);
  const [prescriptions, setPrescriptions] = useState([]);   // History (always mock)
  const [homePrescriptions, setHomePrescriptions] = useState([]); // Home (live, or mock fallback)
  const [medications, setMedications] = useState(null);     // live only; null hides the card list
  const [schedule, setSchedule] = useState([]);
  const [profileLive, setProfileLive] = useState(false);    // true when the profile (allergies) came from the server
  const [scheduleLive, setScheduleLive] = useState(false);  // true when doses came from the server
  const [loggingIn, setLoggingIn] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [family, setFamily] = useState(MOCK_FAMILY);       // Family tab, local only; kept here so it survives tab switches

  const [search, setSearch] = useState('');
  const [antibioticOnly, setAntibioticOnly] = useState(false);
  const [showActive, setShowActive] = useState(false);
  const [visitExpanded, setVisitExpanded] = useState({});

  const [toast, setToast] = useState('');
  const fade = useRef(new Animated.Value(0)).current;

  const showToast = (msg) => {
    setToast(msg);
    Animated.timing(fade, { toValue: 1, duration: 200, useNativeDriver: true }).start(() => {
      setTimeout(() => Animated.timing(fade, { toValue: 0, duration: 200, useNativeDriver: true }).start(), 2000);
    });
  };

  const copy = async (text, label) => {
    await Clipboard.setStringAsync(text);
    showToast(`${label} copied`);
  };

  // ---- auth actions ----
  const doSignup = () => {
    if (!name || !phone || !password) return Alert.alert('Fill name, phone and password');
    if (!consentGiven) return Alert.alert('Consent required', 'Please agree to the Privacy Notice.');
    setNewCareId(makeCareId());
  };

  const loginMock = () => {
    setPatient(MOCK_PATIENT);
    setPrescriptions(MOCK_PRESCRIPTIONS);
    setHomePrescriptions(MOCK_PRESCRIPTIONS);
    setMedications(null);
    setProfileLive(false);
    setSchedule(MOCK_TODAY_SCHEDULE);
    setScheduleLive(false);
    setPassword('');
    setScreen(MOCK_PATIENT.profileCompleted ? 'app' : 'profile');
    setTab('home');
  };

  // Token missing, expired or rejected: back to the login screen.
  const expireSession = () => {
    logout();
    Alert.alert('Session expired', 'Please log in again.');
  };

  // Home + Today data. Each call falls back to its mock on its own; an auth
  // error is rethrown so the caller can end the session instead.
  // Prescriptions stay mock: GET /patient/me/prescriptions/detailed is not built
  // yet, so it is not called at all (no request, no retry, no offline toast).
  const loadHome = async (user) => {
    let fellBack = false;
    const orMock = async (call, mock) => {
      try {
        return await call();
      } catch (e) {
        if (api.isAuthError(e)) throw e;
        fellBack = true;
        return mock;
      }
    };
    const [me, meds, doses] = await Promise.all([
      orMock(() => api.getMe(user), null),
      orMock(() => api.getMedications(), null),
      orMock(() => api.getTodayDoses(), null),
    ]);
    setPatient(me || MOCK_PATIENT);
    setProfileLive(!!me);
    setHomePrescriptions(MOCK_PRESCRIPTIONS);
    setMedications(meds);
    setSchedule(doses || MOCK_TODAY_SCHEDULE);
    setScheduleLive(!!doses);
    setPrescriptions(MOCK_PRESCRIPTIONS); // History stays mock
    if (fellBack) showToast('Using offline demo data');
  };

  const LOGIN_MESSAGES = {
    INVALID_CREDENTIALS: 'Wrong CareID/phone or password.',
    ACCOUNT_PENDING: 'Your account is waiting for approval.',
    ACCOUNT_REJECTED: 'Your account was not approved. Contact your facility.',
  };

  const doLogin = async () => {
    if (!phone || !password) return Alert.alert('Enter your CareID or phone and password');
    if (loggingIn) return;
    if (MOCK_MODE) return loginMock();

    setLoggingIn(true);
    try {
      let user;
      try {
        user = await api.login(phone.trim(), password);
      } catch (e) {
        if (e instanceof api.ApiError && e.kind === 'http' && e.status >= 400 && e.status < 500) {
          // A real answer from the server: show it, never fall back to the demo account.
          return Alert.alert('Login failed', LOGIN_MESSAGES[e.code] || `The server refused the login (${e.status}).`);
        }
        // Network error, timeout, 5xx or unexpected response.
        loginMock();
        return showToast('Using offline demo data');
      }

      if (user.role !== 'PATIENT') {
        api.clearSession();
        return Alert.alert('Patients only', 'This app is for patients. Please use the staff site.');
      }

      setPassword('');
      try {
        await loadHome(user);
      } catch (e) {
        return expireSession(); // loadHome only rethrows auth errors
      }
      setScreen(user.profileCompleted ? 'app' : 'profile');
      setTab('home');
    } finally {
      setLoggingIn(false);
    }
  };

  const sendResetOtp = () => {
    if (!phone) return Alert.alert('Enter your phone number first');
    const otp = String(Math.floor(100000 + Math.random() * 900000));
    setSentOtp(otp);
    Alert.alert('OTP sent (demo)', `Your reset OTP is: ${otp}`);
  };

  const doReset = () => {
    if (!resetOtp || !newPassword) return Alert.alert('Enter the OTP and a new password');
    if (resetOtp !== sentOtp) return Alert.alert('Wrong OTP');
    Alert.alert('Success', 'Password reset. Please log in.');
    setResetOtp(''); setSentOtp(''); setNewPassword(''); setScreen('login');
  };

  const logout = () => {
    api.clearSession();
    setPatient(null); setPrescriptions([]); setHomePrescriptions([]); setMedications(null);
    setSchedule([]); setScheduleLive(false); setProfileLive(false); setFamily(MOCK_FAMILY);
    setTab('home'); setShowActive(false); setVisitExpanded({}); setScreen('login');
  };

  const toggleTaken = async (id) => {
    const dose = schedule.find((d) => d.id === id);
    if (!dose) return;

    // Mock / offline demo data: local toggle, as before.
    if (!scheduleLive) {
      return setSchedule((prev) => prev.map((d) => (d.id === id ? { ...d, taken: !d.taken } : d)));
    }

    // Live: there is no "un-take" endpoint, so a taken dose stays taken.
    if (dose.taken || dose.pending) return;

    const patch = (fields) => setSchedule((prev) => prev.map((d) => (d.id === id ? { ...d, ...fields } : d)));
    patch({ taken: true, pending: true }); // optimistic
    try {
      const res = await api.markDoseTaken(id);
      patch({ taken: true, pending: false, status: 'TAKEN', late: !!(res && res.late) }); // confirmed
    } catch (e) {
      if (e instanceof api.ApiError && e.status === 409 && e.code === 'DOSE_ALREADY_TAKEN') {
        return patch({ taken: true, pending: false, status: 'TAKEN' }); // already recorded: same end state
      }
      setSchedule((prev) => prev.map((d) => (d.id === id ? dose : d))); // revert
      if (api.isAuthError(e)) return expireSession();
      showToast("Couldn't mark taken. Try again.");
    }
  };

  // Profile form: allergies go to PUT /patient/me when logged in live; if that fails the edit is
  // kept locally and a toast says so. Everything else on the form is local.
  const onProfileSave = async (data) => {
    if (savingProfile) return;
    setSavingProfile(true);
    const result = await persistProfile({ patient, data, live: profileLive && api.hasSession() });
    setSavingProfile(false);
    setPatient(result.patient);
    setScreen('app');
    setTab('home');
    if (result.error) showToast('Saved on this device only (server unavailable)');
  };

  const toggleVisit = (key) => setVisitExpanded((v) => ({ ...v, [key]: !v[key] }));

  const badgeStyleFor = (cat) => ({
    Access: styles.badgeAccess, Watch: styles.badgeWatch,
    Reserve: styles.badgeReserve, 'Not Recommended': styles.badgeNotRecommended,
  }[cat] || null);

  // ---- overlays ----
  if (overlay === 'privacy') return <PrivacyNotice onBack={() => setOverlay(null)} />;

  if (screen === 'profile') {
    return (
      <ProfileCompletion
        existingProfile={patient?.profileCompleted ? patient : null}
        initialAllergies={patient?.allergies}
        saving={savingProfile}
        onDone={onProfileSave}
        onCancel={patient ? () => { setScreen('app'); setTab('settings'); } : null}
      />
    );
  }

    // ---- SIGNUP (disabled for demo build) ----
  if (screen === 'signup') {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar barStyle="dark-content" />
        <View style={styles.authBody}>
          <Text style={styles.brand}>Curo</Text>
          <Text style={styles.subtitle}>Demo build: sign in with the demo account</Text>
          <TouchableOpacity style={styles.button} onPress={() => setScreen('login')}>
            <Text style={styles.buttonText}>Back to Login</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // ---- FORGOT ----
  if (screen === 'forgot') {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar barStyle="dark-content" />
        <ScrollView contentContainerStyle={styles.authBody} keyboardShouldPersistTaps="handled">
          <Text style={styles.brand}>Curo</Text>
          <Text style={styles.subtitle}>Reset your password</Text>
          <TextInput style={styles.input} placeholder="Phone number" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
          <TouchableOpacity style={styles.buttonAlt} onPress={sendResetOtp}>
            <Text style={styles.buttonText}>Send OTP</Text>
          </TouchableOpacity>
          {sentOtp ? <Text style={styles.hint}>Demo OTP: {sentOtp}</Text> : null}
          <TextInput style={styles.input} placeholder="Enter OTP" value={resetOtp} onChangeText={setResetOtp} keyboardType="number-pad" />
          <TextInput style={styles.input} placeholder="New password" value={newPassword} onChangeText={setNewPassword} secureTextEntry />
          <TouchableOpacity style={styles.button} onPress={doReset}>
            <Text style={styles.buttonText}>Reset Password</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setScreen('login')}>
            <Text style={styles.link}>Back to Log in</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // ---- LOGIN ----
  if (screen === 'login') {
    return (
      <SafeAreaView style={styles.screen}>
        <StatusBar barStyle="dark-content" />
        <View style={styles.authBody}>
          <Text style={styles.brand}>Curo</Text>
          <Text style={styles.subtitle}>Log in to your medicine record</Text>
          <TextInput style={styles.input} placeholder="CareID or phone number" value={phone} onChangeText={setPhone} autoCapitalize="characters" autoCorrect={false} />
          <TextInput style={styles.input} placeholder="Password" value={password} onChangeText={setPassword} secureTextEntry />
          <TouchableOpacity style={styles.button} onPress={doLogin} disabled={loggingIn}>
            <Text style={styles.buttonText}>{loggingIn ? 'Logging in…' : 'Log In'}</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => setScreen('forgot')}>
            <Text style={styles.forgot}>Forgot password?</Text>
          </TouchableOpacity>
          <Text style={styles.demoNote}>Demo build: sign in with the demo account</Text>
        </View>
      </SafeAreaView>
    );
  }

  // ---- MAIN APP ----
  const q = search.toLowerCase();
  const filtered = prescriptions.filter((p) => {
    const match = p.drugName?.toLowerCase().includes(q) || p.doctorName?.toLowerCase().includes(q) || p.hospitalName?.toLowerCase().includes(q);
    return match && (!antibioticOnly || p.isAntibiotic);
  });
  const active = homePrescriptions.filter((p) => !p.status?.toLowerCase().includes('dispensed'));
  // Interaction and allergy checks: computed from the real medicine list and allergies
  // when they loaded from the server; otherwise the mock demo data, as before.
  const liveMeds = medications !== null;
  const interactions = liveMeds ? findInteractions(medications.map((m) => m.drug)) : MOCK_INTERACTIONS;
  const conflicts = liveMeds && profileLive
    ? findAllergyConflicts(medications.map((m) => m.drug), patient.allergies)
    : active.filter((p) => p.allergyConflict).map((p) => ({ drug: p.drugName, allergy: null }));
  const conflictDrugs = [...new Set(conflicts.map((c) => c.drug))];
  const activeVisits = groupByVisit(active);
  const historyVisits = groupByVisit(filtered);
  const takenCount = schedule.filter((d) => d.taken).length;

  const title = { home: 'Curo', today: "Today's Schedule", qr: 'My QR', book: 'Book Appointment', family: 'Family', settings: 'Settings', share: 'Share My Record' }[tab];

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.headerRow}>
        <Text style={styles.header}>{title}</Text>
        <TouchableOpacity onPress={() => setTab('settings')}><Text style={styles.gear}>⚙️</Text></TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* HOME */}
        {tab === 'home' && (
          <>
            <View style={styles.card}>
              <Text style={styles.cardLabel}>CareID</Text>
              <TouchableOpacity onPress={() => copy(patient.careId, 'CareID')} activeOpacity={0.6}>
                <Text style={styles.careIdInline}>{patient.careId}  📋</Text>
              </TouchableOpacity>
              <View style={styles.divider} />
              <Text style={styles.cardLabel}>Name</Text>
              <Text style={styles.cardValue}>{patient.name}</Text>
              <View style={styles.divider} />
              <Text style={styles.cardLabel}>Phone</Text>
              <Text style={styles.cardValue}>{patient.phone}</Text>
            </View>

            {patient.allergies && patient.allergies.length > 0 ? (
              <View style={styles.allergyBanner}>
                <Text style={styles.allergyText}>⚠️ Allergy on record: {patient.allergies.join(', ')}. Doctors are alerted before prescribing.</Text>
              </View>
            ) : null}

            {/* Drug-drug interaction warnings */}
            {interactions.map((ix) => (
              <View key={ix.id} style={[styles.interactionCard, { borderLeftColor: SEVERITY_COLORS[ix.severity] }]}>
                <Text style={styles.interactionTitle}>🚨 {ix.drugA} + {ix.drugB} — {ix.warning}</Text>
                <Text style={styles.interactionText}>{ix.explanation}</Text>
                <Text style={[styles.severityLabel, { color: SEVERITY_COLORS[ix.severity] }]}>{ix.severity.toUpperCase()} INTERACTION</Text>
              </View>
            ))}

            {conflicts.length > 0 ? (
              <View style={styles.conflictBanner}>
                <Text style={styles.conflictTitle}>🚨 Allergy Conflict</Text>
                <Text style={styles.conflictText}>
                  {conflictDrugs.length === 1 ? `${conflictDrugs[0]} conflicts with your recorded allergies${conflicts[0].allergy ? ` (${conflicts.map((c) => c.allergy).join(', ')})` : ''}.` : `${conflictDrugs.length} active medicines conflict with your allergies.`} Contact your doctor before taking.
                </Text>
              </View>
            ) : null}

            {/* Current medicines (live only) */}
            {medications && medications.length > 0 ? (
              <>
                <Text style={styles.sectionTitle}>Current medicines</Text>
                {medications.map((m) => (
                  <View key={m.id} style={styles.card}>
                    <Text style={styles.drugName}>{m.drug}</Text>
                    <Text style={styles.cardValue}>{m.dose}{m.times.length > 0 ? ` · ${m.times.join(', ')}` : ''}</Text>
                    {m.endDate ? <Text style={styles.cardValueFaint}>Until {m.endDate}</Text> : null}
                  </View>
                ))}
              </>
            ) : null}

            {active.length === 0 ? (
              <View style={styles.empty}>
                <Text style={styles.emptyIcon}>📋</Text>
                <Text style={styles.emptyTitle}>No active prescriptions</Text>
              </View>
            ) : (
              <>
                <TouchableOpacity style={styles.bigButton} onPress={() => setShowActive((v) => !v)}>
                  <Text style={styles.bigButtonText}>{showActive ? '▲  Hide Active Prescriptions' : `📋  Show Active Prescriptions (${active.length})`}</Text>
                </TouchableOpacity>
                {showActive && activeVisits.map(([key, visit]) => (
                  <VisitCard key={key} visit={visit} open={visitExpanded[key]} onToggle={() => toggleVisit(key)} badgeStyleFor={badgeStyleFor} showFlags />
                ))}
              </>
            )}
          </>
        )}

        {/* TODAY */}
        {tab === 'today' && (
          <>
            <Text style={styles.progress}>{takenCount} of {schedule.length} doses taken</Text>
            {schedule.map((d) => (
              <View key={d.id} style={[styles.card, d.taken && styles.cardDone]}>
                <View style={styles.doseRow}>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.drugName, d.taken && styles.doneText]}>{d.drugName}</Text>
                    <Text style={styles.cardValue}>{d.dosage} · {d.time}</Text>
                  </View>
                  <TouchableOpacity style={[styles.markBtn, d.taken && styles.markBtnDone]} onPress={() => toggleTaken(d.id)} disabled={scheduleLive && d.taken}>
                    <Text style={[styles.markBtnText, d.taken && styles.markBtnTextDone]}>{d.taken ? '✓ Taken' : 'Mark taken'}</Text>
                  </TouchableOpacity>
                </View>
              </View>
            ))}
          </>
        )}

        {/* HISTORY lives in settings menu button; keep a History view reachable from Home's search?
            We surface History under its own in-app view via the search field here on Home-less tabs. */}

        {/* QR */}
        {tab === 'qr' && <QrTab careId={patient.careId} onRefresh={() => showToast('QR refreshed')} refreshing={false} />}

        {/* BOOK */}
        {tab === 'book' && <BookTab />}

        {/* FAMILY */}
        {tab === 'family' && <FamilyTab members={family} onChange={setFamily} />}

        {/* SETTINGS */}
        {tab === 'settings' && !overlay && (
          <>
            <View style={styles.card}>
              <Text style={styles.cardLabel}>Logged in as</Text>
              <Text style={styles.cardValue}>{patient.name}</Text>
              <Text style={styles.cardValueFaint}>{patient.phone} · {patient.careId}</Text>
            </View>
            <SettingsItem text="✏️  Edit Personal Details" onPress={() => setScreen('profile')} />
            <SettingsItem text="🧾  Prescription History" onPress={() => setTab('history')} />
            <SettingsItem text="👁️  Who Viewed My Record" onPress={() => setTab('accesslog')} />
            <SettingsItem text="🔗  Share my record" onPress={() => setTab('share')} />
            <SettingsItem text="🛡️  Access Requests" onPress={() => setTab('consent')} />
            <SettingsItem text="🔒  Privacy Notice" onPress={() => setOverlay('privacy')} />
            <SettingsItem text="ℹ️  About Curo" onPress={() => Alert.alert('Curo', 'Version 1.0 (demo)')} />
            <TouchableOpacity style={styles.logoutButton} onPress={logout}><Text style={styles.buttonText}>Log Out</Text></TouchableOpacity>
            <TouchableOpacity style={styles.deleteButton} onPress={() => Alert.alert('Delete Account', 'This permanently deletes your account.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Delete', style: 'destructive', onPress: logout }])}>
              <Text style={styles.deleteText}>🗑️  Delete Account</Text>
            </TouchableOpacity>
          </>
        )}

        {/* SHARE MY RECORD (reached from Settings) */}
        {tab === 'share' && <ShareRecord onBack={() => setTab('settings')} onCopied={showToast} />}

        {/* ACCESS REQUESTS (reached from Settings) */}
        {tab === 'consent' && <ConsentApproval onBack={() => setTab('settings')} />}

        {/* HISTORY (reached from Settings) */}
        {tab === 'history' && (
          <>
            <TouchableOpacity onPress={() => setTab('settings')}><Text style={styles.link}>‹ Back to Settings</Text></TouchableOpacity>
            <TextInput style={[styles.input, { marginTop: 12 }]} placeholder="🔍 Search by drug, doctor, clinic" value={search} onChangeText={setSearch} />
            <TouchableOpacity style={[styles.filterToggle, antibioticOnly && styles.filterToggleOn]} onPress={() => setAntibioticOnly((v) => !v)}>
              <Text style={[styles.filterText, antibioticOnly && styles.filterTextOn]}>{antibioticOnly ? '✓ Antibiotics only' : '💊 Antibiotics only'}</Text>
            </TouchableOpacity>
            {historyVisits.length === 0 ? (
              <View style={styles.empty}><Text style={styles.emptyTitle}>No history found</Text></View>
            ) : historyVisits.map(([key, visit]) => (
              <VisitCard key={key} visit={visit} open={visitExpanded[key]} onToggle={() => toggleVisit(key)} badgeStyleFor={badgeStyleFor} />
            ))}
          </>
        )}

        {/* ACCESS LOG (reached from Settings) */}
        {tab === 'accesslog' && (
          <>
            <TouchableOpacity onPress={() => setTab('settings')}><Text style={styles.link}>‹ Back to Settings</Text></TouchableOpacity>
            <Text style={styles.sectionTitle}>Who viewed your record</Text>
            {MOCK_ACCESS_LOG.map((a) => (
              <View key={a.id} style={styles.card}>
                <View style={styles.whoRow}>
                  <Text style={styles.drugName}>{a.who}</Text>
                  {a.role === 'Guest' ? <View style={styles.guestBadge}><Text style={styles.badgeText}>Guest</Text></View> : null}
                </View>
                <Text style={styles.cardValue}>{a.role} · {a.org}</Text>
                <Text style={styles.cardValueFaint}>{a.action} · {formatVisitDate(a.at)}</Text>
              </View>
            ))}
          </>
        )}
      </ScrollView>

      {['home', 'today', 'qr', 'book', 'family'].includes(tab) && (
        <BottomNav activeTab={tab} onTabPress={setTab} />
      )}

      <Animated.View style={[styles.toast, { opacity: fade }]} pointerEvents="none">
        <Text style={styles.toastText}>{toast}</Text>
      </Animated.View>
    </View>
  );
}

// Visit card used by Home (active) and History
function VisitCard({ visit, open, onToggle, badgeStyleFor, showFlags }) {
  return (
    <View style={styles.visitCard}>
      <TouchableOpacity onPress={onToggle} activeOpacity={0.75}>
        <Text style={styles.visitDoctor}>{visit.doctorName || 'Unknown Doctor'}</Text>
        <Text style={styles.visitHospital}>{visit.hospitalName || 'Unknown Hospital'}</Text>
        <Text style={styles.visitDate}>{formatVisitDate(visit.createdAt)}</Text>
      </TouchableOpacity>
      {open && (
        <View style={styles.visitDrugs}>
          {visit.drugs.map((p) => {
            const tag = getCourseTag(p);
            return (
              <View key={p.id} style={styles.drugRow}>
                <View style={styles.rxHeader}>
                  <Text style={styles.visitDrugName}>{p.drugName}</Text>
                  {p.awareCategory ? (
                    <View style={[styles.badge, badgeStyleFor(p.awareCategory)]}><Text style={styles.badgeText}>{p.awareCategory}</Text></View>
                  ) : null}
                </View>
                <Text style={styles.cardValue}>{p.dosage} · {p.durationDays} days</Text>
                <View style={[styles.courseTag, styles[tag.style]]}><Text style={styles.courseTagText}>{tag.label}</Text></View>
                {showFlags && p.allergyConflict ? <Text style={styles.flag}>⚠️ Conflicts with your allergies</Text> : null}
              </View>
            );
          })}
        </View>
      )}
    </View>
  );
}

function SettingsItem({ text, onPress }) {
  return (
    <TouchableOpacity style={styles.settingsItem} onPress={onPress}>
      <Text style={styles.settingsItemText}>{text}</Text>
    </TouchableOpacity>
  );
}

export default function App() {
  return (
    <SafeAreaProvider>
      <AppInner />
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f0f7f7' },

  authBody: { flexGrow: 1, justifyContent: 'center', padding: 24 },
  brand: { fontSize: 32, fontWeight: 'bold', color: TEAL, textAlign: 'center', marginBottom: 4 },
  subtitle: { fontSize: 15, color: '#555', textAlign: 'center', marginBottom: 24 },
  input: { backgroundColor: '#fff', borderRadius: 10, padding: 14, marginBottom: 12, borderWidth: 1, borderColor: '#cde' },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 4 },
  buttonAlt: { backgroundColor: '#14a1a6', borderRadius: 10, padding: 15, alignItems: 'center', marginBottom: 12 },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  link: { color: TEAL, textAlign: 'center', marginTop: 16, fontWeight: '600' },
  forgot: { color: '#14a1a6', textAlign: 'center', marginTop: 14, fontWeight: '600' },
  demoNote: { color: '#8a9a9a', textAlign: 'center', marginTop: 16, fontSize: 13, fontStyle: 'italic' },
  hint: { color: '#c00', textAlign: 'center', marginBottom: 8, fontWeight: '600' },

  consentRow: { flexDirection: 'row', alignItems: 'flex-start', marginVertical: 12 },
  checkbox: { width: 22, height: 22, borderRadius: 5, borderWidth: 2, borderColor: TEAL, marginRight: 10, marginTop: 1, alignItems: 'center', justifyContent: 'center' },
  checkboxOn: { backgroundColor: TEAL },
  tick: { color: '#fff', fontSize: 14, fontWeight: 'bold' },
  consentText: { flex: 1, fontSize: 13, color: '#333', lineHeight: 19 },
  consentLink: { color: TEAL, fontWeight: '700', textDecorationLine: 'underline' },

  careIdCard: { backgroundColor: '#fff', borderRadius: 16, padding: 24, alignItems: 'center', marginBottom: 24, borderWidth: 2, borderColor: TEAL },
  careIdLabel: { fontSize: 12, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 1 },
  careIdValue: { fontSize: 26, fontWeight: 'bold', color: TEAL, marginVertical: 8, letterSpacing: 1 },
  careIdNote: { fontSize: 13, color: '#667', textAlign: 'center', lineHeight: 19 },

  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingTop: 12, paddingBottom: 8 },
  header: { fontSize: 24, fontWeight: 'bold', color: TEAL },
  gear: { fontSize: 20 },
  content: { padding: 16, paddingBottom: 24 },

  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, elevation: 2 },
  cardDone: { backgroundColor: '#eef7f0' },
  cardLabel: { fontSize: 11, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 0.5 },
  cardValue: { fontSize: 15, color: '#445', marginTop: 4 },
  cardValueFaint: { fontSize: 13, color: '#8a9a9a', marginTop: 2 },
  careIdInline: { fontSize: 18, fontWeight: '700', color: TEAL, marginTop: 2, letterSpacing: 1 },
  divider: { height: 1, backgroundColor: '#eef2f2', marginVertical: 10 },
  drugName: { fontSize: 16, fontWeight: '700', color: '#1a3c3d' },

  allergyBanner: { backgroundColor: '#fff3f3', borderRadius: 10, padding: 12, marginBottom: 12, borderWidth: 1, borderColor: '#f0c0c0' },
  allergyText: { color: '#c0392b', fontSize: 13, fontWeight: '600' },

  interactionCard: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, borderLeftWidth: 5, elevation: 2 },
  interactionTitle: { fontSize: 15, fontWeight: '700', color: '#1a3c3d', marginBottom: 6 },
  interactionText: { fontSize: 13, color: '#445', lineHeight: 19 },
  severityLabel: { fontSize: 11, fontWeight: '800', letterSpacing: 0.5, marginTop: 8 },

  conflictBanner: { backgroundColor: '#7d1128', borderRadius: 12, padding: 16, marginBottom: 12 },
  conflictTitle: { color: '#fff', fontSize: 15, fontWeight: '800', marginBottom: 6 },
  conflictText: { color: '#ffe0e0', fontSize: 13, fontWeight: '600', lineHeight: 18 },

  empty: { alignItems: 'center', padding: 30 },
  emptyIcon: { fontSize: 40, marginBottom: 10 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: '#333' },

  bigButton: { backgroundColor: TEAL, borderRadius: 12, padding: 16, alignItems: 'center', marginBottom: 8 },
  bigButtonText: { color: '#fff', fontWeight: '700', fontSize: 15 },

  progress: { fontSize: 14, fontWeight: '600', color: '#5a6a6a', marginBottom: 12 },
  doseRow: { flexDirection: 'row', alignItems: 'center' },
  doneText: { textDecorationLine: 'line-through', color: '#8a9a9a' },
  markBtn: { backgroundColor: TEAL, borderRadius: 8, paddingHorizontal: 14, paddingVertical: 10 },
  markBtnDone: { backgroundColor: '#cfe8d6' },
  markBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  markBtnTextDone: { color: '#2e7d4f' },

  sectionTitle: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginVertical: 12, textTransform: 'uppercase', letterSpacing: 0.5 },

  filterToggle: { backgroundColor: '#fff', borderRadius: 10, padding: 12, alignItems: 'center', marginBottom: 12, borderWidth: 1, borderColor: '#cde' },
  filterToggleOn: { backgroundColor: TEAL, borderColor: TEAL },
  filterText: { color: TEAL, fontWeight: '700', fontSize: 13 },
  filterTextOn: { color: '#fff' },

  visitCard: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginBottom: 10, elevation: 1 },
  visitDoctor: { fontSize: 16, fontWeight: '700', color: TEAL },
  visitHospital: { fontSize: 13, color: '#666', marginTop: 2 },
  visitDate: { fontSize: 12, color: '#8a9a9a', marginTop: 4 },
  visitDrugs: { marginTop: 12, borderTopWidth: 1, borderTopColor: '#eef2f2', paddingTop: 10 },
  drugRow: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#f5f8f8' },
  rxHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  visitDrugName: { fontSize: 14, fontWeight: '600', color: TEAL, flexShrink: 1, marginRight: 8 },
  flag: { color: '#c0392b', fontSize: 12, fontWeight: '700', marginTop: 8 },

  courseTag: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, marginTop: 6 },
  courseTagText: { fontSize: 11, fontWeight: '700' },
  tagIssued: { backgroundColor: '#eef2f2' },
  tagActive: { backgroundColor: '#e3f5ea' },
  tagFinished: { backgroundColor: '#eef0f5' },

  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12 },
  badgeText: { fontSize: 11, fontWeight: '700', color: '#fff' },
  whoRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  guestBadge: { backgroundColor: '#6b4fa0', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12 },
  badgeAccess: { backgroundColor: '#2e9e5b' },
  badgeWatch: { backgroundColor: '#e08e0b' },
  badgeReserve: { backgroundColor: '#c0392b' },
  badgeNotRecommended: { backgroundColor: '#7d1128' },

  settingsItem: { backgroundColor: '#fff', borderRadius: 12, padding: 16, marginTop: 10 },
  settingsItemText: { fontSize: 15, color: '#333', fontWeight: '600' },
  logoutButton: { backgroundColor: '#8a4b4b', borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 24 },
  deleteButton: { backgroundColor: '#fff3f3', borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 12, borderWidth: 1, borderColor: '#f0c0c0' },
  deleteText: { color: '#c0392b', fontWeight: '700', fontSize: 15 },

  toast: { position: 'absolute', bottom: 90, alignSelf: 'center', backgroundColor: '#333', paddingVertical: 12, paddingHorizontal: 20, borderRadius: 25, elevation: 5 },
  toastText: { color: '#fff', fontSize: 14, fontWeight: '600' },
});