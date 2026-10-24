// theme.js
// Design tokens for the Curo patient app. Visual only: no logic lives here.
// Orange (accent) is for warnings, alerts and CTAs; never a screen or card background.

export const colors = {
  primary: '#0F766E',      // deep teal: headers, primary actions
  primaryDark: '#134E4A',  // gradient end, pressed states
  primarySoft: '#CCFBF1',  // tinted chips / selected backgrounds
  accent: '#F97316',       // warm orange: alerts, interaction warnings, CTAs
  accentSoft: '#FFF7ED',   // faint tint inside warning cards
  success: '#16A34A',
  successSoft: '#F0FDF4',
  danger: '#DC2626',
  dangerSoft: '#FEF2F2',
  bg: '#F8FAFC',           // screens (not white)
  card: '#FFFFFF',
  border: '#E2E8F0',
  text: '#0F172A',
  textMuted: '#64748B',
  placeholder: '#94A3B8',
  white: '#FFFFFF',
};

// Type scale: 24 / 20 titles, 16 body, 13 secondary + section labels, 11 captions.
export const type = {
  screenTitle: { fontSize: 24, fontWeight: '800', color: colors.text },
  heading: { fontSize: 20, fontWeight: '700', color: colors.text },
  body: { fontSize: 16, color: colors.text },
  secondary: { fontSize: 13, color: colors.textMuted },
  sectionLabel: { fontSize: 13, fontWeight: '700', color: colors.textMuted, textTransform: 'uppercase', letterSpacing: 1 },
  caption: { fontSize: 11, color: colors.textMuted },
};

export const shadow = {
  shadowColor: '#0F172A',
  shadowOpacity: 0.06,
  shadowRadius: 12,
  shadowOffset: { width: 0, height: 4 },
  elevation: 3,
};

// Standard card: radius 16, 1px border, soft shadow.
export const card = {
  backgroundColor: colors.card,
  borderRadius: 16,
  borderWidth: 1,
  borderColor: colors.border,
  ...shadow,
};
