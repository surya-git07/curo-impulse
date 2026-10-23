// config.js
// Single place for the API URL and the mock switch.
// Set EXPO_PUBLIC_API_URL in patient-app/.env (gitignored), e.g.
//   EXPO_PUBLIC_API_URL=http://13.203.10.143:8080
// then restart with `npx expo start -c` so Expo picks it up.

export const API_URL = process.env.EXPO_PUBLIC_API_URL || '';

// Automatic: with no API URL the app is fully mock and never calls the network.
// With a URL, Login, Home and Today go live, and any call that fails falls back
// to the mock data for that screen (see App.js). History, Family, Settings,
// consent and alerts are still always mock. Venue Wi-Fi can be flaky, so that
// fallback is what keeps the live demo from crashing.
export const MOCK_MODE = !API_URL;
