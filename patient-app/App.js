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
import { toActivePrescriptions, formatDay } from './groupMeds';
import { LinearGradient } from 'expo-linear-gradient';
import { colors, type, card as cardStyle, shadow } from './theme';
import {
  MOCK_PATIENT, MOCK_PRESCRIPTIONS, MOCK_TODAY_SCHEDULE,
  MOCK_INTERACTIONS, MOCK_ACCESS_LOG, MOCK_FAMILY,
} from './mocks/data';

const TEAL = '#0d7377';
const ROLE_ICON = { Doctor: '🩺', Pharmacy: '💊', Guest: '👤' }; // access-log row icons
const SEVERITY_UI = {
  major: { fg: colors.danger, bg: colors.dangerSoft },
  moderate: { fg: colors.accent, bg: colors.accentSoft },
  minor: { fg: colors.textMuted, bg: '#F1F5F9' },
};

// ---- helpers --------------------------------------------------------------
function groupByVisit(prescriptions) {
  const groups = {};
  prescriptions.forEach((p) => {
    const key = p.visitId || p.id;
    if (!groups[key]) {
      groups[key] = { doctorName: p.doctorName, hospitalName: p.hospitalName, createdAt: p.createdAt, issueDay: p.issueDay || null, drugs: [] };
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
      <SafeAreaView style={styles.authScreen}>
        <StatusBar barStyle="dark-content" />
        <View style={styles.authBody}>
          <View style={styles.authLogo}><Text style={styles.authLogoText}>C</Text></View>
          <Text style={styles.authBrand}>Curo</Text>
          <Text style={styles.authSubtitle}>Log in to your medicine record</Text>
          <View style={styles.authCard}>
            <Text style={styles.authLabel}>CareID or phone</Text>
            <TextInput style={styles.authInput} placeholder="CareID or phone number" placeholderTextColor={colors.placeholder} value={phone} onChangeText={setPhone} autoCapitalize="characters" autoCorrect={false} />
            <Text style={styles.authLabel}>Password</Text>
            <TextInput style={styles.authInput} placeholder="Password" placeholderTextColor={colors.placeholder} value={password} onChangeText={setPassword} secureTextEntry />
            <TouchableOpacity style={[styles.authButton, loggingIn && styles.authButtonBusy]} onPress={doLogin} disabled={loggingIn} activeOpacity={0.85}>
              <Text style={styles.authButtonText}>{loggingIn ? 'Logging in…' : 'Log In'}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => setScreen('forgot')}>
              <Text style={styles.authLink}>Forgot password?</Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.authNote}>ℹ️  Demo build: sign in with the demo account</Text>
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
  const mockActive = homePrescriptions.filter((p) => !p.status?.toLowerCase().includes('dispensed'));
  // Interaction and allergy checks: computed from the real medicine list and allergies
  // when they loaded from the server; otherwise the mock demo data, as before.
  const liveMeds = medications !== null;
  const interactions = liveMeds ? findInteractions(medications.map((m) => m.drug)) : MOCK_INTERACTIONS;
  const conflicts = liveMeds && profileLive
    ? findAllergyConflicts(medications.map((m) => m.drug), patient.allergies)
    : mockActive.filter((p) => p.allergyConflict).map((p) => ({ drug: p.drugName, allergy: null }));
  const conflictDrugs = [...new Set(conflicts.map((c) => c.drug))];
  // Active Prescriptions: the live medicine list when it loaded, otherwise the mock demo list.
  const active = liveMeds ? toActivePrescriptions(medications, conflictDrugs) : mockActive;
  const activeVisits = groupByVisit(active);
  const historyVisits = groupByVisit(filtered);
  const takenCount = schedule.filter((d) => d.taken).length;

  const title = { home: 'Curo', today: "Today's Schedule", qr: 'My QR', book: 'Book Appointment', family: 'Family', settings: 'Settings', share: 'Share My Record' }[tab];

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.headerRow}>
        <Text style={styles.header}>{title}</Text>
        <TouchableOpacity style={styles.gearBtn} onPress={() => setTab('settings')}><Text style={styles.gear}>⚙️</Text></TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* HOME */}
        {tab === 'home' && (
          <>
            <LinearGradient colors={[colors.primary, colors.primaryDark]} start={{ x: 0, y: 0 }} end={{ x: 1, y: 1 }} style={styles.idCard}>
              <Text style={styles.idBrand}>CURO HEALTH ID</Text>
              <Text style={styles.idLabel}>CareID</Text>
              <TouchableOpacity onPress={() => copy(patient.careId, 'CareID')} activeOpacity={0.6}>
                <Text style={styles.idCareId}>{patient.careId}  📋</Text>
              </TouchableOpacity>
              <View style={styles.idRow}>
                <View style={{ flex: 1, marginRight: 12 }}>
                  <Text style={styles.idLabel}>Name</Text>
                  <Text style={styles.idValue}>{patient.name}</Text>
                </View>
                <View>
                  <Text style={styles.idLabel}>Phone</Text>
                  <Text style={styles.idValue}>{patient.phone}</Text>
                </View>
              </View>
            </LinearGradient>

            {patient.allergies && patient.allergies.length > 0 ? (
              <View style={styles.warnCard}>
                <View style={styles.warnHead}><Text style={styles.warnIcon}>⚠️</Text><Text style={styles.warnTitle}>Allergy on record</Text></View>
                <Text style={styles.warnText}>{patient.allergies.join(', ')}. Doctors are alerted before prescribing.</Text>
              </View>
            ) : null}

            {/* Drug-drug interaction warnings */}
            {interactions.map((ix) => (
              <View key={ix.id} style={styles.warnCard}>
                <View style={styles.warnHead}><Text style={styles.warnIcon}>🚨</Text><Text style={styles.warnTitle}>{ix.drugA} + {ix.drugB}</Text></View>
                <Text style={styles.warnHeadline}>{ix.warning}</Text>
                <Text style={styles.warnText}>{ix.explanation}</Text>
                <View style={[styles.sevPill, { backgroundColor: (SEVERITY_UI[ix.severity] || SEVERITY_UI.minor).bg }]}>
                  <Text style={[styles.sevText, { color: (SEVERITY_UI[ix.severity] || SEVERITY_UI.minor).fg }]}>{ix.severity.toUpperCase()} INTERACTION</Text>
                </View>
              </View>
            ))}

            {conflicts.length > 0 ? (
              <View style={styles.warnCard}>
                <View style={styles.warnHead}><Text style={styles.warnIcon}>🚨</Text><Text style={styles.warnTitle}>Allergy conflict</Text></View>
                <Text style={styles.warnText}>
                  {conflictDrugs.length === 1 ? `${conflictDrugs[0]} conflicts with your recorded allergies${conflicts[0].allergy ? ` (${conflicts.map((c) => c.allergy).join(', ')})` : ''}.` : `${conflictDrugs.length} active medicines conflict with your allergies.`} Contact your doctor before taking.
                </Text>
              </View>
            ) : null}

            {active.length === 0 ? (
              <View style={styles.empty}>
                <Text style={styles.emptyIcon}>📋</Text>
                <Text style={styles.emptyTitle}>No active prescriptions</Text>
                <Text style={styles.emptySub}>Nothing to take right now.</Text>
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
            {schedule.length === 0 ? (
              <View style={styles.empty}>
                <Text style={styles.emptyIcon}>💊</Text>
                <Text style={styles.emptyTitle}>No doses scheduled today</Text>
                <Text style={styles.emptySub}>Doses from your prescriptions will appear here.</Text>
              </View>
            ) : (
              <>
                <View style={styles.progressCard}>
                  <View style={styles.progressTop}>
                    <Text style={styles.progressLabel}>Today's progress</Text>
                    <Text style={styles.progress}>{takenCount} of {schedule.length} doses taken</Text>
                  </View>
                  <View style={styles.progressTrack}>
                    <View style={[styles.progressFill, { width: Math.round((takenCount / schedule.length) * 100) + '%' }]} />
                  </View>
                </View>
                {schedule.map((d) => (
                  <View key={d.id} style={[styles.card, d.taken && styles.cardDone]}>
                    <View style={styles.doseRow}>
                      <View style={[styles.timePill, d.taken && styles.timePillDone]}>
                        <Text style={[styles.timeText, d.taken && styles.timeTextDone]}>{d.time}</Text>
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.drugName, d.taken && styles.doneText]}>{d.drugName}</Text>
                        <Text style={styles.cardValueFaint}>{d.dosage}</Text>
                      </View>
                    </View>
                    <TouchableOpacity style={[styles.markBtn, d.taken && styles.markBtnDone, d.pending && styles.markBtnPending]} onPress={() => toggleTaken(d.id)} disabled={scheduleLive && d.taken} activeOpacity={0.8}>
                      <Text style={[styles.markBtnText, d.taken && styles.markBtnTextDone]}>{d.taken ? '✓ Taken' : 'Mark taken'}</Text>
                    </TouchableOpacity>
                  </View>
                ))}
              </>
            )}
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
            <TouchableOpacity onPress={() => setTab('home')}><Text style={styles.backLink}>‹ Back to Home</Text></TouchableOpacity>
            <View style={[styles.card, styles.profileRow]}>
              <View style={styles.settingsAvatar}><Text style={styles.settingsAvatarText}>{(patient.name || '?').charAt(0).toUpperCase()}</Text></View>
              <View style={{ flex: 1 }}>
                <Text style={styles.cardLabel}>Logged in as</Text>
                <Text style={styles.cardValue}>{patient.name}</Text>
                <Text style={styles.cardValueFaint}>{patient.phone} · {patient.careId}</Text>
              </View>
            </View>
            <Text style={styles.sectionTitle}>Account</Text>
            <SettingsItem text="✏️  Edit Personal Details" onPress={() => setScreen('profile')} />
            <Text style={styles.sectionTitle}>Your record</Text>
            <SettingsItem text="🧾  Prescription History" onPress={() => setTab('history')} />
            <SettingsItem text="👁️  Who Viewed My Record" onPress={() => setTab('accesslog')} />
            <SettingsItem text="🔗  Share my record" onPress={() => setTab('share')} />
            <SettingsItem text="🛡️  Access Requests" onPress={() => setTab('consent')} />
            <Text style={styles.sectionTitle}>About</Text>
            <SettingsItem text="🔒  Privacy Notice" onPress={() => setOverlay('privacy')} />
            <SettingsItem text="ℹ️  About Curo" onPress={() => Alert.alert('Curo', 'Version 1.0 (demo)')} />
            <TouchableOpacity style={styles.logoutButton} onPress={logout}><Text style={styles.logoutText}>Log Out</Text></TouchableOpacity>
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
            <TouchableOpacity onPress={() => setTab('settings')}><Text style={styles.backLink}>‹ Back to Settings</Text></TouchableOpacity>
            <TextInput style={[styles.input, { marginTop: 12 }]} placeholderTextColor={colors.placeholder} placeholder="🔍 Search by drug, doctor, clinic" value={search} onChangeText={setSearch} />
            <TouchableOpacity style={[styles.filterToggle, antibioticOnly && styles.filterToggleOn]} onPress={() => setAntibioticOnly((v) => !v)}>
              <Text style={[styles.filterText, antibioticOnly && styles.filterTextOn]}>{antibioticOnly ? '✓ Antibiotics only' : '💊 Antibiotics only'}</Text>
            </TouchableOpacity>
            {historyVisits.length === 0 ? (
              <View style={styles.empty}>
                <Text style={styles.emptyIcon}>🧾</Text>
                <Text style={styles.emptyTitle}>No history found</Text>
                <Text style={styles.emptySub}>Try a different search.</Text>
              </View>
            ) : historyVisits.map(([key, visit]) => (
              <VisitCard key={key} visit={visit} open={visitExpanded[key]} onToggle={() => toggleVisit(key)} badgeStyleFor={badgeStyleFor} />
            ))}
          </>
        )}

        {/* ACCESS LOG (reached from Settings) */}
        {tab === 'accesslog' && (
          <>
            <TouchableOpacity onPress={() => setTab('settings')}><Text style={styles.backLink}>‹ Back to Settings</Text></TouchableOpacity>
            <Text style={styles.sectionTitle}>Who viewed your record</Text>
            {MOCK_ACCESS_LOG.map((a) => (
              <View key={a.id} style={[styles.card, styles.logRow]}>
                <View style={styles.settingsIcon}><Text style={styles.settingsIconText}>{ROLE_ICON[a.role] || '👁️'}</Text></View>
                <View style={{ flex: 1 }}>
                  <View style={styles.whoRow}>
                    <Text style={[styles.drugName, { flexShrink: 1 }]}>{a.who}</Text>
                    {a.role === 'Guest' ? <View style={styles.guestBadge}><Text style={styles.guestBadgeText}>Guest</Text></View> : null}
                  </View>
                  <Text style={styles.cardValue}>{a.role} · {a.org}</Text>
                  <Text style={styles.cardValueFaint}>{a.action} · {formatVisitDate(a.at)}</Text>
                </View>
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
        <Text style={styles.visitDate}>{visit.issueDay ? 'Prescribed ' + formatDay(visit.issueDay) : formatVisitDate(visit.createdAt)}</Text>
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
                <Text style={styles.cardValue}>{p.detail ? p.detail : p.dosage + ' · ' + p.durationDays + ' days'}</Text>
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
  const split = text.indexOf('  ');
  const icon = split > 0 ? text.slice(0, split) : '';
  const label = split > 0 ? text.slice(split + 2) : text;
  return (
    <TouchableOpacity style={styles.settingsItem} onPress={onPress} activeOpacity={0.7}>
      {icon ? <View style={styles.settingsIcon}><Text style={styles.settingsIconText}>{icon}</Text></View> : null}
      <Text style={styles.settingsItemText}>{label}</Text>
      <Text style={styles.settingsChevron}>›</Text>
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
  screen: { flex: 1, backgroundColor: colors.bg },

  authBody: { flexGrow: 1, justifyContent: 'center', padding: 24 },
  brand: { fontSize: 32, fontWeight: 'bold', color: TEAL, textAlign: 'center', marginBottom: 4 },
  subtitle: { fontSize: 15, color: '#555', textAlign: 'center', marginBottom: 24 },
  input: { backgroundColor: colors.card, borderRadius: 12, paddingHorizontal: 14, paddingVertical: 13, marginBottom: 12, borderWidth: 1, borderColor: colors.border, fontSize: 16, color: colors.text },
  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 4 },
  buttonAlt: { backgroundColor: '#14a1a6', borderRadius: 10, padding: 15, alignItems: 'center', marginBottom: 12 },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },
  link: { color: colors.primary, textAlign: 'center', marginTop: 16, fontWeight: '600', fontSize: 13 },
  // ---- Settings / Access Log (design system) ----
  profileRow: { flexDirection: 'row', alignItems: 'center' },
  settingsAvatar: { width: 52, height: 52, borderRadius: 26, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', marginRight: 14 },
  settingsAvatarText: { color: colors.white, fontSize: 22, fontWeight: '800' },
  settingsIcon: { width: 38, height: 38, borderRadius: 11, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  settingsIconText: { fontSize: 18 },
  settingsChevron: { fontSize: 24, color: colors.placeholder, marginLeft: 8 },
  logoutText: { color: colors.text, fontWeight: '700', fontSize: 16 },
  backLink: { alignSelf: 'flex-start', color: colors.primary, fontWeight: '700', fontSize: 13, paddingVertical: 6, marginBottom: 2 },
  logRow: { flexDirection: 'row', alignItems: 'flex-start' },
  guestBadgeText: { fontSize: 11, fontWeight: '800', color: colors.accent },

  // ---- Today (design system) ----
  progressCard: { ...cardStyle, padding: 16, marginBottom: 14 },
  progressTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 },
  progressLabel: { ...type.sectionLabel, fontSize: 11 },
  progressTrack: { height: 8, borderRadius: 4, backgroundColor: colors.border, overflow: 'hidden' },
  progressFill: { height: 8, borderRadius: 4, backgroundColor: colors.success },
  timePill: { backgroundColor: colors.primarySoft, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 8, marginRight: 14, minWidth: 66, alignItems: 'center' },
  timePillDone: { backgroundColor: '#DCFCE7' },
  timeText: { fontSize: 16, fontWeight: '800', color: colors.primary },
  timeTextDone: { color: colors.success },
  markBtnPending: { opacity: 0.7 },

  // ---- Home (design system) ----
  gearBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, alignItems: 'center', justifyContent: 'center' },
  idCard: { borderRadius: 20, padding: 20, marginBottom: 16, shadowColor: '#0F172A', shadowOpacity: 0.18, shadowRadius: 14, shadowOffset: { width: 0, height: 6 }, elevation: 5 },
  idBrand: { color: 'rgba(255,255,255,0.7)', fontSize: 11, fontWeight: '800', letterSpacing: 2, marginBottom: 16 },
  idLabel: { color: 'rgba(255,255,255,0.65)', fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 1 },
  idCareId: { color: colors.white, fontSize: 26, fontWeight: '800', letterSpacing: 2, marginTop: 4, marginBottom: 20 },
  idRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' },
  idValue: { color: colors.white, fontSize: 16, fontWeight: '600', marginTop: 3 },
  warnCard: { ...cardStyle, borderLeftWidth: 5, borderLeftColor: colors.accent, padding: 16, marginBottom: 12 },
  warnHead: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  warnIcon: { fontSize: 18, marginRight: 8 },
  warnTitle: { fontSize: 16, fontWeight: '800', color: colors.text, flexShrink: 1 },
  warnHeadline: { fontSize: 13, fontWeight: '700', color: colors.text, marginBottom: 4 },
  warnText: { fontSize: 13, color: colors.textMuted, lineHeight: 19 },
  sevPill: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 4, borderRadius: 10, marginTop: 10 },
  sevText: { fontSize: 11, fontWeight: '800', letterSpacing: 0.5 },
  emptySub: { ...type.secondary, marginTop: 4, textAlign: 'center' },

  // ---- Login (design system) ----
  authScreen: { flex: 1, backgroundColor: colors.bg },
  authLogo: { alignSelf: 'center', width: 64, height: 64, borderRadius: 20, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', marginBottom: 12, ...shadow },
  authLogoText: { color: colors.white, fontSize: 32, fontWeight: '800' },
  authBrand: { ...type.screenTitle, fontSize: 28, color: colors.primary, textAlign: 'center' },
  authSubtitle: { ...type.secondary, fontSize: 16, textAlign: 'center', marginTop: 4, marginBottom: 24 },
  authCard: { ...cardStyle, padding: 20 },
  authLabel: { ...type.sectionLabel, fontSize: 11, marginBottom: 6 },
  authInput: { backgroundColor: colors.bg, borderRadius: 12, borderWidth: 1, borderColor: colors.border, paddingHorizontal: 14, paddingVertical: 13, marginBottom: 16, fontSize: 16, color: colors.text },
  authButton: { backgroundColor: colors.primary, borderRadius: 12, paddingVertical: 15, alignItems: 'center', marginTop: 4, ...shadow },
  authButtonBusy: { opacity: 0.6 },
  authButtonText: { color: colors.white, fontWeight: '700', fontSize: 16 },
  authLink: { color: colors.primary, textAlign: 'center', marginTop: 16, fontWeight: '600', fontSize: 13 },
  authNote: { ...type.caption, fontSize: 13, textAlign: 'center', marginTop: 20 },

  hint: { color: '#c00', textAlign: 'center', marginBottom: 8, fontWeight: '600' },

  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingTop: 12, paddingBottom: 12 },
  header: { ...type.screenTitle, color: colors.primary },
  gear: { fontSize: 18 },
  content: { padding: 16, paddingBottom: 24 },

  card: { ...cardStyle, padding: 16, marginBottom: 12 },
  cardDone: { backgroundColor: colors.successSoft, borderColor: '#BBF7D0' },
  cardLabel: { ...type.sectionLabel, fontSize: 11 },
  cardValue: { fontSize: 16, color: colors.text, marginTop: 4 },
  cardValueFaint: { ...type.secondary, marginTop: 2 },
  drugName: { fontSize: 16, fontWeight: '700', color: colors.text },

  empty: { alignItems: 'center', paddingVertical: 36, paddingHorizontal: 24 },
  emptyIcon: { fontSize: 40, marginBottom: 12, opacity: 0.6 },
  emptyTitle: { fontSize: 16, fontWeight: '700', color: colors.textMuted },

  bigButton: { backgroundColor: colors.primary, borderRadius: 14, paddingVertical: 15, alignItems: 'center', marginBottom: 10, ...shadow },
  bigButtonText: { color: colors.white, fontWeight: '700', fontSize: 16 },

  progress: { fontSize: 13, fontWeight: '700', color: colors.text },
  doseRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 14 },
  doneText: { textDecorationLine: 'line-through', color: colors.textMuted },
  markBtn: { backgroundColor: colors.primary, borderRadius: 12, paddingVertical: 14, alignItems: 'center', alignSelf: 'stretch', ...shadow },
  markBtnDone: { backgroundColor: '#DCFCE7', shadowOpacity: 0, elevation: 0 },
  markBtnText: { color: colors.white, fontWeight: '700', fontSize: 16 },
  markBtnTextDone: { color: colors.success },

  sectionTitle: { ...type.sectionLabel, marginTop: 16, marginBottom: 10 },

  filterToggle: { backgroundColor: colors.card, borderRadius: 12, padding: 12, alignItems: 'center', marginBottom: 14, borderWidth: 1, borderColor: colors.border },
  filterToggleOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  filterText: { color: colors.primary, fontWeight: '700', fontSize: 13 },
  filterTextOn: { color: colors.white },

  visitCard: { ...cardStyle, padding: 16, marginBottom: 12 },
  visitDoctor: { fontSize: 16, fontWeight: '700', color: colors.primary },
  visitHospital: { ...type.secondary, marginTop: 2 },
  visitDate: { ...type.caption, fontSize: 12, marginTop: 4 },
  visitDrugs: { marginTop: 12, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 10 },
  drugRow: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#F1F5F9' },
  rxHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  visitDrugName: { fontSize: 16, fontWeight: '600', color: colors.text, flexShrink: 1, marginRight: 8 },
  flag: { color: colors.accent, fontSize: 13, fontWeight: '700', marginTop: 8 },

  courseTag: { alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, marginTop: 6 },
  courseTagText: { fontSize: 11, fontWeight: '700' },
  tagIssued: { backgroundColor: '#F1F5F9' },
  tagActive: { backgroundColor: colors.successSoft },
  tagFinished: { backgroundColor: '#F1F5F9' },

  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 12 },
  badgeText: { fontSize: 11, fontWeight: '700', color: '#fff' },
  whoRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  guestBadge: { backgroundColor: colors.accentSoft, borderWidth: 1, borderColor: colors.accent, paddingHorizontal: 10, paddingVertical: 3, borderRadius: 10, marginLeft: 8 },
  badgeAccess: { backgroundColor: colors.success },
  badgeWatch: { backgroundColor: colors.accent },
  badgeReserve: { backgroundColor: colors.danger },
  badgeNotRecommended: { backgroundColor: '#7F1D1D' },

  settingsItem: { ...cardStyle, flexDirection: 'row', alignItems: 'center', padding: 14, marginBottom: 10 },
  settingsItemText: { flex: 1, fontSize: 16, color: colors.text, fontWeight: '600' },
  logoutButton: { backgroundColor: colors.card, borderRadius: 12, paddingVertical: 15, alignItems: 'center', marginTop: 24, borderWidth: 1, borderColor: colors.border, ...shadow },
  deleteButton: { backgroundColor: colors.dangerSoft, borderRadius: 12, paddingVertical: 15, alignItems: 'center', marginTop: 12, borderWidth: 1, borderColor: '#FECACA' },
  deleteText: { color: colors.danger, fontWeight: '700', fontSize: 16 },

  toast: { position: 'absolute', bottom: 96, alignSelf: 'center', backgroundColor: colors.text, paddingVertical: 12, paddingHorizontal: 20, borderRadius: 25, ...shadow, elevation: 5 },
  toastText: { color: '#fff', fontSize: 14, fontWeight: '600' },
});