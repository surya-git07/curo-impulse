import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ActivityIndicator } from 'react-native';
import { createShareCode, ApiError } from './api';

const TEAL = '#0d7377';

// Try to load the QR library. If it isn't installed, we fall back to showing
// the CareID in a box so the app never crashes (useful on flaky venue Wi-Fi).
// To get a real QR code, run:  npx expo install react-native-svg react-native-qrcode-svg
let QRCode = null;
try {
  // eslint-disable-next-line global-require
  QRCode = require('react-native-qrcode-svg').default;
} catch (e) {
  QRCode = null;
}

export default function QrTab({ careId, onRefresh, refreshing }) {
  const [shareCode, setShareCode] = useState(null);
  const [shareExpiry, setShareExpiry] = useState(null);
  const [shareLoading, setShareLoading] = useState(false);
  const [shareError, setShareError] = useState(null);

  async function handleGenerateShareCode() {
    setShareLoading(true);
    setShareError(null);
    try {
      const result = await createShareCode(['PROFILE', 'PRESCRIPTIONS'], 24);
      setShareCode(result.shareCode); // string, keep as-is — never Number(result.shareCode)
      setShareExpiry(result.expiresAtDisplay);
    } catch (e) {
      setShareError('Could not generate code — try again');
    } finally {
      setShareLoading(false);
    }
  }

  return (
    <View style={styles.wrap}>
      <Text style={styles.caption}>Show this to your doctor or pharmacy</Text>

      <View style={styles.qrBox}>
        {QRCode ? (
          <QRCode value={careId || 'CID-UNKNOWN'} size={200} color="#1a3c3d" backgroundColor="#fff" />
        ) : (
          <View style={styles.fallback}>
            <Text style={styles.fallbackLabel}>CareID</Text>
            <Text style={styles.fallbackValue}>{careId}</Text>
            <Text style={styles.fallbackNote}>
              (Install react-native-qrcode-svg to show a scannable code)
            </Text>
          </View>
        )}
      </View>

      <Text style={styles.careId}>{careId}</Text>

      <TouchableOpacity style={styles.button} onPress={onRefresh}>
        <Text style={styles.buttonText}>{refreshing ? 'Refreshing…' : '🔄 Refresh'}</Text>
      </TouchableOpacity>

      <View style={styles.divider} />

      <Text style={styles.caption}>Doctor access code</Text>

      {shareCode ? (
        <View style={styles.qrBox}>
          {QRCode ? (
            <QRCode value={shareCode} size={160} color="#1a3c3d" backgroundColor="#fff" />
          ) : (
            <View style={[styles.fallback, { width: 160, height: 160 }]}>
              <Text style={styles.fallbackLabel}>Share Code</Text>
              <Text style={styles.fallbackValue}>{shareCode}</Text>
            </View>
          )}
        </View>
      ) : null}

      {shareCode ? (
        <>
          <Text style={styles.shareCode}>{shareCode}</Text>
          <Text style={styles.expiryNote}>Expires {shareExpiry}</Text>
        </>
      ) : null}

      {shareError ? <Text style={styles.errorNote}>{shareError}</Text> : null}

      <TouchableOpacity style={styles.buttonSecondary} onPress={handleGenerateShareCode} disabled={shareLoading}>
        {shareLoading ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.buttonText}>{shareCode ? '🔄 New code' : 'Generate code'}</Text>
        )}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center', paddingVertical: 20 },
  caption: { fontSize: 14, color: '#5a6a6a', marginBottom: 20 },
  qrBox: { backgroundColor: '#fff', padding: 24, borderRadius: 16, elevation: 3, marginBottom: 16 },
  fallback: { width: 200, height: 200, alignItems: 'center', justifyContent: 'center' },
  fallbackLabel: { fontSize: 12, color: '#8a9a9a', textTransform: 'uppercase', letterSpacing: 1 },
  fallbackValue: { fontSize: 20, fontWeight: 'bold', color: TEAL, marginVertical: 8, textAlign: 'center' },
  fallbackNote: { fontSize: 11, color: '#99a', textAlign: 'center', paddingHorizontal: 12 },
  careId: { fontSize: 18, fontWeight: '700', color: TEAL, letterSpacing: 1, marginBottom: 20 },
  button: { backgroundColor: TEAL, borderRadius: 10, paddingHorizontal: 30, paddingVertical: 12 },
  buttonSecondary: { backgroundColor: TEAL, borderRadius: 10, paddingHorizontal: 30, paddingVertical: 12, marginTop: 8, minWidth: 160, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: 'bold', fontSize: 15 },
  divider: { width: '80%', height: 1, backgroundColor: '#e0e6e6', marginVertical: 24 },
  shareCode: { fontSize: 24, fontWeight: '700', color: TEAL, letterSpacing: 4, marginBottom: 4 },
  expiryNote: { fontSize: 12, color: '#8a9a9a', marginBottom: 16 },
  errorNote: { fontSize: 12, color: '#c0392b', marginBottom: 12 },
});