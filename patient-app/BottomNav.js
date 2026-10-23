import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { colors } from './theme';


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
          <TouchableOpacity key={t.key} style={[styles.item, active && styles.itemActive]} onPress={() => onTabPress(t.key)} activeOpacity={0.8}>
            <Text style={styles.icon}>{t.icon}</Text>
            <Text style={[styles.label, active && styles.labelActive]}>{t.label}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border, paddingVertical: 8, paddingHorizontal: 8, shadowColor: '#0F172A', shadowOpacity: 0.06, shadowRadius: 12, shadowOffset: { width: 0, height: -4 }, elevation: 8 },
  item: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 7, marginHorizontal: 3, borderRadius: 14 },
  icon: { fontSize: 20 },
  label: { fontSize: 11, color: colors.textMuted, fontWeight: '600', marginTop: 2 },
  labelActive: { color: colors.white },
  itemActive: { backgroundColor: colors.primary },
});