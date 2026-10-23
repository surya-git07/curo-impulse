import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import * as Clipboard from 'expo-clipboard';

import {
  SCOPES, DURATIONS, DEMO_CODES,
  createShare, revokeShare, listActiveShares, redeemShare,
} from './mocks/shares';

const TEAL = '#0d7377';

const SCOPE_LABELS = { MEDICINES: 'Medicines', ALLERGIES: 'Allergies', HISTORY: 'History' };

// Same guarded load as QrTab so a missing QR library never crashes the screen.
let QRCode = null;
try {
  // eslint-disable-next-line global-require
  QRCode = require('react-native-qrcode-svg').default;
} catch (e) {
  QRCode = null;
}

function formatWhen(iso) {
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
  });
}

const scopeText = (scopes) => scopes.map((s) => SCOPE_LABELS[s]).join(', ');

// Message shown in the preview box for each failed redeem.
function errorMessage(r) {
  switch (r.error) {
    case 'INVALID_CODE': return 'No share matches this code. Check it and try again.';
    case 'SHARE_EXPIRED': return `This share expired on ${formatWhen(r.expires_at)}. Ask the patient for a new code.`;
    case 'SHARE_REVOKED': return 'The patient has stopped sharing this record.';
    case 'TOO_MANY_ATTEMPTS': return `Too many wrong codes. Try again in ${r.retry_after} seconds.`;
    default: return 'Something went wrong.';
  }
}

