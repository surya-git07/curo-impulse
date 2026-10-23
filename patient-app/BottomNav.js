import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

const TEAL = '#0d7377';

// Bottom tab bar. Tabs: Home, Today, QR, Book, Family.
// Settings is reached from the gear in the header, not here.
const TABS = [
  { key: 'home', label: 'Home', icon: '🏠' },
  { key: 'today', label: 'Today', icon: '📅' },
  { key: 'qr', label: 'QR', icon: '🔳' },
  { key: 'book', label: 'Book', icon: '🗓️' },
  { key: 'family', label: 'Family', icon: '👪' },
];

export default function BottomNav({ activeTab, onTabPress }) {
  return (
    <View style={styles.bar}>
      {TABS.map((t) => {
        const active = activeTab === t.key;
        return (
          <TouchableOpacity key={t.key} style={styles.item} onPress={() => onTabPress(t.key)}>
            <Text style={styles.icon}>{t.icon}</Text>
            <Text style={[styles.label, active && styles.labelActive]}>{t.label}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#e0eaea', paddingVertical: 6 },
  item: { flex: 1, alignItems: 'center', paddingVertical: 4 },
  icon: { fontSize: 18 },
  label: { fontSize: 11, color: '#8a9a9a', fontWeight: '600', marginTop: 2 },
  labelActive: { color: TEAL },
});