export default function ShareRecord({ onBack, onCopied }) {
  const [picked, setPicked] = useState({ MEDICINES: true, ALLERGIES: true, HISTORY: false });
  const [hours, setHours] = useState(24);
  const [created, setCreated] = useState(null);
  const [active, setActive] = useState(listActiveShares());

  const [guestCode, setGuestCode] = useState('');
  const [outcome, setOutcome] = useState(null);

  const toggle = (s) => setPicked((p) => ({ ...p, [s]: !p[s] }));

  const create = () => {
    const scopes = SCOPES.filter((s) => picked[s]);
    if (scopes.length === 0) return Alert.alert('Pick what to share', 'Select at least one of Medicines, Allergies or History.');
    setCreated(createShare({ scopes, durationHours: hours }));
    setActive(listActiveShares());
  };

  const revoke = (id) => {
    revokeShare(id);
    setActive(listActiveShares());
    if (created && created.id === id) setCreated(null);
  };

  const copyLink = async () => {
    await Clipboard.setStringAsync(created.link);
    if (onCopied) onCopied('Link copied');
  };

  const preview = () => setOutcome(redeemShare(guestCode));

  return (
    <View>
      <TouchableOpacity onPress={onBack}><Text style={styles.link}>‹ Back to Settings</Text></TouchableOpacity>

      {/* CREATE */}
      <Text style={styles.sectionTitle}>Create a share</Text>
      <View style={styles.card}>
        <Text style={styles.cardLabel}>What to share</Text>
        {SCOPES.map((s) => (
          <TouchableOpacity key={s} style={styles.checkRow} onPress={() => toggle(s)} activeOpacity={0.7}>
            <View style={[styles.checkbox, picked[s] && styles.checkboxOn]}>
              {picked[s] && <Text style={styles.tick}>✓</Text>}
            </View>
            <Text style={styles.checkText}>{SCOPE_LABELS[s]}</Text>
          </TouchableOpacity>
        ))}

        <View style={styles.divider} />
        <Text style={styles.cardLabel}>Valid for</Text>
        <View style={styles.chipRow}>
          {DURATIONS.map((d) => (
            <TouchableOpacity key={d.hours} style={[styles.chip, hours === d.hours && styles.chipOn]} onPress={() => setHours(d.hours)}>
              <Text style={[styles.chipText, hours === d.hours && styles.chipTextOn]}>{d.label}</Text>
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity style={styles.button} onPress={create}>
          <Text style={styles.buttonText}>Create share</Text>
        </TouchableOpacity>
      </View>

      {/* RESULT */}
      {created ? (
        <View style={[styles.card, styles.resultCard]}>
          <Text style={styles.cardLabel}>Share ready</Text>
          <View style={styles.qrBox}>
            {QRCode ? (
              <QRCode value={created.link} size={160} color="#1a3c3d" backgroundColor="#fff" />
            ) : (
              <Text style={styles.qrFallback}>QR unavailable{'\n'}use the code below</Text>
            )}
          </View>
          <Text style={styles.code}>{created.code}</Text>
          <TouchableOpacity onPress={copyLink} activeOpacity={0.6}>
            <Text style={styles.linkText}>{created.link}  📋</Text>
          </TouchableOpacity>
          <Text style={styles.cardValueFaint}>Shares: {scopeText(created.scopes)}</Text>
          <Text style={styles.cardValueFaint}>Expires {formatWhen(created.expires_at)}</Text>
        </View>
      ) : null}

      {/* ACTIVE */}
      <Text style={styles.sectionTitle}>Active shares</Text>
      {active.length === 0 ? (
        <View style={styles.card}><Text style={styles.cardValueFaint}>No active shares.</Text></View>
      ) : active.map((s) => (
        <View key={s.id} style={[styles.card, styles.shareRow]}>
          <View style={{ flex: 1 }}>
            <Text style={styles.drugName}>{s.code}</Text>
            <Text style={styles.cardValue}>{scopeText(s.scopes)}</Text>
            <Text style={styles.cardValueFaint}>Expires {formatWhen(s.expires_at)}</Text>
          </View>
          <TouchableOpacity style={styles.revokeBtn} onPress={() => revoke(s.id)}>
            <Text style={styles.revokeText}>Revoke</Text>
          </TouchableOpacity>
        </View>
      ))}

      {/* PREVIEW AS GUEST */}
      <Text style={styles.sectionTitle}>Preview as guest</Text>
      <View style={styles.card}>
        <Text style={styles.cardValueFaint}>Enter a code to see what a guest would see.</Text>
        <TextInput
          style={styles.input}
          placeholder="6-character code"
          value={guestCode}
          onChangeText={setGuestCode}
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={6}
        />
        <TouchableOpacity style={styles.button} onPress={preview}>
          <Text style={styles.buttonText}>Preview</Text>
        </TouchableOpacity>
        <Text style={styles.hint}>Demo codes: {DEMO_CODES.expired} (expired), {DEMO_CODES.revoked} (revoked)</Text>

        {outcome && !outcome.ok ? (
          <View style={styles.errorBox}>
            <Text style={styles.errorCode}>{outcome.error}</Text>
            <Text style={styles.errorText}>{errorMessage(outcome)}</Text>
          </View>
        ) : null}

        {outcome && outcome.ok ? (
          <View style={styles.okBox}>
            <Text style={styles.okTitle}>Shared by {outcome.shared_by} · valid until {formatWhen(outcome.share.expires_at)}</Text>
            {outcome.data.medicines ? (
              <>
                <Text style={styles.okHead}>Medicines</Text>
                {outcome.data.medicines.map((m, i) => (
                  <Text key={i} style={styles.okLine}>{m.drug_name} · {m.dosage} · {m.duration_days} days</Text>
                ))}
              </>
            ) : null}
            {outcome.data.allergies !== undefined ? (
              <>
                <Text style={styles.okHead}>Allergies</Text>
                <Text style={styles.okLine}>{outcome.data.allergies || 'None recorded'}</Text>
              </>
            ) : null}
            {outcome.data.history ? (
              <>
                <Text style={styles.okHead}>History</Text>
                {outcome.data.history.map((h, i) => (
                  <Text key={i} style={styles.okLine}>{h.drug_name} · {h.doctor_name} · {formatWhen(h.created_at)}</Text>
                ))}
              </>
            ) : null}
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  link: { color: TEAL, textAlign: 'center', marginTop: 16, fontWeight: '600' },
  sectionTitle: { fontSize: 13, fontWeight: '700', color: '#5a6a6a', marginVertical: 12, textTransform: 'uppercase', letterSpacing: 0.5 },

  card: { backgroundColor: '#fff', borderRadius: 14, padding: 16, marginBottom: 12, elevation: 2 },
  resultCard: { alignItems: 'center', borderWidth: 2, borderColor: TEAL },
  cardLabel: { fontSize: 11, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 0.5, alignSelf: 'flex-start' },
  cardValue: { fontSize: 15, color: '#445', marginTop: 4 },
  cardValueFaint: { fontSize: 13, color: '#8a9a9a', marginTop: 2 },
  divider: { height: 1, backgroundColor: '#eef2f2', marginVertical: 12 },
  drugName: { fontSize: 16, fontWeight: '700', color: '#1a3c3d', letterSpacing: 1 },

  checkRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  checkbox: { width: 22, height: 22, borderRadius: 5, borderWidth: 2, borderColor: TEAL, marginRight: 10, alignItems: 'center', justifyContent: 'center' },
  checkboxOn: { backgroundColor: TEAL },
  tick: { color: '#fff', fontSize: 14, fontWeight: 'bold' },
  checkText: { fontSize: 15, color: '#333' },

  chipRow: { flexDirection: 'row', marginTop: 8, marginBottom: 12 },
  chip: { flex: 1, backgroundColor: '#fff', borderRadius: 10, padding: 10, alignItems: 'center', marginRight: 8, borderWidth: 1, borderColor: '#cde' },
  chipOn: { backgroundColor: TEAL, borderColor: TEAL },
  chipText: { color: TEAL, fontWeight: '700', fontSize: 13 },
  chipTextOn: { color: '#fff' },

  button: { backgroundColor: TEAL, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 4 },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 16 },

  qrBox: { backgroundColor: '#fff', padding: 16, borderRadius: 12, marginVertical: 12 },
  qrFallback: { width: 160, height: 160, textAlign: 'center', textAlignVertical: 'center', color: '#99a', fontSize: 12 },
  code: { fontSize: 30, fontWeight: 'bold', color: TEAL, letterSpacing: 4, marginBottom: 6 },
  linkText: { fontSize: 13, color: TEAL, fontWeight: '600', marginBottom: 8 },

  shareRow: { flexDirection: 'row', alignItems: 'center' },
  revokeBtn: { backgroundColor: '#fff3f3', borderRadius: 8, paddingHorizontal: 14, paddingVertical: 10, borderWidth: 1, borderColor: '#f0c0c0' },
  revokeText: { color: '#c0392b', fontWeight: '700', fontSize: 13 },

  input: { backgroundColor: '#fff', borderRadius: 10, padding: 14, marginVertical: 12, borderWidth: 1, borderColor: '#cde', letterSpacing: 3, fontSize: 16 },
  hint: { color: '#8a9a9a', fontSize: 12, marginTop: 10 },

  errorBox: { backgroundColor: '#fff3f3', borderRadius: 10, padding: 12, marginTop: 12, borderWidth: 1, borderColor: '#f0c0c0' },
  errorCode: { color: '#c0392b', fontSize: 11, fontWeight: '800', letterSpacing: 0.5, marginBottom: 4 },
  errorText: { color: '#7d1128', fontSize: 14, fontWeight: '600', lineHeight: 20 },

  okBox: { backgroundColor: '#eef7f0', borderRadius: 10, padding: 12, marginTop: 12, borderWidth: 1, borderColor: '#cfe8d6' },
  okTitle: { color: '#2e7d4f', fontSize: 14, fontWeight: '700', marginBottom: 6 },
  okHead: { color: '#5a6a6a', fontSize: 11, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5, marginTop: 10 },
  okLine: { color: '#334', fontSize: 14, marginTop: 3 },
});
